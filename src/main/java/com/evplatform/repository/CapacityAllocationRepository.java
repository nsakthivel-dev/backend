package com.evplatform.repository;

import com.evplatform.domain.CapacityAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CapacityAllocationRepository extends JpaRepository<CapacityAllocation, Long> {

    @Query("SELECT ca FROM CapacityAllocation ca WHERE ca.transformer.id = :transformerId " +
           "AND ca.intervalStart < :intervalEnd AND ca.intervalEnd > :intervalStart")
    List<CapacityAllocation> findByTransformerAndInterval(
            @Param("transformerId") Long transformerId,
            @Param("intervalStart") LocalDateTime intervalStart,
            @Param("intervalEnd") LocalDateTime intervalEnd);

    @Query("SELECT ca FROM CapacityAllocation ca WHERE ca.shop.id = :shopId " +
           "AND ca.intervalStart < :intervalEnd AND ca.intervalEnd > :intervalStart")
    List<CapacityAllocation> findByShopAndInterval(
            @Param("shopId") Long shopId,
            @Param("intervalStart") LocalDateTime intervalStart,
            @Param("intervalEnd") LocalDateTime intervalEnd);

    List<CapacityAllocation> findByTransformerIdOrderByIntervalStartAsc(Long transformerId);

    List<CapacityAllocation> findByShopIdOrderByIntervalStartAsc(Long shopId);
}
