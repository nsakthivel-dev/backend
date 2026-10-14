package com.evplatform.repository;

import com.evplatform.domain.Booking;
import com.evplatform.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByBookingReference(String bookingReference);

    List<Booking> findByCustomerIdOrderByStartTimeDesc(Long customerId);

    @Query("SELECT b FROM Booking b WHERE b.connector.charger.station.shop.id = :shopId ORDER BY b.startTime DESC")
    List<Booking> findByShopId(@Param("shopId") Long shopId);

    @Query("SELECT b FROM Booking b WHERE b.connector.id = :connectorId " +
           "AND b.status IN :activeStatuses " +
           "AND b.startTime < :endTime AND b.endTime > :startTime")
    List<Booking> findOverlappingBookings(
            @Param("connectorId") Long connectorId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("activeStatuses") Collection<BookingStatus> activeStatuses);

    @Query("SELECT b FROM Booking b WHERE b.connector.charger.station.shop.transformer.id = :transformerId " +
           "AND b.status IN :activeStatuses " +
           "AND b.startTime < :endTime AND b.endTime > :startTime")
    List<Booking> findTransformerOverlappingBookings(
            @Param("transformerId") Long transformerId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("activeStatuses") Collection<BookingStatus> activeStatuses);

    @Query("SELECT b FROM Booking b WHERE b.connector.charger.station.shop.id = :shopId " +
           "AND b.status IN :activeStatuses " +
           "AND b.startTime < :endTime AND b.endTime > :startTime")
    List<Booking> findShopOverlappingBookings(
            @Param("shopId") Long shopId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("activeStatuses") Collection<BookingStatus> activeStatuses);

    List<Booking> findByStatusIn(Collection<BookingStatus> statuses);
}
