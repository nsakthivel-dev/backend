package com.evplatform;

import com.evplatform.domain.Transformer;
import com.evplatform.service.AlgorithmEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlgorithmEngineTest {

    private AlgorithmEngineService algorithmEngineService;

    @BeforeEach
    void setUp() {
        algorithmEngineService = new AlgorithmEngineService();
    }

    @Test
    @DisplayName("Algorithm 1: Estimated duration and mandatory 10-minute occupancy buffer")
    void testAlgorithm1DurationAndBuffer() {
        // Example from prompt:
        // Configured average full-charge: 60 minutes
        // Current SoC: 30%, Target SoC: 80% (delta = 50%)
        // Expected charging duration: 60 * 50 / 100 = 30 minutes
        // Mandatory turnaround buffer: 10 minutes
        // Total connector occupancy: 40 minutes
        var estimate = algorithmEngineService.calculateOccupancy(30, 80, 60, 10);

        assertEquals(30, estimate.estimatedChargingDurationMinutes());
        assertEquals(10, estimate.turnaroundBufferMinutes());
        assertEquals(40, estimate.totalOccupancyMinutes());
        assertEquals(30, estimate.initialSoC());
        assertEquals(80, estimate.targetSoC());
    }

    @Test
    @DisplayName("Algorithm 3: Usable EV charging budget calculation")
    void testAlgorithm3UsableEvBudget() {
        // TR-Downtown: 500kVA, approved limit: 450 kW, safety margin: 10%, non-EV baseline: 75 kW
        // Safe operating capacity = 450 * (1 - 0.10) = 405 kW
        // Usable EV Budget = 405 - 75 = 330 kW
        Transformer transformer = new Transformer(
                "TR-TEST", "Test Transformer", "Substation", 500.0, 0.95, 450.0, 10.0, 75.0);

        double usableBudget = algorithmEngineService.calculateUsableEvBudget(transformer);
        assertEquals(330.0, usableBudget, 0.001);
    }

    @Test
    @DisplayName("Algorithm 4: Dynamic pricing clamps between floor and cap")
    void testAlgorithm4DynamicPricingBounds() {
        double floor = 0.20;
        double cap = 0.65;
        double baseTariff = 0.35;

        // Low congestion (0 kW load / 330 kW budget) -> should be base tariff or floor
        var lowPrice = algorithmEngineService.calculateDynamicPrice(baseTariff, floor, cap, 0.0, 330.0);
        assertTrue(lowPrice.calculatedPricePerKwh() >= floor);
        assertTrue(lowPrice.calculatedPricePerKwh() <= cap);

        // High congestion (500 kW load / 330 kW budget) -> should clamp to cap
        var highPrice = algorithmEngineService.calculateDynamicPrice(baseTariff, floor, cap, 500.0, 330.0);
        assertEquals(cap, highPrice.calculatedPricePerKwh(), 0.001);
        assertTrue(highPrice.congestionIndex() > 1.0);
    }
}
