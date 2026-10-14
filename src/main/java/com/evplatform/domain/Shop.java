package com.evplatform.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "shops")
public class Shop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 150)
    private String location;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "transformer_id", nullable = false)
    private Transformer transformer;

    @Column(name = "operating_hours", length = 50)
    private String operatingHours = "24/7";

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "allocated_capacity_kw", nullable = false)
    private Double allocatedCapacityKw = 150.0;

    @Column(nullable = false, length = 30)
    private String status = "ACTIVE"; // ACTIVE, INACTIVE, CURTAILED

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Shop() {}

    public Shop(String name, String location, Transformer transformer, String operatingHours, String contactPhone, Double allocatedCapacityKw) {
        this.name = name;
        this.location = location;
        this.transformer = transformer;
        this.operatingHours = operatingHours != null ? operatingHours : "24/7";
        this.contactPhone = contactPhone;
        this.allocatedCapacityKw = allocatedCapacityKw != null ? allocatedCapacityKw : 100.0;
        this.status = "ACTIVE";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Transformer getTransformer() { return transformer; }
    public void setTransformer(Transformer transformer) { this.transformer = transformer; }

    public String getOperatingHours() { return operatingHours; }
    public void setOperatingHours(String operatingHours) { this.operatingHours = operatingHours; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public Double getAllocatedCapacityKw() { return allocatedCapacityKw; }
    public void setAllocatedCapacityKw(Double allocatedCapacityKw) { this.allocatedCapacityKw = allocatedCapacityKw; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
