package com.evplatform.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "connectors")
public class Connector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "charger_id", nullable = false)
    private Charger charger;

    @Column(name = "connector_number", nullable = false)
    private Integer connectorNumber; // e.g. 1, 2

    @Column(name = "connector_type", nullable = false, length = 30)
    private String connectorType = "CCS2"; // CCS2, CHAdeMO, Type2

    @Column(name = "max_power_kw", nullable = false)
    private Double maxPowerKw = 50.0;

    @Column(name = "is_walk_in_reserve", nullable = false)
    private Boolean isWalkInReserve = false; // Excluded from online booking inventory, reserved for walk-in

    @Column(nullable = false, length = 30)
    private String status = "AVAILABLE"; // AVAILABLE, OCCUPIED, RESERVED, FAULTED

    @Version
    private Long version; // Optimistic locking for concurrency safety

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Connector() {}

    public Connector(Charger charger, Integer connectorNumber, String connectorType, Double maxPowerKw, Boolean isWalkInReserve) {
        this.charger = charger;
        this.connectorNumber = connectorNumber;
        this.connectorType = connectorType != null ? connectorType : "CCS2";
        this.maxPowerKw = maxPowerKw != null ? maxPowerKw : 50.0;
        this.isWalkInReserve = isWalkInReserve != null ? isWalkInReserve : false;
        this.status = "AVAILABLE";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Charger getCharger() { return charger; }
    public void setCharger(Charger charger) { this.charger = charger; }

    public Integer getConnectorNumber() { return connectorNumber; }
    public void setConnectorNumber(Integer connectorNumber) { this.connectorNumber = connectorNumber; }

    public String getConnectorType() { return connectorType; }
    public void setConnectorType(String connectorType) { this.connectorType = connectorType; }

    public Double getMaxPowerKw() { return maxPowerKw; }
    public void setMaxPowerKw(Double maxPowerKw) { this.maxPowerKw = maxPowerKw; }

    public Boolean getIsWalkInReserve() { return isWalkInReserve; }
    public void setIsWalkInReserve(Boolean walkInReserve) { isWalkInReserve = walkInReserve; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
