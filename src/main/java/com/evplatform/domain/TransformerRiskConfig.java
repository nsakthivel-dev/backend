package com.evplatform.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "transformer_risk_configs", indexes = {
    @Index(name = "idx_risk_config_transformer", columnList = "transformer_id, is_active")
})
public class TransformerRiskConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "transformer_id", nullable = false)
    private Transformer transformer;

    @Column(name = "config_version", nullable = false)
    private Integer configVersion = 1;

    @Column(name = "approved_limit_kw", nullable = false)
    private Double approvedLimitKw;

    @Column(name = "warning_threshold_percent", nullable = false)
    private Double warningThresholdPercent = 90.0; // e.g., 90% of approved limit

    @Column(name = "critical_threshold_percent", nullable = false)
    private Double criticalThresholdPercent = 98.0; // e.g., 98% of approved limit

    @Column(name = "safety_margin_percent", nullable = false)
    private Double safetyMarginPercent = 10.0; // required safety reserve/uncertainty margin

    @Column(name = "non_ev_baseline_kw", nullable = false)
    private Double nonEvBaselineKw = 75.0;

    @Column(name = "high_risk_policy", length = 100)
    private String highRiskPolicy = "RESTRICT_NEW_ONLINE_BOOKINGS,OFFER_ALTERNATIVES";

    @Column(name = "critical_risk_policy", length = 100)
    private String criticalRiskPolicy = "EMERGENCY_THROTTLING,PROHIBIT_NEW_DEMAND";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom = LocalDateTime.now();

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "UTILITY_ADMIN";

    @Column(name = "change_reason", length = 500)
    private String changeReason = "Initial baseline configuration";

    public TransformerRiskConfig() {}

    public TransformerRiskConfig(
            Transformer transformer,
            Integer configVersion,
            Double approvedLimitKw,
            Double warningThresholdPercent,
            Double criticalThresholdPercent,
            Double safetyMarginPercent,
            Double nonEvBaselineKw,
            String highRiskPolicy,
            String criticalRiskPolicy,
            String updatedBy,
            String changeReason) {
        this.transformer = transformer;
        this.configVersion = configVersion != null ? configVersion : 1;
        this.approvedLimitKw = approvedLimitKw;
        this.warningThresholdPercent = warningThresholdPercent != null ? warningThresholdPercent : 90.0;
        this.criticalThresholdPercent = criticalThresholdPercent != null ? criticalThresholdPercent : 98.0;
        this.safetyMarginPercent = safetyMarginPercent != null ? safetyMarginPercent : 10.0;
        this.nonEvBaselineKw = nonEvBaselineKw != null ? nonEvBaselineKw : 75.0;
        this.highRiskPolicy = highRiskPolicy != null ? highRiskPolicy : "RESTRICT_NEW_ONLINE_BOOKINGS,OFFER_ALTERNATIVES";
        this.criticalRiskPolicy = criticalRiskPolicy != null ? criticalRiskPolicy : "EMERGENCY_THROTTLING,PROHIBIT_NEW_DEMAND";
        this.isActive = true;
        this.effectiveFrom = LocalDateTime.now();
        this.updatedBy = updatedBy != null ? updatedBy : "UTILITY_ADMIN";
        this.changeReason = changeReason;
    }

    /**
     * Calculated Warning Level in kW = Approved Limit * (Warning Threshold % / 100)
     */
    public double getWarningThresholdKw() {
        return approvedLimitKw * (warningThresholdPercent / 100.0);
    }

    /**
     * Calculated Critical Level in kW = Approved Limit * (Critical Threshold % / 100)
     */
    public double getCriticalThresholdKw() {
        return approvedLimitKw * (criticalThresholdPercent / 100.0);
    }

    /**
     * Usable EV budget considering safety margin and baseline non-EV load
     */
    public double getUsableEvBudgetKw() {
        double safeOperatingCapacity = approvedLimitKw * (1.0 - (safetyMarginPercent / 100.0));
        return Math.max(0.0, safeOperatingCapacity - nonEvBaselineKw);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Transformer getTransformer() { return transformer; }
    public void setTransformer(Transformer transformer) { this.transformer = transformer; }

    public Integer getConfigVersion() { return configVersion; }
    public void setConfigVersion(Integer configVersion) { this.configVersion = configVersion; }

    public Double getApprovedLimitKw() { return approvedLimitKw; }
    public void setApprovedLimitKw(Double approvedLimitKw) { this.approvedLimitKw = approvedLimitKw; }

    public Double getWarningThresholdPercent() { return warningThresholdPercent; }
    public void setWarningThresholdPercent(Double warningThresholdPercent) { this.warningThresholdPercent = warningThresholdPercent; }

    public Double getCriticalThresholdPercent() { return criticalThresholdPercent; }
    public void setCriticalThresholdPercent(Double criticalThresholdPercent) { this.criticalThresholdPercent = criticalThresholdPercent; }

    public Double getSafetyMarginPercent() { return safetyMarginPercent; }
    public void setSafetyMarginPercent(Double safetyMarginPercent) { this.safetyMarginPercent = safetyMarginPercent; }

    public Double getNonEvBaselineKw() { return nonEvBaselineKw; }
    public void setNonEvBaselineKw(Double nonEvBaselineKw) { this.nonEvBaselineKw = nonEvBaselineKw; }

    public String getHighRiskPolicy() { return highRiskPolicy; }
    public void setHighRiskPolicy(String highRiskPolicy) { this.highRiskPolicy = highRiskPolicy; }

    public String getCriticalRiskPolicy() { return criticalRiskPolicy; }
    public void setCriticalRiskPolicy(String criticalRiskPolicy) { this.criticalRiskPolicy = criticalRiskPolicy; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }

    public LocalDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
}
