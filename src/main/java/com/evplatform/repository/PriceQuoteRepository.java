package com.evplatform.repository;

import com.evplatform.domain.PriceQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PriceQuoteRepository extends JpaRepository<PriceQuote, Long> {
    Optional<PriceQuote> findByQuoteToken(String quoteToken);
}
