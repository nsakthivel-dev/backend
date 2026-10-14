package com.evplatform.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "charging_sessions", indexes = {
    @Index(name = "idx_session_status", columnList = "status"),
    @Index(name = "idx_session_connector", columnList = "connector_id")
})
public class ChargingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_reference", nullable = false, unique = true, length = 64)
    private String sessionReference;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "booking_id", nullable = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "connector_id", nullable = false)
    private Connector connector;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "actual_start_time", nullable = false)
    private LocalDateTime actualStartTime = LocalDateTime.now();

    @Column(name = "actual_end_time")
    private LocalDateTime actualEndTime;

    @Column(name = "metered_kwh", nullable = false)
    private Double meteredKwh = 0.0;

    @Column(name = "current_power_kw", nullable = false)
    private Double currentPowerKw = 0.0;

    @Column(name = "current_soc_percent", nullable = false)
    private Integer currentSoC = 30;

    @Column(nullable = false, length = 30)
    private String status = "ACTIVE"; // ACTIVE, PAUSED, COMPLETED, INTERRUPTED

    @Column(name = "billed_amount")
    private Double billedAmount = 0.0;

    @Column(name = "session_notes", length = 255)
    private String sessionNotes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public ChargingSession() {}

    public ChargingSession(String sessionReference, Booking booking, Connector connector, Vehicle vehicle, Double currentPowerKw, Integer initialSoC) {
        this.sessionReference = sessionReference;
        this.booking = booking;
        this.connector = connector;
        this.vehicle = vehicle;
        this.currentPowerKw = currentPowerKw != null ? currentPowerKw : 50.0;
        this.currentSoC = initialSoC != null ? initialSoC : 30;
        this.actualStartTime = LocalDateTime.now();
        this.status = "ACTIVE";
        this.meteredKwh = 0.0;
        this.billedAmount = 0.0;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionReference() { return sessionReference; }
    public void setSessionReference(String sessionReference) { this.sessionReference = sessionReference; }

    public Booking getBooking() { return booking; }
    public void setBooking(Booking booking) { this.booking = booking; }

    public Connector getConnector() { return connector; }
    public void setConnector(Connector connector) { this.connector = connector; }

    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }

    public LocalDateTime getActualStartTime() { return actualStartTime; }
    public void setActualStartTime(LocalDateTime actualStartTime) { this.actualStartTime = actualStartTime; }

    public LocalDateTime getActualEndTime() { return actualEndTime; }
    public void setActualEndTime(LocalDateTime actualEndTime) { this.actualEndTime = actualEndTime; }

    public Double getMeteredKwh() { return meteredKwh; }
    public void setMeteredKwh(Double meteredKwh) { this.meteredKwh = meteredKwh; }

    public Double getCurrentPowerKw() { return currentPowerKw; }
    public void setCurrentPowerKw(Double currentPowerKw) { this.currentPowerKw = currentPowerKw; }

    public Integer getCurrentSoC() { return currentSoC; }
    public void setCurrentSoC(Integer currentSoC) { this.currentSoC = currentSoC; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getBilledAmount() { return billedAmount; }
    public void setBilledAmount(Double billedAmount) { this.billedAmount = billedAmount; }

    public String getSessionNotes() { return sessionNotes; }
    public void setSessionNotes(String sessionNotes) { this.sessionNotes = sessionNotes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
