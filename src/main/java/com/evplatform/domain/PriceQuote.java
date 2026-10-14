package com.evplatform.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "price_quotes", indexes = {
    @Index(name = "idx_quote_token", columnList = "quote_token")
})
public class PriceQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quote_token", nullable = false, unique = true, length = 64)
    private String quoteToken;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(name = "slot_start", nullable = false)
    private LocalDateTime slotStart;

    @Column(name = "slot_end", nullable = false)
    private LocalDateTime slotEnd;

    @Column(name = "base_tariff", nullable = false)
    private Double baseTariff;

    @Column(name = "congestion_index", nullable = false)
    private Double congestionIndex; // 0.0 to 1.0+

    @Column(name = "calculated_price_per_kwh", nullable = false)
    private Double calculatedPricePerKwh; // Clamped within floor and cap

    @Column(name = "floor_price", nullable = false)
    private Double floorPrice;

    @Column(name = "cap_price", nullable = false)
    private Double capPrice;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false, length = 30)
    private String status = "ACTIVE"; // ACTIVE, ACCEPTED, EXPIRED

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public PriceQuote() {}

    public PriceQuote(String quoteToken, Station station, LocalDateTime slotStart, LocalDateTime slotEnd, Double baseTariff, Double congestionIndex, Double calculatedPricePerKwh, Double floorPrice, Double capPrice, LocalDateTime expiresAt) {
        this.quoteToken = quoteToken;
        this.station = station;
        this.slotStart = slotStart;
        this.slotEnd = slotEnd;
        this.baseTariff = baseTariff;
        this.congestionIndex = congestionIndex;
        this.calculatedPricePerKwh = calculatedPricePerKwh;
        this.floorPrice = floorPrice;
        this.capPrice = capPrice;
        this.expiresAt = expiresAt;
        this.status = "ACTIVE";
        this.createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getQuoteToken() { return quoteToken; }
    public void setQuoteToken(String quoteToken) { this.quoteToken = quoteToken; }

    public Station getStation() { return station; }
    public void setStation(Station station) { this.station = station; }

    public LocalDateTime getSlotStart() { return slotStart; }
    public void setSlotStart(LocalDateTime slotStart) { this.slotStart = slotStart; }

    public LocalDateTime getSlotEnd() { return slotEnd; }
    public void setSlotEnd(LocalDateTime slotEnd) { this.slotEnd = slotEnd; }

    public Double getBaseTariff() { return baseTariff; }
    public void setBaseTariff(Double baseTariff) { this.baseTariff = baseTariff; }

    public Double getCongestionIndex() { return congestionIndex; }
    public void setCongestionIndex(Double congestionIndex) { this.congestionIndex = congestionIndex; }

    public Double getCalculatedPricePerKwh() { return calculatedPricePerKwh; }
    public void setCalculatedPricePerKwh(Double calculatedPricePerKwh) { this.calculatedPricePerKwh = calculatedPricePerKwh; }

    public Double getFloorPrice() { return floorPrice; }
    public void setFloorPrice(Double floorPrice) { this.floorPrice = floorPrice; }

    public Double getCapPrice() { return capPrice; }
    public void setCapPrice(Double capPrice) { this.capPrice = capPrice; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
