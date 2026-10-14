package com.evplatform.repository;

import com.evplatform.domain.TransformerRiskConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransformerRiskConfigRepository extends JpaRepository<TransformerRiskConfig, Long> {

    @Query("SELECT c FROM TransformerRiskConfig c WHERE c.transformer.id = :transformerId AND c.isActive = true ORDER BY c.configVersion DESC LIMIT 1")
    Optional<TransformerRiskConfig> findActiveByTransformerId(@Param("transformerId") Long transformerId);

    List<TransformerRiskConfig> findByTransformerIdOrderByConfigVersionDesc(Long transformerId);
}
