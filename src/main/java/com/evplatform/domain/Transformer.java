package com.evplatform.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "transformers")
public class Transformer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code; // e.g. "TR-DOWNTOWN-01"

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 150)
    private String location;

    @Column(name = "nameplate_rating_kva", nullable = false)
    private Double nameplateRatingKva; // e.g. 500 kVA

    @Column(name = "power_factor", nullable = false)
    private Double powerFactor = 0.95; // default 0.95

    @Column(name = "approved_limit_kw", nullable = false)
    private Double approvedLimitKw; // e.g. 450 kW utility approved limit

    @Column(name = "safety_margin_percent", nullable = false)
    private Double safetyMarginPercent = 10.0; // 10% reserve for electrical stability

    @Column(name = "non_ev_baseline_kw", nullable = false)
    private Double nonEvBaselineKw = 75.0; // Baseline non-EV residential/commercial load

    @Column(nullable = false, length = 30)
    private String status = "OPERATIONAL"; // OPERATIONAL, MAINTENANCE, TRIPPED, CRITICAL

    @Column(name = "updated_by", length = 100)
    private String updatedBy = "UTILITY_SYSTEM";

    @Column(name = "last_updated_at", nullable = false)
    private LocalDateTime lastUpdatedAt = LocalDateTime.now();

    public Transformer() {}

    public Transformer(String code, String name, String location, Double nameplateRatingKva, Double powerFactor, Double approvedLimitKw, Double safetyMarginPercent, Double nonEvBaselineKw) {
        this.code = code;
        this.name = name;
        this.location = location;
        this.nameplateRatingKva = nameplateRatingKva;
        this.powerFactor = powerFactor != null ? powerFactor : 0.95;
        this.approvedLimitKw = approvedLimitKw;
        this.safetyMarginPercent = safetyMarginPercent != null ? safetyMarginPercent : 10.0;
        this.nonEvBaselineKw = nonEvBaselineKw != null ? nonEvBaselineKw : 0.0;
        this.status = "OPERATIONAL";
        this.lastUpdatedAt = LocalDateTime.now();
    }

    /**
     * Usable EV charging budget in kW after accounting for safety margin and non-EV baseline.
     */
    public double getUsableEvBudgetKw() {
        double safeOperatingCapacity = approvedLimitKw * (1.0 - (safetyMarginPercent / 100.0));
        return Math.max(0.0, safeOperatingCapacity - nonEvBaselineKw);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Double getNameplateRatingKva() { return nameplateRatingKva; }
    public void setNameplateRatingKva(Double nameplateRatingKva) { this.nameplateRatingKva = nameplateRatingKva; }

    public Double getPowerFactor() { return powerFactor; }
    public void setPowerFactor(Double powerFactor) { this.powerFactor = powerFactor; }

    public Double getApprovedLimitKw() { return approvedLimitKw; }
    public void setApprovedLimitKw(Double approvedLimitKw) { this.approvedLimitKw = approvedLimitKw; }

    public Double getSafetyMarginPercent() { return safetyMarginPercent; }
    public void setSafetyMarginPercent(Double safetyMarginPercent) { this.safetyMarginPercent = safetyMarginPercent; }

    public Double getNonEvBaselineKw() { return nonEvBaselineKw; }
    public void setNonEvBaselineKw(Double nonEvBaselineKw) { this.nonEvBaselineKw = nonEvBaselineKw; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getLastUpdatedAt() { return lastUpdatedAt; }
    public void setLastUpdatedAt(LocalDateTime lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }
}
