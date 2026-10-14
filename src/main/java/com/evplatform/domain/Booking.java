package com.evplatform.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings", indexes = {
    @Index(name = "idx_booking_connector_time", columnList = "connector_id, start_time, end_time"),
    @Index(name = "idx_booking_customer", columnList = "customer_id"),
    @Index(name = "idx_booking_status", columnList = "status")
})
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_reference", nullable = false, unique = true, length = 64)
    private String bookingReference;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "connector_id", nullable = false)
    private Connector connector;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime; // start_time + estimatedChargingDurationMinutes + turnaroundBufferMinutes

    @Column(name = "estimated_charging_duration_minutes", nullable = false)
    private Integer estimatedChargingDurationMinutes;

    @Column(name = "turnaround_buffer_minutes", nullable = false)
    private Integer turnaroundBufferMinutes = 10;

    @Column(name = "initial_soc", nullable = false)
    private Integer initialSoC;

    @Column(name = "target_soc", nullable = false)
    private Integer targetSoC;

    @Column(name = "booked_price_per_kwh", nullable = false)
    private Double bookedPricePerKwh; // Frozen price agreed upon booking

    @Column(name = "total_estimated_cost", nullable = false)
    private Double totalEstimatedCost;

    @Column(name = "allocated_power_kw", nullable = false)
    private Double allocatedPowerKw; // Charging rate reserved on transformer

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Column(name = "is_walk_in", nullable = false)
    private Boolean isWalkIn = false;

    @Column(name = "is_urgent", nullable = false)
    private Boolean isUrgent = false;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Booking() {}

    public Booking(String bookingReference, User customer, Vehicle vehicle, Connector connector, LocalDateTime startTime, LocalDateTime endTime, Integer estimatedChargingDurationMinutes, Integer turnaroundBufferMinutes, Integer initialSoC, Integer targetSoC, Double bookedPricePerKwh, Double totalEstimatedCost, Double allocatedPowerKw, Boolean isWalkIn, Boolean isUrgent) {
        this.bookingReference = bookingReference;
        this.customer = customer;
        this.vehicle = vehicle;
        this.connector = connector;
        this.startTime = startTime;
        this.endTime = endTime;
        this.estimatedChargingDurationMinutes = estimatedChargingDurationMinutes;
        this.turnaroundBufferMinutes = turnaroundBufferMinutes != null ? turnaroundBufferMinutes : 10;
        this.initialSoC = initialSoC;
        this.targetSoC = targetSoC;
        this.bookedPricePerKwh = bookedPricePerKwh;
        this.totalEstimatedCost = totalEstimatedCost;
        this.allocatedPowerKw = allocatedPowerKw;
        this.isWalkIn = isWalkIn != null ? isWalkIn : false;
        this.isUrgent = isUrgent != null ? isUrgent : false;
        this.status = BookingStatus.CONFIRMED;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBookingReference() { return bookingReference; }
    public void setBookingReference(String bookingReference) { this.bookingReference = bookingReference; }

    public User getCustomer() { return customer; }
    public void setCustomer(User customer) { this.customer = customer; }

    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }

    public Connector getConnector() { return connector; }
    public void setConnector(Connector connector) { this.connector = connector; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Integer getEstimatedChargingDurationMinutes() { return estimatedChargingDurationMinutes; }
    public void setEstimatedChargingDurationMinutes(Integer estimatedChargingDurationMinutes) { this.estimatedChargingDurationMinutes = estimatedChargingDurationMinutes; }

    public Integer getTurnaroundBufferMinutes() { return turnaroundBufferMinutes; }
    public void setTurnaroundBufferMinutes(Integer turnaroundBufferMinutes) { this.turnaroundBufferMinutes = turnaroundBufferMinutes; }

    public Integer getInitialSoC() { return initialSoC; }
    public void setInitialSoC(Integer initialSoC) { this.initialSoC = initialSoC; }

    public Integer getTargetSoC() { return targetSoC; }
    public void setTargetSoC(Integer targetSoC) { this.targetSoC = targetSoC; }

    public Double getBookedPricePerKwh() { return bookedPricePerKwh; }
    public void setBookedPricePerKwh(Double bookedPricePerKwh) { this.bookedPricePerKwh = bookedPricePerKwh; }

    public Double getTotalEstimatedCost() { return totalEstimatedCost; }
    public void setTotalEstimatedCost(Double totalEstimatedCost) { this.totalEstimatedCost = totalEstimatedCost; }

    public Double getAllocatedPowerKw() { return allocatedPowerKw; }
    public void setAllocatedPowerKw(Double allocatedPowerKw) { this.allocatedPowerKw = allocatedPowerKw; }

    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }

    public Boolean getIsWalkIn() { return isWalkIn; }
    public void setIsWalkIn(Boolean walkIn) { isWalkIn = walkIn; }

    public Boolean getIsUrgent() { return isUrgent; }
    public void setIsUrgent(Boolean urgent) { isUrgent = urgent; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
