package com.evplatform.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "meter_readings", indexes = {
    @Index(name = "idx_meter_transformer_time", columnList = "transformer_id, reading_timestamp")
})
public class MeterReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "transformer_id", nullable = false)
    private Transformer transformer;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "shop_id")
    private Shop shop; // null if substation transformer-level reading

    @Column(name = "active_power_kw", nullable = false)
    private Double activePowerKw; // kW

    @Column(name = "apparent_power_kva", nullable = false)
    private Double apparentPowerKva; // kVA

    @Column(name = "power_factor", nullable = false)
    private Double powerFactor = 0.95;

    @Column(name = "reading_timestamp", nullable = false)
    private LocalDateTime readingTimestamp = LocalDateTime.now();

    @Column(nullable = false, length = 30)
    private String source = "SIMULATED"; // HARDWARE_TELEMETRY, SIMULATED

    @Column(name = "data_quality", nullable = false, length = 30)
    private String dataQuality = "VALID"; // VALID, STALE, IMPLAUSIBLE

    public MeterReading() {}

    public MeterReading(Transformer transformer, Shop shop, Double activePowerKw, Double apparentPowerKva, Double powerFactor, LocalDateTime readingTimestamp, String source, String dataQuality) {
        this.transformer = transformer;
        this.shop = shop;
        this.activePowerKw = activePowerKw;
        this.apparentPowerKva = apparentPowerKva;
        this.powerFactor = powerFactor != null ? powerFactor : 0.95;
        this.readingTimestamp = readingTimestamp != null ? readingTimestamp : LocalDateTime.now();
        this.source = source != null ? source : "SIMULATED";
        this.dataQuality = dataQuality != null ? dataQuality : "VALID";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Transformer getTransformer() { return transformer; }
    public void setTransformer(Transformer transformer) { this.transformer = transformer; }

    public Shop getShop() { return shop; }
    public void setShop(Shop shop) { this.shop = shop; }

    public Double getActivePowerKw() { return activePowerKw; }
    public void setActivePowerKw(Double activePowerKw) { this.activePowerKw = activePowerKw; }

    public Double getApparentPowerKva() { return apparentPowerKva; }
    public void setApparentPowerKva(Double apparentPowerKva) { this.apparentPowerKva = apparentPowerKva; }

    public Double getPowerFactor() { return powerFactor; }
    public void setPowerFactor(Double powerFactor) { this.powerFactor = powerFactor; }

    public LocalDateTime getReadingTimestamp() { return readingTimestamp; }
    public void setReadingTimestamp(LocalDateTime readingTimestamp) { this.readingTimestamp = readingTimestamp; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getDataQuality() { return dataQuality; }
    public void setDataQuality(String dataQuality) { this.dataQuality = dataQuality; }
}
