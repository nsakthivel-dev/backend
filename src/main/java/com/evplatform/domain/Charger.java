package com.evplatform.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chargers")
public class Charger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(nullable = false, length = 50)
    private String code; // e.g. "CH-01"

    @Column(length = 100)
    private String model;

    @Column(name = "rated_power_kw", nullable = false)
    private Double ratedPowerKw = 50.0; // e.g. 50kW, 150kW

    @Column(name = "supported_control_capabilities", length = 150)
    private String supportedControlCapabilities = "DYNAMIC_POWER_CURTAILMENT,SMART_THROTTLING";

    @Column(nullable = false, length = 30)
    private String status = "OPERATIONAL"; // OPERATIONAL, FAULTED, OFFLINE

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Charger() {}

    public Charger(Station station, String code, String model, Double ratedPowerKw, String supportedControlCapabilities) {
        this.station = station;
        this.code = code;
        this.model = model;
        this.ratedPowerKw = ratedPowerKw != null ? ratedPowerKw : 50.0;
        this.supportedControlCapabilities = supportedControlCapabilities;
        this.status = "OPERATIONAL";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Station getStation() { return station; }
    public void setStation(Station station) { this.station = station; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Double getRatedPowerKw() { return ratedPowerKw; }
    public void setRatedPowerKw(Double ratedPowerKw) { this.ratedPowerKw = ratedPowerKw; }

    public String getSupportedControlCapabilities() { return supportedControlCapabilities; }
    public void setSupportedControlCapabilities(String supportedControlCapabilities) { this.supportedControlCapabilities = supportedControlCapabilities; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
