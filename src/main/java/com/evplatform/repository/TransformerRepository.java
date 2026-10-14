package com.evplatform.repository;

import com.evplatform.domain.Transformer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransformerRepository extends JpaRepository<Transformer, Long> {
    Optional<Transformer> findByCode(String code);
}
