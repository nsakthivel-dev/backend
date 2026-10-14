package com.evplatform.repository;

import com.evplatform.domain.MeterReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MeterReadingRepository extends JpaRepository<MeterReading, Long> {

    List<MeterReading> findByTransformerIdOrderByReadingTimestampDesc(Long transformerId);

    @Query("SELECT m FROM MeterReading m WHERE m.transformer.id = :transformerId ORDER BY m.readingTimestamp DESC LIMIT 1")
    Optional<MeterReading> findLatestByTransformerId(@Param("transformerId") Long transformerId);

    @Query("SELECT m FROM MeterReading m WHERE m.transformer.id = :transformerId AND m.readingTimestamp >= :since ORDER BY m.readingTimestamp ASC")
    List<MeterReading> findHistorySince(@Param("transformerId") Long transformerId, @Param("since") LocalDateTime since);
}
