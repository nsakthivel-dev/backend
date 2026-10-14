package com.evplatform.repository;

import com.evplatform.domain.ChargingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChargingSessionRepository extends JpaRepository<ChargingSession, Long> {

    Optional<ChargingSession> findBySessionReference(String sessionReference);

    Optional<ChargingSession> findByBookingId(Long bookingId);

    List<ChargingSession> findByStatus(String status);

    @Query("SELECT s FROM ChargingSession s WHERE s.status = 'ACTIVE' " +
           "AND s.connector.charger.station.shop.transformer.id = :transformerId")
    List<ChargingSession> findActiveSessionsByTransformerId(@Param("transformerId") Long transformerId);

    @Query("SELECT s FROM ChargingSession s WHERE s.status = 'ACTIVE' " +
           "AND s.connector.charger.station.shop.id = :shopId")
    List<ChargingSession> findActiveSessionsByShopId(@Param("shopId") Long shopId);

    @Query("SELECT s FROM ChargingSession s WHERE s.connector.charger.station.shop.id = :shopId ORDER BY s.actualStartTime DESC")
    List<ChargingSession> findByShopId(@Param("shopId") Long shopId);

    @Query("SELECT s FROM ChargingSession s WHERE s.booking.customer.id = :customerId ORDER BY s.actualStartTime DESC")
    List<ChargingSession> findByCustomerId(@Param("customerId") Long customerId);
}
