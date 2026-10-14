package com.evplatform.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "capacity_allocations", indexes = {
    @Index(name = "idx_allocation_shop_interval", columnList = "shop_id, interval_start, interval_end"),
    @Index(name = "idx_allocation_transformer_interval", columnList = "transformer_id, interval_start")
})
public class CapacityAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "transformer_id", nullable = false)
    private Transformer transformer;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @Column(name = "interval_start", nullable = false)
    private LocalDateTime intervalStart;

    @Column(name = "interval_end", nullable = false)
    private LocalDateTime intervalEnd;

    @Column(name = "allocated_kw", nullable = false)
    private Double allocatedKw;

    @Column(name = "requested_kw", nullable = false)
    private Double requestedKw;

    @Column(name = "approved_by", length = 100)
    private String approvedBy = "SYSTEM_ALLOCATOR";

    @Column(nullable = false, length = 30)
    private String status = "APPROVED"; // APPROVED, PENDING, REJECTED, REALLOCATED

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public CapacityAllocation() {}

    public CapacityAllocation(Transformer transformer, Shop shop, LocalDateTime intervalStart, LocalDateTime intervalEnd, Double allocatedKw, Double requestedKw, String approvedBy, String status) {
        this.transformer = transformer;
        this.shop = shop;
        this.intervalStart = intervalStart;
        this.intervalEnd = intervalEnd;
        this.allocatedKw = allocatedKw;
        this.requestedKw = requestedKw;
        this.approvedBy = approvedBy != null ? approvedBy : "SYSTEM_ALLOCATOR";
        this.status = status != null ? status : "APPROVED";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Transformer getTransformer() { return transformer; }
    public void setTransformer(Transformer transformer) { this.transformer = transformer; }

    public Shop getShop() { return shop; }
    public void setShop(Shop shop) { this.shop = shop; }

    public LocalDateTime getIntervalStart() { return intervalStart; }
    public void setIntervalStart(LocalDateTime intervalStart) { this.intervalStart = intervalStart; }

    public LocalDateTime getIntervalEnd() { return intervalEnd; }
    public void setIntervalEnd(LocalDateTime intervalEnd) { this.intervalEnd = intervalEnd; }

    public Double getAllocatedKw() { return allocatedKw; }
    public void setAllocatedKw(Double allocatedKw) { this.allocatedKw = allocatedKw; }

    public Double getRequestedKw() { return requestedKw; }
    public void setRequestedKw(Double requestedKw) { this.requestedKw = requestedKw; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
