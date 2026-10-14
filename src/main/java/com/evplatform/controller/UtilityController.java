package com.evplatform.controller;

import com.evplatform.domain.*;
import com.evplatform.repository.*;
import com.evplatform.service.AuditService;
import com.evplatform.service.CapacityManagementService;
import com.evplatform.service.ForecastingService;
import com.evplatform.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/utility")
public class UtilityController {

    private final TransformerRepository transformerRepository;
    private final ShopRepository shopRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final LoadForecastRepository forecastRepository;
    private final CapacityRequestRepository capacityRequestRepository;
    private final CapacityManagementService capacityManagementService;
    private final ForecastingService forecastingService;
    private final ChargingSessionRepository sessionRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final com.evplatform.service.SmartChargingRecommendationService recommendationService;

    public UtilityController(
            TransformerRepository transformerRepository,
            ShopRepository shopRepository,
            MeterReadingRepository meterReadingRepository,
            LoadForecastRepository forecastRepository,
            CapacityRequestRepository capacityRequestRepository,
            CapacityManagementService capacityManagementService,
            ForecastingService forecastingService,
            ChargingSessionRepository sessionRepository,
            AuditService auditService,
            NotificationService notificationService,
            com.evplatform.service.SmartChargingRecommendationService recommendationService) {
        this.transformerRepository = transformerRepository;
        this.shopRepository = shopRepository;
        this.meterReadingRepository = meterReadingRepository;
        this.forecastRepository = forecastRepository;
        this.capacityRequestRepository = capacityRequestRepository;
        this.capacityManagementService = capacityManagementService;
        this.forecastingService = forecastingService;
        this.sessionRepository = sessionRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.recommendationService = recommendationService;
    }

    // --- UTILITY DASHBOARD ---
    @GetMapping("/dashboard")
    public ResponseEntity<?> getUtilityDashboard() {
        List<Transformer> transformers = transformerRepository.findAll();
        if (transformers.isEmpty()) {
            return ResponseEntity.ok(Map.of());
        }

        Transformer t = transformers.get(0);
        List<Shop> shops = shopRepository.findByTransformerId(t.getId());

        List<ChargingSession> activeSessions = sessionRepository.findActiveSessionsByTransformerId(t.getId());
        double activeEvLoad = activeSessions.stream().mapToDouble(ChargingSession::getCurrentPowerKw).sum();
        double currentTotalLoad = t.getNonEvBaselineKw() + activeEvLoad;
        double apparentKva = currentTotalLoad / t.getPowerFactor();
        double usableEvBudget = t.getUsableEvBudgetKw();
        double remainingHeadroom = Math.max(0.0, usableEvBudget - activeEvLoad);

        // Fetch upcoming risk alerts
        List<LoadForecast> riskForecasts = forecastRepository.findActiveRiskForecasts(t.getId(), LocalDateTime.now());

        return ResponseEntity.ok(Map.of(
                "transformer", t,
                "connectedShopsCount", shops.size(),
                "activeEvLoadKw", Math.round(activeEvLoad * 10.0) / 10.0,
                "currentTotalLoadKw", Math.round(currentTotalLoad * 10.0) / 10.0,
                "apparentKva", Math.round(apparentKva * 10.0) / 10.0,
                "usableEvBudgetKw", Math.round(usableEvBudget * 10.0) / 10.0,
                "remainingHeadroomKw", Math.round(remainingHeadroom * 10.0) / 10.0,
                "riskAlertsCount", riskForecasts.size(),
                "gridHealthStatus", (riskForecasts.isEmpty() ? "HEALTHY" : (currentTotalLoad > t.getApprovedLimitKw() ? "CRITICAL" : "AT_RISK"))
        ));
    }

    // --- TRANSFORMERS ---
    @GetMapping("/transformers")
    public ResponseEntity<List<Transformer>> getTransformers() {
        return ResponseEntity.ok(transformerRepository.findAll());
    }

    @GetMapping("/transformers/{id}")
    public ResponseEntity<?> getTransformerDetails(@PathVariable Long id) {
        Transformer t = transformerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transformer not found: " + id));
        List<Shop> shops = shopRepository.findByTransformerId(id);

        return ResponseEntity.ok(Map.of(
                "transformer", t,
                "connectedShops", shops,
                "usableEvBudgetKw", t.getUsableEvBudgetKw()
        ));
    }

