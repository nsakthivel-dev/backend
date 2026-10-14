package com.evplatform.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "load_forecasts", indexes = {
    @Index(name = "idx_forecast_transformer_interval", columnList = "transformer_id, target_interval_start")
})
public class LoadForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "transformer_id", nullable = false)
    private Transformer transformer;

    @Column(name = "forecast_time", nullable = false)
    private LocalDateTime forecastTime = LocalDateTime.now();

    @Column(name = "target_interval_start", nullable = false)
    private LocalDateTime targetIntervalStart;

    @Column(name = "target_interval_end", nullable = false)
    private LocalDateTime targetIntervalEnd;

    @Column(name = "baseline_load_kw", nullable = false)
    private Double baselineLoadKw;

    @Column(name = "active_sessions_load_kw", nullable = false)
    private Double activeSessionsLoadKw;

    @Column(name = "bookings_load_kw", nullable = false)
    private Double bookingsLoadKw;

    @Column(name = "expected_walk_in_load_kw", nullable = false)
    private Double expectedWalkInLoadKw;

    @Column(name = "total_predicted_load_kw", nullable = false)
    private Double totalPredictedLoadKw;

    @Column(name = "upper_prediction_bound_kw", nullable = false)
    private Double upperPredictionBoundKw;

    @Column(name = "approved_limit_kw", nullable = false)
    private Double approvedLimitKw;

    @Column(name = "risk_status", nullable = false, length = 30)
    private String riskStatus = "NORMAL"; // NORMAL, AT_RISK, CRITICAL

    @Column(name = "confidence_score", nullable = false)
    private Double confidenceScore = 0.95;

    public LoadForecast() {}

    public LoadForecast(Transformer transformer, LocalDateTime forecastTime, LocalDateTime targetIntervalStart, LocalDateTime targetIntervalEnd, Double baselineLoadKw, Double activeSessionsLoadKw, Double bookingsLoadKw, Double expectedWalkInLoadKw, Double totalPredictedLoadKw, Double upperPredictionBoundKw, Double approvedLimitKw, String riskStatus, Double confidenceScore) {
        this.transformer = transformer;
        this.forecastTime = forecastTime != null ? forecastTime : LocalDateTime.now();
        this.targetIntervalStart = targetIntervalStart;
        this.targetIntervalEnd = targetIntervalEnd;
        this.baselineLoadKw = baselineLoadKw;
        this.activeSessionsLoadKw = activeSessionsLoadKw;
        this.bookingsLoadKw = bookingsLoadKw;
        this.expectedWalkInLoadKw = expectedWalkInLoadKw;
        this.totalPredictedLoadKw = totalPredictedLoadKw;
        this.upperPredictionBoundKw = upperPredictionBoundKw;
        this.approvedLimitKw = approvedLimitKw;
        this.riskStatus = riskStatus;
        this.confidenceScore = confidenceScore != null ? confidenceScore : 0.95;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Transformer getTransformer() { return transformer; }
    public void setTransformer(Transformer transformer) { this.transformer = transformer; }

    public LocalDateTime getForecastTime() { return forecastTime; }
    public void setForecastTime(LocalDateTime forecastTime) { this.forecastTime = forecastTime; }

    public LocalDateTime getTargetIntervalStart() { return targetIntervalStart; }
    public void setTargetIntervalStart(LocalDateTime targetIntervalStart) { this.targetIntervalStart = targetIntervalStart; }

    public LocalDateTime getTargetIntervalEnd() { return targetIntervalEnd; }
    public void setTargetIntervalEnd(LocalDateTime targetIntervalEnd) { this.targetIntervalEnd = targetIntervalEnd; }

    public Double getBaselineLoadKw() { return baselineLoadKw; }
    public void setBaselineLoadKw(Double baselineLoadKw) { this.baselineLoadKw = baselineLoadKw; }

    public Double getActiveSessionsLoadKw() { return activeSessionsLoadKw; }
    public void setActiveSessionsLoadKw(Double activeSessionsLoadKw) { this.activeSessionsLoadKw = activeSessionsLoadKw; }

    public Double getBookingsLoadKw() { return bookingsLoadKw; }
    public void setBookingsLoadKw(Double bookingsLoadKw) { this.bookingsLoadKw = bookingsLoadKw; }

    public Double getExpectedWalkInLoadKw() { return expectedWalkInLoadKw; }
    public void setExpectedWalkInLoadKw(Double expectedWalkInLoadKw) { this.expectedWalkInLoadKw = expectedWalkInLoadKw; }

    public Double getTotalPredictedLoadKw() { return totalPredictedLoadKw; }
    public void setTotalPredictedLoadKw(Double totalPredictedLoadKw) { this.totalPredictedLoadKw = totalPredictedLoadKw; }

    public Double getUpperPredictionBoundKw() { return upperPredictionBoundKw; }
    public void setUpperPredictionBoundKw(Double upperPredictionBoundKw) { this.upperPredictionBoundKw = upperPredictionBoundKw; }

    public Double getApprovedLimitKw() { return approvedLimitKw; }
    public void setApprovedLimitKw(Double approvedLimitKw) { this.approvedLimitKw = approvedLimitKw; }

    public String getRiskStatus() { return riskStatus; }
    public void setRiskStatus(String riskStatus) { this.riskStatus = riskStatus; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }
}
