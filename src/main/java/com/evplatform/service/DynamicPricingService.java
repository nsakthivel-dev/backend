package com.evplatform.service;

import com.evplatform.domain.PriceQuote;
import com.evplatform.domain.Station;
import com.evplatform.domain.Transformer;
import com.evplatform.repository.BookingRepository;
import com.evplatform.repository.PriceQuoteRepository;
import com.evplatform.repository.StationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

@Service
public class DynamicPricingService {

    private final StationRepository stationRepository;
    private final PriceQuoteRepository priceQuoteRepository;
    private final BookingRepository bookingRepository;
    private final AlgorithmEngineService algorithmEngineService;

    public DynamicPricingService(
            StationRepository stationRepository,
            PriceQuoteRepository priceQuoteRepository,
            BookingRepository bookingRepository,
            AlgorithmEngineService algorithmEngineService) {
        this.stationRepository = stationRepository;
        this.priceQuoteRepository = priceQuoteRepository;
        this.bookingRepository = bookingRepository;
        this.algorithmEngineService = algorithmEngineService;
    }

    /**
     * Algorithm 4: Generate a transparent dynamic price quote without bidding/auctions.
     */
    @Transactional
    public PriceQuote generateQuote(Long stationId, LocalDateTime slotStart, LocalDateTime slotEnd) {
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new IllegalArgumentException("Station not found: " + stationId));

        Transformer transformer = station.getShop().getTransformer();
        double usableBudget = algorithmEngineService.calculateUsableEvBudget(transformer);

        // Calculate expected load in that slot
        var overlapping = bookingRepository.findTransformerOverlappingBookings(
                transformer.getId(), slotStart, slotEnd,
                Arrays.asList(com.evplatform.domain.BookingStatus.CONFIRMED, com.evplatform.domain.BookingStatus.IN_PROGRESS));
        double expectedLoad = overlapping.stream().mapToDouble(b -> b.getAllocatedPowerKw()).sum();

        var result = algorithmEngineService.calculateDynamicPrice(
                station.getBaseTariffPerKwh(),
                station.getPricingFloorPerKwh(),
                station.getPricingCapPerKwh(),
                expectedLoad,
                usableBudget
        );

        String token = "PQ-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);

        PriceQuote quote = new PriceQuote(
                token,
                station,
                slotStart,
                slotEnd,
                result.baseTariff(),
                result.congestionIndex(),
                result.calculatedPricePerKwh(),
                result.floorPrice(),
                result.capPrice(),
                expiresAt
        );

        return priceQuoteRepository.save(quote);
    }

    public PriceQuote validateQuote(String quoteToken) {
        PriceQuote quote = priceQuoteRepository.findByQuoteToken(quoteToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid price quote token: " + quoteToken));

        if (quote.isExpired() || !"ACTIVE".equals(quote.getStatus())) {
            throw new IllegalStateException("Price quote has expired. Please refresh to get an updated tariff quote.");
        }

        return quote;
    }
}
