package com.evplatform.service;

import com.evplatform.domain.*;
import com.evplatform.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class ForecastingService {

    private final TransformerRepository transformerRepository;
    private final LoadForecastRepository loadForecastRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final BookingRepository bookingRepository;
    private final ChargingSessionRepository sessionRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final TransformerRiskConfigRepository riskConfigRepository;

    @Value("${app.scheduling.forecast-horizon-hours:6}")
    private int forecastHorizonHours = 6;

    @Value("${app.scheduling.forecast-interval-minutes:15}")
    private int forecastIntervalMinutes = 15;

    public ForecastingService(
            TransformerRepository transformerRepository,
            LoadForecastRepository loadForecastRepository,
            MeterReadingRepository meterReadingRepository,
            BookingRepository bookingRepository,
            ChargingSessionRepository sessionRepository,
            NotificationService notificationService,
            AuditService auditService,
            TransformerRiskConfigRepository riskConfigRepository) {
        this.transformerRepository = transformerRepository;
        this.loadForecastRepository = loadForecastRepository;
        this.meterReadingRepository = meterReadingRepository;
        this.bookingRepository = bookingRepository;
        this.sessionRepository = sessionRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.riskConfigRepository = riskConfigRepository;
    }

    /**
     * Algorithm 2: Rolling Load Forecasting across future 15-minute intervals.
     */
    @Transactional
    public List<LoadForecast> generateForecastForTransformer(Long transformerId) {
        Transformer transformer = transformerRepository.findById(transformerId)
                .orElseThrow(() -> new IllegalArgumentException("Transformer not found: " + transformerId));

        LocalDateTime now = LocalDateTime.now();
        int totalSlices = (forecastHorizonHours * 60) / forecastIntervalMinutes;
        List<LoadForecast> forecasts = new ArrayList<>();

        // Fetch user-configured risk configuration (Section 2 critical requirement)
        TransformerRiskConfig riskConfig = riskConfigRepository.findActiveByTransformerId(transformerId)
                .orElse(null);

        double approvedLimit = (riskConfig != null) ? riskConfig.getApprovedLimitKw() : transformer.getApprovedLimitKw();
        double warningThresholdKw = (riskConfig != null) ? riskConfig.getWarningThresholdKw() : (approvedLimit * 0.90);
        double criticalThresholdKw = (riskConfig != null) ? riskConfig.getCriticalThresholdKw() : (approvedLimit * 0.98);
        double safetyMargin = (riskConfig != null) ? riskConfig.getSafetyMarginPercent() : transformer.getSafetyMarginPercent();
        double safeOperatingCapacity = approvedLimit * (1.0 - (safetyMargin / 100.0));
        double baselineNonEv = (riskConfig != null) ? riskConfig.getNonEvBaselineKw() : transformer.getNonEvBaselineKw();

        // Get active sessions
        List<ChargingSession> activeSessions = sessionRepository.findActiveSessionsByTransformerId(transformerId);
        double activeSessionsPower = activeSessions.stream()
                .mapToDouble(ChargingSession::getCurrentPowerKw)
                .sum();

        // Get confirmed future bookings
        LocalDateTime horizonEnd = now.plusHours(forecastHorizonHours);
        List<Booking> futureBookings = bookingRepository.findTransformerOverlappingBookings(
                transformerId, now, horizonEnd,
                Arrays.asList(BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN, BookingStatus.IN_PROGRESS));

        boolean foundAtRisk = false;
        boolean foundCritical = false;

        for (int i = 0; i < totalSlices; i++) {
            LocalDateTime sliceStart = now.plusMinutes((long) i * forecastIntervalMinutes);
            LocalDateTime sliceEnd = sliceStart.plusMinutes(forecastIntervalMinutes);

            // Active sessions only contribute heavily to the immediate next 30-45 minutes
            double activeLoadInSlice = (i < 3) ? activeSessionsPower : 0.0;

            // Bookings scheduled for this slice
            double bookingsLoadInSlice = 0.0;
            for (Booking b : futureBookings) {
                if (b.getStartTime().isBefore(sliceEnd) && b.getEndTime().isAfter(sliceStart)) {
                    bookingsLoadInSlice += b.getAllocatedPowerKw();
                }
            }

            // Historical walk-in pattern simulation by hour: peak from 14:00 to 20:00
            int hour = sliceStart.getHour();
            double walkInFactor = (hour >= 14 && hour <= 20) ? 35.0 : ((hour >= 8 && hour < 14) ? 20.0 : 8.0);

            // Time-of-day baseline variation
            double baselineInSlice = baselineNonEv * (hour >= 17 && hour <= 22 ? 1.25 : 1.0);

            double totalPredicted = baselineInSlice + activeLoadInSlice + bookingsLoadInSlice + walkInFactor;
            // 8% uncertainty upper bound
            double upperPredictionBound = totalPredicted * 1.08;

            // Apply User-Configured Risk Threshold (Section 2)
            String riskStatus = "NORMAL";
            if (totalPredicted >= criticalThresholdKw || totalPredicted > approvedLimit) {
                riskStatus = "CRITICAL";
                foundCritical = true;
            } else if (totalPredicted >= warningThresholdKw || upperPredictionBound > approvedLimit || totalPredicted > safeOperatingCapacity) {
                riskStatus = "AT_RISK";
                foundAtRisk = true;
            }

            LoadForecast forecast = new LoadForecast(
                    transformer,
                    now,
                    sliceStart,
                    sliceEnd,
                    Math.round(baselineInSlice * 10.0) / 10.0,
                    Math.round(activeLoadInSlice * 10.0) / 10.0,
                    Math.round(bookingsLoadInSlice * 10.0) / 10.0,
                    Math.round(walkInFactor * 10.0) / 10.0,
                    Math.round(totalPredicted * 10.0) / 10.0,
                    Math.round(upperPredictionBound * 10.0) / 10.0,
                    approvedLimit,
                    riskStatus,
                    0.94
            );
            forecasts.add(forecast);
        }

        loadForecastRepository.saveAll(forecasts);

        // Algorithm 7: Load risk and recovery notification triggers
        if (foundCritical) {
            notificationService.sendNotification(null, Role.UTILITY_OPERATOR,
                    "CRITICAL: Transformer Overload Risk Detected",
                    String.format("Transformer %s forecasted to exceed electrical safety limits. Automated load curtailment recommended.",
                            transformer.getCode()), "CRITICAL", transformer.getId());
            notificationService.sendNotification(null, Role.SHOP_OWNER,
                    "GRID ALERT: Transformer Operating in Critical Zone",
                    "Utility grid reports critical capacity shortfall. Throttling non-essential loads.", "CRITICAL", transformer.getId());
        } else if (foundAtRisk) {
            notificationService.sendNotification(null, Role.UTILITY_OPERATOR,
                    "WARNING: Transformer Approaching Peak Capacity",
                    String.format("Transformer %s forecast exceeds safe operating threshold. New booking intake restricted.",
                            transformer.getCode()), "WARNING", transformer.getId());
        }

        return forecasts;
    }

    public List<LoadForecast> getUpcomingForecasts(Long transformerId) {
        return loadForecastRepository.findFutureForecasts(transformerId, LocalDateTime.now().minusMinutes(15));
    }
}
