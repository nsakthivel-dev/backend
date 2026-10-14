package com.evplatform.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "stations")
public class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "pricing_floor_per_kwh", nullable = false)
    private Double pricingFloorPerKwh = 0.20;

    @Column(name = "pricing_cap_per_kwh", nullable = false)
    private Double pricingCapPerKwh = 0.65;

    @Column(name = "base_tariff_per_kwh", nullable = false)
    private Double baseTariffPerKwh = 0.35;

    @Column(name = "turnaround_buffer_minutes", nullable = false)
    private Integer turnaroundBufferMinutes = 10; // Mandatory turnaround buffer

    @Column(name = "status", nullable = false, length = 30)
    private String status = "ONLINE"; // ONLINE, OFFLINE, MAINTENANCE

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Station() {}

    public Station(Shop shop, String name, String address, Double latitude, Double longitude, Double pricingFloorPerKwh, Double pricingCapPerKwh, Double baseTariffPerKwh, Integer turnaroundBufferMinutes) {
        this.shop = shop;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.pricingFloorPerKwh = pricingFloorPerKwh != null ? pricingFloorPerKwh : 0.20;
        this.pricingCapPerKwh = pricingCapPerKwh != null ? pricingCapPerKwh : 0.65;
        this.baseTariffPerKwh = baseTariffPerKwh != null ? baseTariffPerKwh : 0.35;
        this.turnaroundBufferMinutes = turnaroundBufferMinutes != null ? turnaroundBufferMinutes : 10;
        this.status = "ONLINE";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Shop getShop() { return shop; }
    public void setShop(Shop shop) { this.shop = shop; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getPricingFloorPerKwh() { return pricingFloorPerKwh; }
    public void setPricingFloorPerKwh(Double pricingFloorPerKwh) { this.pricingFloorPerKwh = pricingFloorPerKwh; }

    public Double getPricingCapPerKwh() { return pricingCapPerKwh; }
    public void setPricingCapPerKwh(Double pricingCapPerKwh) { this.pricingCapPerKwh = pricingCapPerKwh; }

    public Double getBaseTariffPerKwh() { return baseTariffPerKwh; }
    public void setBaseTariffPerKwh(Double baseTariffPerKwh) { this.baseTariffPerKwh = baseTariffPerKwh; }

    public Integer getTurnaroundBufferMinutes() { return turnaroundBufferMinutes; }
    public void setTurnaroundBufferMinutes(Integer turnaroundBufferMinutes) { this.turnaroundBufferMinutes = turnaroundBufferMinutes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
