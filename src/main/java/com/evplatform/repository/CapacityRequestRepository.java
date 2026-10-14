package com.evplatform.repository;

import com.evplatform.domain.CapacityRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CapacityRequestRepository extends JpaRepository<CapacityRequest, Long> {
    List<CapacityRequest> findByTransformerIdOrderByRequestedAtDesc(Long transformerId);
    List<CapacityRequest> findByShopIdOrderByRequestedAtDesc(Long shopId);
    List<CapacityRequest> findByStatus(String status);
}