    @PutMapping("/transformers/{id}/limits")
    public ResponseEntity<?> updateTransformerLimits(
            @PathVariable Long id,
            @RequestBody TransformerLimitUpdateRequest req) {

        Transformer t = transformerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transformer not found: " + id));

        double oldLimit = t.getApprovedLimitKw();
        double oldMargin = t.getSafetyMarginPercent();

        if (req.approvedLimitKw() != null) t.setApprovedLimitKw(req.approvedLimitKw());
        if (req.safetyMarginPercent() != null) t.setSafetyMarginPercent(req.safetyMarginPercent());
        if (req.nonEvBaselineKw() != null) t.setNonEvBaselineKw(req.nonEvBaselineKw());
        t.setLastUpdatedAt(LocalDateTime.now());
        t.setUpdatedBy(req.updatedBy() != null ? req.updatedBy() : "UTILITY_ADMIN");

        Transformer saved = transformerRepository.save(t);

        // Audit log limit changes (Section 5 requirement)
        auditService.logAction(
                req.updatedBy() != null ? req.updatedBy() : "UTILITY_ADMIN",
                "UTILITY_ADMIN",
                "TRANSFORMER_LIMITS_UPDATED",
                "Transformer", id,
                String.format("Updated limits for %s: Limit %.1f kW -> %.1f kW, Safety Margin %.1f%% -> %.1f%%. Reason: %s",
                        t.getCode(), oldLimit, t.getApprovedLimitKw(), oldMargin, t.getSafetyMarginPercent(), req.reason()),
                "127.0.0.1"
        );

        // Reforecast after limit changes
        forecastingService.generateForecastForTransformer(id);

        return ResponseEntity.ok(saved);
    }

    // --- USER-CONFIGURED RISK THRESHOLDS (SECTION 2 CRITICAL REQUIREMENT) ---
    @GetMapping("/transformers/{id}/risk-config")
    public ResponseEntity<?> getTransformerRiskConfig(@PathVariable Long id) {
        Transformer t = transformerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transformer not found: " + id));
        TransformerRiskConfig config = recommendationService.getActiveRiskConfig(t);
        return ResponseEntity.ok(config);
    }

