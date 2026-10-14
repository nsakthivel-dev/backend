package com.evplatform.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String make;

    @Column(nullable = false, length = 50)
    private String model;

    @Column(nullable = false, unique = true, length = 30)
    private String licensePlate;

    @Column(name = "battery_capacity_kwh")
    private Double batteryCapacityKwh;

    @Column(name = "max_charging_rate_kw")
    private Double maxChargingRateKw;

    @Column(name = "connector_type", nullable = false, length = 30)
    private String connectorType; // CCS2, CHAdeMO, Type2, Tesla

    @Column(name = "current_soc_percent", nullable = false)
    private Integer currentSoC = 30; // 0 - 100

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Vehicle() {}

    public Vehicle(User user, String make, String model, String licensePlate, Double batteryCapacityKwh, Double maxChargingRateKw, String connectorType, Integer currentSoC) {
        this.user = user;
        this.make = make;
        this.model = model;
        this.licensePlate = licensePlate;
        this.batteryCapacityKwh = batteryCapacityKwh;
        this.maxChargingRateKw = maxChargingRateKw;
        this.connectorType = connectorType;
        this.currentSoC = currentSoC != null ? currentSoC : 30;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getMake() { return make; }
    public void setMake(String make) { this.make = make; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }

    public Double getBatteryCapacityKwh() { return batteryCapacityKwh; }
    public void setBatteryCapacityKwh(Double batteryCapacityKwh) { this.batteryCapacityKwh = batteryCapacityKwh; }

    public Double getMaxChargingRateKw() { return maxChargingRateKw; }
    public void setMaxChargingRateKw(Double maxChargingRateKw) { this.maxChargingRateKw = maxChargingRateKw; }

    public String getConnectorType() { return connectorType; }
    public void setConnectorType(String connectorType) { this.connectorType = connectorType; }

    public Integer getCurrentSoC() { return currentSoC; }
    public void setCurrentSoC(Integer currentSoC) { this.currentSoC = currentSoC; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
