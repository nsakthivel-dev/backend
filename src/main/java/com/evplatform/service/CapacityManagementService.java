package com.evplatform.service;

import com.evplatform.domain.*;
import com.evplatform.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class CapacityManagementService {

    private final TransformerRepository transformerRepository;
    private final ShopRepository shopRepository;
    private final BookingRepository bookingRepository;
    private final ChargingSessionRepository sessionRepository;
    private final CapacityAllocationRepository allocationRepository;
    private final CapacityRequestRepository capacityRequestRepository;
    private final AlgorithmEngineService algorithmEngineService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final TransformerRiskConfigRepository riskConfigRepository;

    public CapacityManagementService(
            TransformerRepository transformerRepository,
            ShopRepository shopRepository,
            BookingRepository bookingRepository,
            ChargingSessionRepository sessionRepository,
            CapacityAllocationRepository allocationRepository,
            CapacityRequestRepository capacityRequestRepository,
            AlgorithmEngineService algorithmEngineService,
            NotificationService notificationService,
            AuditService auditService,
            TransformerRiskConfigRepository riskConfigRepository) {
        this.transformerRepository = transformerRepository;
        this.shopRepository = shopRepository;
        this.bookingRepository = bookingRepository;
        this.sessionRepository = sessionRepository;
        this.allocationRepository = allocationRepository;
        this.capacityRequestRepository = capacityRequestRepository;
        this.algorithmEngineService = algorithmEngineService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.riskConfigRepository = riskConfigRepository;
    }

    /**
     * Checks if requested power can be safely allocated on the transformer across [startTime, endTime].
     * Evaluates multiple 15-minute slices across the occupancy interval.
     */
    public CapacityCheckResult checkTransformerCapacity(Transformer transformer, LocalDateTime startTime, LocalDateTime endTime, double requestedKw) {
        // Load authoritative user-configured risk thresholds (Section 2 critical requirement)
        TransformerRiskConfig riskConfig = riskConfigRepository.findActiveByTransformerId(transformer.getId()).orElse(null);

        double approvedLimit = (riskConfig != null && riskConfig.getApprovedLimitKw() != null)
                ? Math.min(transformer.getApprovedLimitKw(), riskConfig.getApprovedLimitKw())
                : transformer.getApprovedLimitKw();
        double warningThresholdKw = (riskConfig != null) ? riskConfig.getWarningThresholdKw() : (approvedLimit * 0.90);
        double safetyMargin = (riskConfig != null) ? riskConfig.getSafetyMarginPercent() : transformer.getSafetyMarginPercent();
        double baselineNonEv = (riskConfig != null) ? riskConfig.getNonEvBaselineKw() : transformer.getNonEvBaselineKw();

        double safeOperatingCapacity = approvedLimit * (1.0 - (safetyMargin / 100.0));
        double usableBudget = Math.max(0.0, safeOperatingCapacity - baselineNonEv);

        if (usableBudget <= 0.0) {
            return new CapacityCheckResult(false, 0.0, 0.0, usableBudget, "Transformer has zero usable EV budget under current user-configured safety margin and baseline.");
        }

        List<BookingStatus> activeStatuses = Arrays.asList(
                BookingStatus.CONFIRMED,
                BookingStatus.CHECKED_IN,
                BookingStatus.IN_PROGRESS
        );

        // Fetch all bookings overlapping with the interval on this transformer
        List<Booking> overlappingBookings = bookingRepository.findTransformerOverlappingBookings(
                transformer.getId(), startTime, endTime, activeStatuses);

        // Fetch active charging sessions on this transformer
        List<ChargingSession> activeSessions = sessionRepository.findActiveSessionsByTransformerId(transformer.getId());

        // Step through in 15-minute intervals across the ENTIRE requested session duration
        LocalDateTime sliceStart = startTime;
        double maxCommitted = 0.0;
        double minRemainingHeadroom = usableBudget;

        while (sliceStart.isBefore(endTime)) {
            LocalDateTime sliceEnd = sliceStart.plusMinutes(15);
            if (sliceEnd.isAfter(endTime)) sliceEnd = endTime;

            double sliceLoad = 0.0;

            // Add active sessions if slice covers current time
            LocalDateTime now = LocalDateTime.now();
            if (sliceStart.isBefore(now.plusMinutes(15)) && sliceEnd.isAfter(now)) {
                for (ChargingSession session : activeSessions) {
                    sliceLoad += session.getCurrentPowerKw();
                }
            }

            // Add confirmed bookings active in this slice
            for (Booking b : overlappingBookings) {
                if (b.getStartTime().isBefore(sliceEnd) && b.getEndTime().isAfter(sliceStart)) {
                    sliceLoad += b.getAllocatedPowerKw();
                }
            }

            if (sliceLoad > maxCommitted) {
                maxCommitted = sliceLoad;
            }

            double totalGridDemand = baselineNonEv + sliceLoad + requestedKw;
            double remainingInSlice = usableBudget - sliceLoad;
            if (remainingInSlice < minRemainingHeadroom) {
                minRemainingHeadroom = remainingInSlice;
            }

            // Section 2: Validate against user-configured warning threshold and approved limit
            if (totalGridDemand > approvedLimit) {
                return new CapacityCheckResult(
                        false,
                        maxCommitted,
                        minRemainingHeadroom,
                        usableBudget,
                        String.format("Transformer electrical limit violated at %s: Resulting demand %.1f kW exceeds approved limit %.1f kW.",
                                sliceStart.toLocalTime(), totalGridDemand, approvedLimit)
                );
            }

            if (totalGridDemand >= warningThresholdKw || remainingInSlice < requestedKw) {
                return new CapacityCheckResult(
                        false,
                        maxCommitted,
                        minRemainingHeadroom,
                        usableBudget,
                        String.format("User-configured risk warning threshold exceeded at %s: Resulting load %.1f kW crosses warning threshold %.1f kW (Approved Limit: %.1f kW).",
                                sliceStart.toLocalTime(), totalGridDemand, warningThresholdKw, approvedLimit)
                );
            }

            sliceStart = sliceStart.plusMinutes(15);
        }

        return new CapacityCheckResult(true, maxCommitted, minRemainingHeadroom, usableBudget, "Sufficient capacity available.");
    }

    /**
     * Attempts safe power reallocation across active charging sessions if compatible chargers support it.
     */
    @Transactional
    public boolean attemptSafePowerReallocation(Transformer transformer, double requiredHeadroomKw) {
        List<ChargingSession> activeSessions = sessionRepository.findActiveSessionsByTransformerId(transformer.getId());
        double freedKw = 0.0;

        for (ChargingSession session : activeSessions) {
            Charger charger = session.getConnector().getCharger();
            if (charger.getSupportedControlCapabilities() != null &&
                charger.getSupportedControlCapabilities().contains("DYNAMIC_POWER_CURTAILMENT")) {

                double currentPwr = session.getCurrentPowerKw();
                if (currentPwr > 25.0) { // Throttle down to minimum 25 kW
                    double curtail = Math.min(currentPwr - 25.0, requiredHeadroomKw - freedKw);
                    session.setCurrentPowerKw(currentPwr - curtail);
                    sessionRepository.save(session);
                    freedKw += curtail;

                    auditService.logAction("SYSTEM_AUTONOMOUS", "SYSTEM", "SAFE_POWER_REALLOCATION",
                            "ChargingSession", session.getId(),
                            String.format("Curtailed session %s from %.1f kW to %.1f kW to relieve transformer pressure",
                                    session.getSessionReference(), currentPwr, session.getCurrentPowerKw()), "127.0.0.1");

                    if (freedKw >= requiredHeadroomKw) {
                        return true;
                    }
                }
            }
        }
        return freedKw >= requiredHeadroomKw;
    }

    @Transactional
    public CapacityRequest submitCapacityRequest(Long shopId, double requestedKw, String reason) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new IllegalArgumentException("Shop not found: " + shopId));
        CapacityRequest request = new CapacityRequest(shop.getTransformer(), shop, requestedKw, reason);
        return capacityRequestRepository.save(request);
    }

    @Transactional
    public CapacityRequest reviewCapacityRequest(Long requestId, boolean approve, String reviewerUsername) {
        CapacityRequest request = capacityRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Capacity request not found: " + requestId));

        request.setReviewedAt(LocalDateTime.now());
        request.setReviewedBy(reviewerUsername);

        if (approve) {
            request.setStatus("APPROVED");
            Shop shop = request.getShop();
            shop.setAllocatedCapacityKw(shop.getAllocatedCapacityKw() + request.getRequestedAdditionalKw());
            shopRepository.save(shop);

            notificationService.sendNotification(null, Role.SHOP_OWNER, "Capacity Request Approved",
                    String.format("Your request for %.1f kW additional capacity was approved.", request.getRequestedAdditionalKw()),
                    "INFO", request.getId());
            auditService.logAction(reviewerUsername, "UTILITY_ADMIN", "CAPACITY_REQUEST_APPROVED",
                    "Shop", shop.getId(), "Approved " + request.getRequestedAdditionalKw() + " kW additional capacity", "127.0.0.1");
        } else {
            request.setStatus("REJECTED");
            notificationService.sendNotification(null, Role.SHOP_OWNER, "Capacity Request Rejected",
                    "Your request for additional capacity was reviewed and declined based on current grid stability limits.",
                    "WARNING", request.getId());
            auditService.logAction(reviewerUsername, "UTILITY_ADMIN", "CAPACITY_REQUEST_REJECTED",
                    "Shop", request.getShop().getId(), "Rejected additional capacity request", "127.0.0.1");
        }

        return capacityRequestRepository.save(request);
    }

    public record CapacityCheckResult(
            boolean isFeasible,
            double maxCommittedKw,
            double remainingHeadroomKw,
            double usableBudgetKw,
            String message
    ) {}
}