    @PutMapping("/transformers/{id}/risk-config")
    public ResponseEntity<?> updateTransformerRiskConfig(
            @PathVariable Long id,
            @RequestBody RiskConfigUpdateRequest req) {
        try {
            TransformerRiskConfig updated = recommendationService.updateRiskConfig(
                    id,
                    req.approvedLimitKw(),
                    req.warningThresholdPercent(),
                    req.criticalThresholdPercent(),
                    req.safetyMarginPercent(),
                    req.nonEvBaselineKw(),
                    req.highRiskPolicy(),
                    req.criticalRiskPolicy(),
                    req.updatedBy() != null ? req.updatedBy() : "UTILITY_ADMIN",
                    req.changeReason() != null ? req.changeReason() : "User configured risk thresholds via Utility Portal"
            );

            // Audit log the critical threshold update
            auditService.logAction(
                    req.updatedBy() != null ? req.updatedBy() : "UTILITY_ADMIN",
                    "UTILITY_ADMIN",
                    "TRANSFORMER_RISK_THRESHOLD_UPDATED",
                    "TransformerRiskConfig", updated.getId(),
                    String.format("Config v%d applied: ApprovedLimit=%.1fkW, Warning=%.1f%% (%.1fkW), Critical=%.1f%% (%.1fkW), Margin=%.1f%%. Reason: %s",
                            updated.getConfigVersion(), updated.getApprovedLimitKw(), updated.getWarningThresholdPercent(),
                            updated.getWarningThresholdKw(), updated.getCriticalThresholdPercent(), updated.getCriticalThresholdKw(),
                            updated.getSafetyMarginPercent(), req.changeReason()),
                    "127.0.0.1"
            );

            // Trigger reforecasting with the updated user-configured threshold
            forecastingService.generateForecastForTransformer(id);

            // Send notification to operators and shops
            notificationService.sendNotification(
                    null, Role.SHOP_OWNER, "Grid Risk Policy Updated",
                    String.format("Substation %s risk warning threshold adjusted to %.1f%% (%.1f kW). Automatic booking limits updated.",
                            updated.getTransformer().getCode(), updated.getWarningThresholdPercent(), updated.getWarningThresholdKw()),
                    "INFO", updated.getId()
            );

            return ResponseEntity.ok(updated);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
    @GetMapping("/connected-shops")
    public ResponseEntity<List<Shop>> getConnectedShops(@RequestParam(required = false, defaultValue = "1") Long transformerId) {
        return ResponseEntity.ok(shopRepository.findByTransformerId(transformerId));
    }

    // --- METER READINGS ---
    @GetMapping("/meter-readings")
    public ResponseEntity<List<MeterReading>> getMeterReadings(@RequestParam(required = false, defaultValue = "1") Long transformerId) {
        return ResponseEntity.ok(meterReadingRepository.findByTransformerIdOrderByReadingTimestampDesc(transformerId));
    }

    // --- FORECASTS ---
    @GetMapping("/forecasts")
    public ResponseEntity<?> getForecasts(@RequestParam(required = false, defaultValue = "1") Long transformerId) {
        List<LoadForecast> forecasts = forecastingService.getUpcomingForecasts(transformerId);
        if (forecasts.isEmpty()) {
            forecasts = forecastingService.generateForecastForTransformer(transformerId);
        }
        return ResponseEntity.ok(forecasts);
    }

    @PostMapping("/forecasts/recalculate")
    public ResponseEntity<?> recalculateForecast(@RequestParam(required = false, defaultValue = "1") Long transformerId) {
        return ResponseEntity.ok(forecastingService.generateForecastForTransformer(transformerId));
    }

    // --- CAPACITY REQUESTS ---
    @GetMapping("/capacity-requests")
    public ResponseEntity<List<CapacityRequest>> getCapacityRequests(@RequestParam(required = false, defaultValue = "1") Long transformerId) {
        return ResponseEntity.ok(capacityRequestRepository.findByTransformerIdOrderByRequestedAtDesc(transformerId));
    }

    @PostMapping("/capacity-requests/{id}/review")
    public ResponseEntity<?> reviewCapacityRequest(
            @PathVariable Long id,
            @RequestBody ReviewRequest req) {
        try {
            return ResponseEntity.ok(capacityManagementService.reviewCapacityRequest(
                    id, req.approve(), req.reviewerUsername()));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    // --- EMERGENCY CURTAILMENT / SAFE POWER REALLOCATION ---
    @PostMapping("/curtailment")
    public ResponseEntity<?> triggerEmergencyCurtailment(
            @RequestParam(required = false, defaultValue = "1") Long transformerId,
            @RequestBody CurtailmentRequest req) {

        Transformer t = transformerRepository.findById(transformerId)
                .orElseThrow(() -> new IllegalArgumentException("Transformer not found: " + transformerId));

        boolean success = capacityManagementService.attemptSafePowerReallocation(t, req.requiredReductionKw());

        auditService.logAction(req.operatorUsername(), "UTILITY_OPERATOR", "EMERGENCY_CURTAILMENT",
                "Transformer", transformerId,
                String.format("Executed emergency load throttling for %.1f kW headroom. Success: %b", req.requiredReductionKw(), success),
                "127.0.0.1");

        forecastingService.generateForecastForTransformer(transformerId);

        return ResponseEntity.ok(Map.of(
                "success", success,
                "message", success ? "Successfully reallocated power across throttle-capable chargers." : "Insufficient throttle-capable sessions to meet full target reduction."
        ));
    }

    // --- AUDIT LOGS ---
    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLog>> getAuditLogs() {
        return ResponseEntity.ok(auditService.getRecentLogs());
    }

    public record TransformerLimitUpdateRequest(Double approvedLimitKw, Double safetyMarginPercent, Double nonEvBaselineKw, String updatedBy, String reason) {}
    public record RiskConfigUpdateRequest(Double approvedLimitKw, Double warningThresholdPercent, Double criticalThresholdPercent, Double safetyMarginPercent, Double nonEvBaselineKw, String highRiskPolicy, String criticalRiskPolicy, String updatedBy, String changeReason) {}
    public record ReviewRequest(boolean approve, String reviewerUsername) {}
    public record CurtailmentRequest(double requiredReductionKw, String operatorUsername) {}
}
