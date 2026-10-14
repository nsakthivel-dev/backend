package com.evplatform.service;

import com.evplatform.domain.*;
import com.evplatform.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class SmartChargingRecommendationService {

    private final TransformerRiskConfigRepository riskConfigRepository;
    private final TransformerRepository transformerRepository;
    private final StationRepository stationRepository;
    private final VehicleRepository vehicleRepository;
    private final ConnectorRepository connectorRepository;
    private final BookingRepository bookingRepository;
    private final ChargingSessionRepository sessionRepository;
    private final DynamicPricingService dynamicPricingService;
    private final AlgorithmEngineService algorithmEngineService;

    public SmartChargingRecommendationService(
            TransformerRiskConfigRepository riskConfigRepository,
            TransformerRepository transformerRepository,
            StationRepository stationRepository,
            VehicleRepository vehicleRepository,
            ConnectorRepository connectorRepository,
            BookingRepository bookingRepository,
            ChargingSessionRepository sessionRepository,
            DynamicPricingService dynamicPricingService,
            AlgorithmEngineService algorithmEngineService) {
        this.riskConfigRepository = riskConfigRepository;
        this.transformerRepository = transformerRepository;
        this.stationRepository = stationRepository;
        this.vehicleRepository = vehicleRepository;
        this.connectorRepository = connectorRepository;
        this.bookingRepository = bookingRepository;
        this.sessionRepository = sessionRepository;
        this.dynamicPricingService = dynamicPricingService;
        this.algorithmEngineService = algorithmEngineService;
    }

    /**
     * Retrieves the currently active user-configured risk configuration for a transformer.
     * If none exists in DB, creates and persists a baseline configuration.
     */
    @Transactional
    public TransformerRiskConfig getActiveRiskConfig(Transformer transformer) {
        return riskConfigRepository.findActiveByTransformerId(transformer.getId())
                .orElseGet(() -> {
                    TransformerRiskConfig config = new TransformerRiskConfig(
                            transformer,
                            1,
                            transformer.getApprovedLimitKw(),
                            90.0, // 90% warning threshold
                            98.0, // 98% critical threshold
                            transformer.getSafetyMarginPercent(),
                            transformer.getNonEvBaselineKw(),
                            "RESTRICT_NEW_ONLINE_BOOKINGS,OFFER_ALTERNATIVES",
                            "EMERGENCY_THROTTLING,PROHIBIT_NEW_DEMAND",
                            "UTILITY_SYSTEM",
                            "Initial baseline risk configuration"
                    );
                    return riskConfigRepository.save(config);
                });
    }

    /**
     * Updates and audits the transformer risk configuration.
     */
    @Transactional
    public TransformerRiskConfig updateRiskConfig(
            Long transformerId,
            Double approvedLimitKw,
            Double warningThresholdPercent,
            Double criticalThresholdPercent,
            Double safetyMarginPercent,
            Double nonEvBaselineKw,
            String highRiskPolicy,
            String criticalRiskPolicy,
            String updatedBy,
            String changeReason) {

        Transformer transformer = transformerRepository.findById(transformerId)
                .orElseThrow(() -> new IllegalArgumentException("Transformer not found: " + transformerId));

        // Validate values against physical and electrical sanity
        if (approvedLimitKw != null && approvedLimitKw <= 0) {
            throw new IllegalArgumentException("Approved operating limit must be greater than zero.");
        }
        if (warningThresholdPercent != null && (warningThresholdPercent <= 50.0 || warningThresholdPercent > 100.0)) {
            throw new IllegalArgumentException("Warning threshold must be between 50% and 100% of approved limit.");
        }
        if (criticalThresholdPercent != null && (criticalThresholdPercent <= 60.0 || criticalThresholdPercent > 110.0)) {
            throw new IllegalArgumentException("Critical threshold must be between 60% and 110% of approved limit.");
        }
        if (warningThresholdPercent != null && criticalThresholdPercent != null && warningThresholdPercent >= criticalThresholdPercent) {
            throw new IllegalArgumentException("Warning threshold must be strictly lower than critical threshold.");
        }

        // Deactivate previous configuration
        Optional<TransformerRiskConfig> currentOpt = riskConfigRepository.findActiveByTransformerId(transformerId);
        int nextVersion = 1;
        if (currentOpt.isPresent()) {
            TransformerRiskConfig current = currentOpt.get();
            current.setIsActive(false);
            riskConfigRepository.save(current);
            nextVersion = current.getConfigVersion() + 1;
        }

        double finalApprovedLimit = (approvedLimitKw != null) ? approvedLimitKw : transformer.getApprovedLimitKw();
        double finalWarning = (warningThresholdPercent != null) ? warningThresholdPercent : 90.0;
        double finalCritical = (criticalThresholdPercent != null) ? criticalThresholdPercent : 98.0;
        double finalMargin = (safetyMarginPercent != null) ? safetyMarginPercent : transformer.getSafetyMarginPercent();
        double finalBaseline = (nonEvBaselineKw != null) ? nonEvBaselineKw : transformer.getNonEvBaselineKw();

        TransformerRiskConfig newConfig = new TransformerRiskConfig(
                transformer,
                nextVersion,
                finalApprovedLimit,
                finalWarning,
                finalCritical,
                finalMargin,
                finalBaseline,
                highRiskPolicy,
                criticalRiskPolicy,
                updatedBy != null ? updatedBy : "UTILITY_ADMIN",
                changeReason != null ? changeReason : "User updated threshold configuration"
        );

        TransformerRiskConfig saved = riskConfigRepository.save(newConfig);

        // Update transformer entity for fast query compatibility
        transformer.setApprovedLimitKw(finalApprovedLimit);
        transformer.setSafetyMarginPercent(finalMargin);
        transformer.setNonEvBaselineKw(finalBaseline);
        transformer.setLastUpdatedAt(LocalDateTime.now());
        transformer.setUpdatedBy(updatedBy);
        transformerRepository.save(transformer);

        return saved;
    }

    /**
     * Section 3: Smart Charging Demand Estimation & Feasibility Evaluation.
     */
    @Transactional(readOnly = true)
    public RecommendationResult evaluateAndRecommend(RecommendationRequest req) {
        // 1. Validation
        if (req.currentSoC() < 0 || req.targetSoC() > 100 || req.targetSoC() <= req.currentSoC()) {
            throw new IllegalArgumentException(String.format(
                    "Invalid state of charge: Target SoC (%d%%) must be strictly greater than current SoC (%d%%) and within [0, 100].",
                    req.targetSoC(), req.currentSoC()));
        }

        Vehicle vehicle = vehicleRepository.findById(req.vehicleId())
                .orElseThrow(() -> new IllegalArgumentException("Vehicle not found: " + req.vehicleId()));

        Station station = stationRepository.findById(req.stationId())
                .orElseThrow(() -> new IllegalArgumentException("Station not found: " + req.stationId()));

        Transformer transformer = station.getShop().getTransformer();
        TransformerRiskConfig riskConfig = getActiveRiskConfig(transformer);

        // Find compatible online connector
        List<Connector> stationConnectors = connectorRepository.findByStationId(station.getId());
        Connector connector = stationConnectors.stream()
                .filter(c -> !c.getIsWalkInReserve())
                .filter(c -> c.getConnectorType().equalsIgnoreCase(vehicle.getConnectorType()))
                .filter(c -> !"FAULTED".equals(c.getStatus()))
                .findFirst()
                .orElse(null);

        if (connector == null) {
            // Check if there are any connectors with matching type at all
            boolean hasAnyType = stationConnectors.stream()
                    .anyMatch(c -> c.getConnectorType().equalsIgnoreCase(vehicle.getConnectorType()));
            if (!hasAnyType) {
                return new RecommendationResult(
                        0, 0, 0.90, 0, 0, 0, 10, 10, false, "INCOMPATIBLE_CONNECTOR",
                        riskConfig.getWarningThresholdKw(), riskConfig.getApprovedLimitKw(),
                        riskConfig.getConfigVersion(),
                        String.format("Station does not possess %s connectors matching vehicle.", vehicle.getConnectorType()),
                        Collections.emptyList(), Collections.emptyList(),
                        Map.of("error", "No compatible connectors"),
                        LocalDateTime.now().toString()
                );
            }
            connector = stationConnectors.get(0);
        }

        // 2. Section 3: Smart Charging Demand Estimation (Energy, Profile, Duration)
        boolean fallbackAssumptionsApplied = false;
        double batteryCapacityKwh;
        if (vehicle.getBatteryCapacityKwh() != null && vehicle.getBatteryCapacityKwh() > 0) {
            batteryCapacityKwh = vehicle.getBatteryCapacityKwh();
        } else {
            batteryCapacityKwh = 60.0; // fallback standard 60 kWh battery
            fallbackAssumptionsApplied = true;
        }

        double vehicleMaxRateKw;
        if (vehicle.getMaxChargingRateKw() != null && vehicle.getMaxChargingRateKw() > 0) {
            vehicleMaxRateKw = vehicle.getMaxChargingRateKw();
        } else {
            vehicleMaxRateKw = 50.0; // fallback 50 kW charging capability
            fallbackAssumptionsApplied = true;
        }

        double efficiency = (req.chargingEfficiency() != null && req.chargingEfficiency() > 0.5)
                ? req.chargingEfficiency() : 0.90; // default 90% charging efficiency

        int socDelta = req.targetSoC() - req.currentSoC();
        double batteryEnergyRequiredKwh = batteryCapacityKwh * (socDelta / 100.0);
        double gridEnergyRequiredKwh = Math.round((batteryEnergyRequiredKwh / efficiency) * 100.0) / 100.0;

        // Charging Power Profile (accounting for vehicle max rate, charger max power, and SoC tapering above 80%)
        double maxAvailablePowerKw = Math.min(connector.getMaxPowerKw(), vehicleMaxRateKw);
        int chargingDurationMinutes;
        double effectiveGridPowerKw;

        if (req.currentSoC() < 80 && req.targetSoC() > 80) {
            // Nonlinear charging curve: full power up to 80%, 50% power from 80% to 100%
            double energyPhase1Kwh = batteryCapacityKwh * ((80 - req.currentSoC()) / 100.0);
            double energyPhase2Kwh = batteryCapacityKwh * ((req.targetSoC() - 80) / 100.0);

            double durationPhase1Hours = (energyPhase1Kwh / efficiency) / maxAvailablePowerKw;
            double durationPhase2Hours = (energyPhase2Kwh / efficiency) / (maxAvailablePowerKw * 0.5);

            chargingDurationMinutes = (int) Math.ceil((durationPhase1Hours + durationPhase2Hours) * 60.0);
            effectiveGridPowerKw = gridEnergyRequiredKwh / ((double) chargingDurationMinutes / 60.0);
        } else if (req.currentSoC() >= 80) {
            // Tapered charging only
            double durationHours = gridEnergyRequiredKwh / (maxAvailablePowerKw * 0.5);
            chargingDurationMinutes = (int) Math.ceil(durationHours * 60.0);
            effectiveGridPowerKw = maxAvailablePowerKw * 0.5;
        } else {
            // Full constant power
            double durationHours = gridEnergyRequiredKwh / maxAvailablePowerKw;
            chargingDurationMinutes = (int) Math.ceil(durationHours * 60.0);
            effectiveGridPowerKw = maxAvailablePowerKw;
        }

        if (chargingDurationMinutes < 5) chargingDurationMinutes = 5;

        int turnaroundBuffer = station.getTurnaroundBufferMinutes();
        int totalOccupancyMinutes = chargingDurationMinutes + turnaroundBuffer;
        double apparentPowerKva = Math.round((effectiveGridPowerKw / transformer.getPowerFactor()) * 10.0) / 10.0;

        LocalDateTime requestedStart = req.preferredStartTime() != null
                ? req.preferredStartTime() : LocalDateTime.now().plusMinutes(10);
        LocalDateTime requestedEnd = requestedStart.plusMinutes(totalOccupancyMinutes);

        // 3. Section 4: Evaluate the Entire Requested Charging Session
        SessionEvaluation requestedEval = evaluateSessionIntervals(
                transformer, riskConfig, connector, requestedStart, requestedEnd, effectiveGridPowerKw);

        boolean isRequestedFeasible = requestedEval.isFeasible() && !"CRITICAL".equals(requestedEval.riskClassification());
        String rejectionReason = null;
        if (!isRequestedFeasible) {
            rejectionReason = requestedEval.rejectionReason();
        }

        // 4. Section 5: Recommend Alternative Charging Times
        List<AlternativeSlot> alternatives = new ArrayList<>();
        if (!isRequestedFeasible || "AT_RISK".equals(requestedEval.riskClassification())) {
            int maxSearchHours = (req.maxWaitingHours() != null && req.maxWaitingHours() > 0) ? req.maxWaitingHours() : 4;
            int stepMinutes = 30;
            int totalSteps = (maxSearchHours * 60) / stepMinutes;

            LocalDateTime candidateStart = requestedStart.plusMinutes(stepMinutes);
            for (int s = 1; s <= totalSteps; s++) {
                LocalDateTime candidateEnd = candidateStart.plusMinutes(totalOccupancyMinutes);

                SessionEvaluation eval = evaluateSessionIntervals(
                        transformer, riskConfig, connector, candidateStart, candidateEnd, effectiveGridPowerKw);

                if (eval.isFeasible() && "NORMAL".equals(eval.riskClassification())) {
                    // Calculate price quote for this candidate
                    double priceEstimate = station.getBaseTariffPerKwh();
                    try {
                        PriceQuote q = dynamicPricingService.generateQuote(station.getId(), candidateStart, candidateEnd);
                        priceEstimate = q.getCalculatedPricePerKwh();
                    } catch (Exception ignored) {}

                    double totalCostEstimate = Math.round(gridEnergyRequiredKwh * priceEstimate * 100.0) / 100.0;

                    alternatives.add(new AlternativeSlot(
                            candidateStart.toString(),
                            candidateEnd.toString(),
                            connector.getId(),
                            connector.getConnectorNumber(),
                            priceEstimate,
                            totalCostEstimate,
                            eval.minHeadroomBelowWarningKw(),
                            s * stepMinutes, // wait time in minutes
                            eval.riskClassification()
                    ));
                }

                candidateStart = candidateStart.plusMinutes(stepMinutes);
            }

            // Rank alternatives: highest safety margin headroom, then lowest waiting time
            alternatives.sort((a, b) -> {
                int headroomCmp = Double.compare(b.safetyHeadroomKw(), a.safetyHeadroomKw());
                if (headroomCmp != 0) return headroomCmp;
                return Integer.compare(a.waitingTimeMinutes(), b.waitingTimeMinutes());
            });
        }

        Map<String, Object> assumptions = new LinkedHashMap<>();
        assumptions.put("fallbackAssumptionsApplied", fallbackAssumptionsApplied);
        assumptions.put("chargingCurveModel", "Non-linear SoC-dependent constant-current/constant-voltage (CC/CV) model with tapering above 80% SoC");
        assumptions.put("chargingEfficiency", efficiency);
        assumptions.put("powerFactor", transformer.getPowerFactor());
        assumptions.put("turnaroundBufferPolicy", "Mandatory " + turnaroundBuffer + "-min buffer enforced");
        assumptions.put("activeWarningThreshold", riskConfig.getWarningThresholdPercent() + "% (" + riskConfig.getWarningThresholdKw() + " kW)");
        assumptions.put("activeApprovedLimit", riskConfig.getApprovedLimitKw() + " kW");

        return new RecommendationResult(
                Math.round(batteryEnergyRequiredKwh * 10.0) / 10.0,
                gridEnergyRequiredKwh,
                efficiency,
                Math.round(effectiveGridPowerKw * 10.0) / 10.0,
                apparentPowerKva,
                chargingDurationMinutes,
                turnaroundBuffer,
                totalOccupancyMinutes,
                isRequestedFeasible,
                requestedEval.riskClassification(),
                riskConfig.getWarningThresholdKw(),
                riskConfig.getApprovedLimitKw(),
                riskConfig.getConfigVersion(),
                rejectionReason,
                requestedEval.intervalEvaluations(),
                alternatives,
                assumptions,
                LocalDateTime.now().toString()
        );
    }

    /**
     * Helper to evaluate every 15-minute slice across [startTime, endTime].
     */
    private SessionEvaluation evaluateSessionIntervals(
            Transformer transformer,
            TransformerRiskConfig riskConfig,
            Connector connector,
            LocalDateTime startTime,
            LocalDateTime endTime,
            double sessionDemandKw) {

        double warningThresholdKw = riskConfig.getWarningThresholdKw();
        double approvedLimitKw = Math.min(transformer.getApprovedLimitKw(), riskConfig.getApprovedLimitKw());
        double safetyMarginPercent = riskConfig.getSafetyMarginPercent();
        double safeOperatingCapacity = approvedLimitKw * (1.0 - (safetyMarginPercent / 100.0));

        // 1. Check connector occupancy
        List<Booking> connectorOverlaps = bookingRepository.findOverlappingBookings(
                connector.getId(), startTime, endTime,
                Arrays.asList(BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN, BookingStatus.IN_PROGRESS));

        if (!connectorOverlaps.isEmpty()) {
            return new SessionEvaluation(
                    false, "CRITICAL", 0.0,
                    "Connector #" + connector.getConnectorNumber() + " is already occupied/reserved during this time window.",
                    Collections.emptyList());
        }

        // 2. Fetch existing bookings on this transformer
        List<Booking> transformerOverlaps = bookingRepository.findTransformerOverlappingBookings(
                transformer.getId(), startTime, endTime,
                Arrays.asList(BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN, BookingStatus.IN_PROGRESS));

        List<ChargingSession> activeSessions = sessionRepository.findActiveSessionsByTransformerId(transformer.getId());

        List<IntervalSliceEvaluation> slices = new ArrayList<>();
        LocalDateTime slice = startTime;
        boolean exceededWarning = false;
        boolean exceededLimit = false;
        double minHeadroom = approvedLimitKw;
        String rejectionExplanation = null;

        while (slice.isBefore(endTime)) {
            LocalDateTime sliceEnd = slice.plusMinutes(15);
            if (sliceEnd.isAfter(endTime)) sliceEnd = endTime;

            double existingBaseline = riskConfig.getNonEvBaselineKw();
            // time-of-day baseline boost between 17:00 and 22:00
            if (slice.getHour() >= 17 && slice.getHour() <= 22) {
                existingBaseline *= 1.25;
            }

            double activeSessionsLoad = 0.0;
            if (slice.isBefore(LocalDateTime.now().plusMinutes(30))) {
                for (ChargingSession s : activeSessions) {
                    activeSessionsLoad += s.getCurrentPowerKw();
                }
            }

            double bookingsLoad = 0.0;
            for (Booking b : transformerOverlaps) {
                if (b.getStartTime().isBefore(sliceEnd) && b.getEndTime().isAfter(slice)) {
                    bookingsLoad += b.getAllocatedPowerKw();
                }
            }

            double existingTotalLoad = existingBaseline + activeSessionsLoad + bookingsLoad;
            double resultingTotalLoad = existingTotalLoad + sessionDemandKw;

            double headroomBelowWarning = warningThresholdKw - resultingTotalLoad;
            if (headroomBelowWarning < minHeadroom) {
                minHeadroom = headroomBelowWarning;
            }

            String sliceStatus = "NORMAL";
            if (resultingTotalLoad > approvedLimitKw) {
                sliceStatus = "PROHIBITED_OVERLOAD";
                exceededLimit = true;
                if (rejectionExplanation == null) {
                    rejectionExplanation = String.format(
                            "Transformer approved electrical limit violated at %s: Resulting load %.1f kW exceeds approved limit %.1f kW.",
                            slice.toLocalTime(), resultingTotalLoad, approvedLimitKw);
                }
            } else if (resultingTotalLoad >= warningThresholdKw || resultingTotalLoad > safeOperatingCapacity) {
                sliceStatus = "WARNING_THRESHOLD_EXCEEDED";
                exceededWarning = true;
                if (rejectionExplanation == null) {
                    rejectionExplanation = String.format(
                            "User-configured risk warning threshold exceeded at %s: Resulting load %.1f kW crosses configured warning threshold %.1f kW (Approved Limit: %.1f kW).",
                            slice.toLocalTime(), resultingTotalLoad, warningThresholdKw, approvedLimitKw);
                }
            }

            slices.add(new IntervalSliceEvaluation(
                    slice.toString(),
                    sliceEnd.toString(),
                    Math.round(existingTotalLoad * 10.0) / 10.0,
                    Math.round(sessionDemandKw * 10.0) / 10.0,
                    Math.round(resultingTotalLoad * 10.0) / 10.0,
                    warningThresholdKw,
                    approvedLimitKw,
                    sliceStatus
            ));

            slice = slice.plusMinutes(15);
        }

        String overallRisk = "NORMAL";
        boolean feasible = true;

        if (exceededLimit) {
            overallRisk = "CRITICAL";
            feasible = false;
        } else if (exceededWarning) {
            overallRisk = "AT_RISK";
            feasible = false; // per Section 2: when forecast crosses warning threshold, apply high risk policy and offer alternatives!
        }

        return new SessionEvaluation(feasible, overallRisk, Math.max(0.0, minHeadroom), rejectionExplanation, slices);
    }

    public record RecommendationRequest(
            Long customerId,
            Long vehicleId,
            Long stationId,
            LocalDateTime preferredStartTime,
            Integer currentSoC,
            Integer targetSoC,
            Double chargingEfficiency,
            Integer maxWaitingHours
    ) {}

    public record RecommendationResult(
            double batteryEnergyRequiredKwh,
            double gridEnergyRequiredKwh,
            double chargingEfficiency,
            double estimatedChargingPowerKw,
            double apparentPowerKva,
            int estimatedChargingDurationMinutes,
            int turnaroundBufferMinutes,
            int totalOccupancyMinutes,
            boolean isRequestedTimeFeasible,
            String riskClassification,
            double activeWarningThresholdKw,
            double activeApprovedLimitKw,
            int configVersion,
            String rejectionReason,
            List<IntervalSliceEvaluation> intervalEvaluations,
            List<AlternativeSlot> feasibleAlternativeSlots,
            Map<String, Object> estimationAssumptions,
            String forecastTimestamp
    ) {}

    public record IntervalSliceEvaluation(
            String sliceStart,
            String sliceEnd,
            double existingLoadKw,
            double proposedSessionDemandKw,
            double resultingTotalLoadKw,
            double warningThresholdKw,
            double approvedLimitKw,
            String intervalStatus
    ) {}

    public record AlternativeSlot(
            String candidateStartTime,
            String candidateEndTime,
            Long connectorId,
            Integer connectorNumber,
            double estimatedTariffPerKwh,
            double estimatedTotalCost,
            double safetyHeadroomKw,
            int waitingTimeMinutes,
            String riskStatus
    ) {}

    private record SessionEvaluation(
            boolean isFeasible,
            String riskClassification,
            double minHeadroomBelowWarningKw,
            String rejectionReason,
            List<IntervalSliceEvaluation> intervalEvaluations
    ) {}
}
