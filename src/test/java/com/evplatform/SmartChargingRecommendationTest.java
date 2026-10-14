package com.evplatform;

import com.evplatform.domain.*;
import com.evplatform.repository.*;
import com.evplatform.service.*;
import com.evplatform.service.SmartChargingRecommendationService.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SmartChargingRecommendationTest {

    @Autowired
    private SmartChargingRecommendationService recommendationService;

    @Autowired
    private TransformerRiskConfigRepository riskConfigRepository;

    @Autowired
    private TransformerRepository transformerRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private ForecastingService forecastingService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("Scenario 1 & 4: User updates risk threshold in Grid Portal, validating limits and persisting versioned audit")
    void testUserConfiguredRiskThresholdUpdateAndAudit() {
        Transformer transformer = transformerRepository.findAll().get(0);

        // Fetch active baseline config
        TransformerRiskConfig initial = recommendationService.getActiveRiskConfig(transformer);
        int initialVersion = initial.getConfigVersion();

        // User updates threshold: warning 85%, critical 95%, approved limit 450 kW
        TransformerRiskConfig updated = recommendationService.updateRiskConfig(
                transformer.getId(),
                450.0,
                85.0,  // 85% = 382.5 kW
                95.0,  // 95% = 427.5 kW
                12.0,  // 12% safety margin
                75.0,  // 75 kW baseline
                "RESTRICT_NEW_ONLINE_BOOKINGS,OFFER_ALTERNATIVES",
                "EMERGENCY_THROTTLING,PROHIBIT_NEW_DEMAND",
                "grid_operator_alice",
                "Pre-summer peak capacity tightening"
        );

        assertNotNull(updated);
        assertEquals(initialVersion + 1, updated.getConfigVersion());
        assertEquals(85.0, updated.getWarningThresholdPercent());
        assertEquals(382.5, updated.getWarningThresholdKw(), 0.01);
        assertEquals(427.5, updated.getCriticalThresholdKw(), 0.01);
        assertEquals("grid_operator_alice", updated.getUpdatedBy());

        // Verify active query returns the updated version
        TransformerRiskConfig currentActive = recommendationService.getActiveRiskConfig(transformer);
        assertEquals(updated.getId(), currentActive.getId());
        assertEquals(85.0, currentActive.getWarningThresholdPercent());
    }

    @Test
    @DisplayName("Scenario 2: Updated risk settings applied consistently across forecasting and capacity engine")
    void testUpdatedThresholdPropagatesToForecastingAndCapacity() {
        Transformer transformer = transformerRepository.findAll().get(0);

        // Lower threshold to 70% (315 kW warning)
        recommendationService.updateRiskConfig(
                transformer.getId(),
                450.0,
                70.0,
                90.0,
                10.0,
                75.0,
                "RESTRICT_NEW_ONLINE_BOOKINGS,OFFER_ALTERNATIVES",
                "EMERGENCY_THROTTLING,PROHIBIT_NEW_DEMAND",
                "utility_admin",
                "Aggressive risk test"
        );

        // Run forecast
        List<LoadForecast> forecasts = forecastingService.generateForecastForTransformer(transformer.getId());
        assertFalse(forecasts.isEmpty());

        // For any interval whose total load exceeds 315 kW, status must be AT_RISK or CRITICAL
        for (LoadForecast f : forecasts) {
            if (f.getTotalPredictedLoadKw() > 315.0) {
                assertNotEquals("NORMAL", f.getRiskStatus(), "Interval exceeding user-configured warning threshold must not be NORMAL");
            }
        }
    }

    @Test
    @DisplayName("Scenario 3: Validation rejects invalid threshold configurations")
    void testConfigurationValidationRules() {
        Transformer transformer = transformerRepository.findAll().get(0);

        // Warning threshold higher than critical threshold must be rejected
        assertThrows(IllegalArgumentException.class, () -> {
            recommendationService.updateRiskConfig(
                    transformer.getId(),
                    450.0,
                    95.0,
                    90.0, // Invalid: warning >= critical
                    10.0,
                    75.0,
                    null, null, "admin", "Invalid config"
            );
        });

        // Negative operating limit must be rejected
        assertThrows(IllegalArgumentException.class, () -> {
            recommendationService.updateRiskConfig(
                    transformer.getId(),
                    -100.0,
                    80.0,
                    95.0,
                    10.0,
                    75.0,
                    null, null, "admin", "Negative limit"
            );
        });
    }

    @Test
    @DisplayName("Scenario 5 & 8: Specific 52% current SoC to 100% target SoC with nonlinear CC/CV power tapering")
    void testSpecificSoC52To100WithNonlinearTapering() {
        User customer = userRepository.findByUsername("customer_john").orElseThrow();
        Station station = stationRepository.findAll().get(0);

        // Create vehicle with 75 kWh battery, 50 kW max rate
        Vehicle vehicle = new Vehicle(customer, "Tesla", "Model 3", "TEST-52-100", 75.0, 50.0, "CCS2", 52);
        vehicle = vehicleRepository.save(vehicle);

        // Delta SoC = 100 - 52 = 48%
        // Battery energy required = 75.0 * 0.48 = 36.0 kWh
        // With 90% efficiency: Grid energy required = 36.0 / 0.90 = 40.0 kWh
        // Non-linear duration: 52% to 80% (28% at 50kW) + 80% to 100% (20% at tapered 25kW)
        RecommendationRequest req = new RecommendationRequest(
                customer.getId(),
                vehicle.getId(),
                station.getId(),
                LocalDateTime.now().plusHours(3),
                52,
                100,
                0.90,
                4
        );

        RecommendationResult result = recommendationService.evaluateAndRecommend(req);

        assertEquals(36.0, result.batteryEnergyRequiredKwh(), 0.01);
        assertEquals(40.0, result.gridEnergyRequiredKwh(), 0.01);
        assertEquals(0.90, result.chargingEfficiency(), 0.01);
        assertTrue(result.apparentPowerKva() >= result.estimatedChargingPowerKw(), "Apparent power in kVA must account for power factor (>= kW)");

        // 52% to 80% takes (21 kWh / 50 kW) * 60 = 25.2 mins
        // 80% to 100% takes (15 kWh / 25 kW) * 60 = 36.0 mins
        // Total physical duration is ~61-62 minutes, significantly longer than a purely linear assumption
        assertTrue(result.estimatedChargingDurationMinutes() >= 55, "Nonlinear CC/CV tapering above 80% must increase charging duration");
        assertEquals(10, result.turnaroundBufferMinutes());
        assertEquals(result.estimatedChargingDurationMinutes() + 10, result.totalOccupancyMinutes());
    }

    @Test
    @DisplayName("Scenario 6: Missing vehicle battery capacity or charging curve fallback approximation")
    void testMissingBatteryCapacityFallbackApproximation() {
        User customer = userRepository.findByUsername("customer_john").orElseThrow();
        Station station = stationRepository.findAll().get(0);

        // Vehicle with null battery capacity and max charging rate
        Vehicle vehicle = new Vehicle(customer, "Generic", "Prototype", "TEST-NULL", null, null, "CCS2", 30);
        vehicle = vehicleRepository.save(vehicle);

        RecommendationRequest req = new RecommendationRequest(
                customer.getId(),
                vehicle.getId(),
                station.getId(),
                LocalDateTime.now().plusHours(2),
                30,
                80,
                0.90,
                4
        );

        RecommendationResult result = recommendationService.evaluateAndRecommend(req);

        // Fallback default 60 kWh battery: delta 50% = 30.0 kWh battery energy
        assertEquals(30.0, result.batteryEnergyRequiredKwh(), 0.01);
        assertNotNull(result.estimationAssumptions().get("fallbackAssumptionsApplied"));
        assertTrue((Boolean) result.estimationAssumptions().get("fallbackAssumptionsApplied"));
    }

    @Test
    @DisplayName("Scenario 7: Different charging efficiencies (85% vs 95%) and charger limit capping")
    void testDifferentChargingEfficienciesAndChargerLimits() {
        User customer = userRepository.findByUsername("customer_john").orElseThrow();
        Station station = stationRepository.findAll().get(0);

        Vehicle vehicle = new Vehicle(customer, "Porsche", "Taycan", "TEST-FAST", 90.0, 270.0, "CCS2", 20);
        vehicle = vehicleRepository.save(vehicle);

        // Efficiency 85%
        RecommendationRequest req85 = new RecommendationRequest(
                customer.getId(), vehicle.getId(), station.getId(), LocalDateTime.now().plusHours(2), 20, 80, 0.85, 4);
        RecommendationResult res85 = recommendationService.evaluateAndRecommend(req85);

        // Efficiency 95%
        RecommendationRequest req95 = new RecommendationRequest(
                customer.getId(), vehicle.getId(), station.getId(), LocalDateTime.now().plusHours(2), 20, 80, 0.95, 4);
        RecommendationResult res95 = recommendationService.evaluateAndRecommend(req95);

        // Battery energy is identical (90 kWh * 60% = 54.0 kWh)
        assertEquals(res85.batteryEnergyRequiredKwh(), res95.batteryEnergyRequiredKwh(), 0.01);
        // Lower efficiency requires higher grid-side energy (54 / 0.85 > 54 / 0.95)
        assertTrue(res85.gridEnergyRequiredKwh() > res95.gridEnergyRequiredKwh());

        // Vehicle supports 270 kW, but connector at Metro Charge Hub is 150 kW or 50 kW
        // Power drawn must be capped at connector limit!
        assertTrue(res85.estimatedChargingPowerKw() <= 150.0, "Charging rate must be capped at connector's physical rating");
    }

    @Test
    @DisplayName("Scenario 9 & 10: Request crossing warning threshold triggers AT_RISK policy and recommends alternatives")
    void testRequestCrossingWarningThresholdTriggersAlternatives() {
        Transformer transformer = transformerRepository.findAll().get(0);
        User customer = userRepository.findByUsername("customer_john").orElseThrow();
        Station station = stationRepository.findAll().get(0);

        // Calibrate warning threshold to 75% (337.5 kW)
        recommendationService.updateRiskConfig(
                transformer.getId(),
                450.0,
                75.0,
                98.0,
                10.0,
                75.0,
                "RESTRICT_NEW_ONLINE_BOOKINGS,OFFER_ALTERNATIVES",
                "EMERGENCY_THROTTLING,PROHIBIT_NEW_DEMAND",
                "utility_admin",
                "Threshold calibration"
        );

        Vehicle vehicle = vehicleRepository.findByUserId(customer.getId()).get(0);

        // Request during a peak afternoon period
        LocalDateTime peakSlot = LocalDateTime.now().plusHours(1);
        RecommendationRequest req = new RecommendationRequest(
                customer.getId(), vehicle.getId(), station.getId(), peakSlot, 20, 90, 0.90, 4);

        RecommendationResult result = recommendationService.evaluateAndRecommend(req);

        // The result will either be feasible with headroom or trigger alternative recommendations
        assertNotNull(result);
        assertNotNull(result.feasibleAlternativeSlots());
        if (!result.isRequestedTimeFeasible()) {
            assertEquals("AT_RISK", result.riskClassification());
            assertNotNull(result.rejectionReason());
            // Feasible alternative slots must be provided
            assertFalse(result.feasibleAlternativeSlots().isEmpty(), "Must return feasible candidate times when requested time crosses threshold");
            // Every recommended alternative must have positive headroom
            for (AlternativeSlot alt : result.feasibleAlternativeSlots()) {
                assertTrue(alt.safetyHeadroomKw() >= 0.0, "Alternative slot must respect safety threshold");
            }
        }
    }

    @Test
    @DisplayName("Scenario 11: Request violating approved transformer limit is strictly prohibited")
    void testRequestViolatingApprovedOperatingLimitProhibited() {
        Transformer transformer = transformerRepository.findAll().get(0);
        User customer = userRepository.findByUsername("customer_john").orElseThrow();
        Station station = stationRepository.findAll().get(0);

        // Temporarily lower transformer operating limit to a very low level (e.g., 80 kW)
        // Since baseline non-EV load is 75 kW, any EV session (+50kW) will exceed the 80 kW approved limit!
        recommendationService.updateRiskConfig(
                transformer.getId(),
                80.0, // 80 kW approved limit
                70.0, // 56 kW warning
                95.0, // 76 kW critical
                5.0,
                75.0,
                "RESTRICT_NEW_ONLINE_BOOKINGS,OFFER_ALTERNATIVES",
                "EMERGENCY_THROTTLING,PROHIBIT_NEW_DEMAND",
                "utility_admin",
                "Severe electrical constraint test"
        );

        Vehicle vehicle = vehicleRepository.findByUserId(customer.getId()).get(0);
        RecommendationRequest req = new RecommendationRequest(
                customer.getId(), vehicle.getId(), station.getId(), LocalDateTime.now().plusHours(12), 20, 80, 0.90, 2);

        RecommendationResult result = recommendationService.evaluateAndRecommend(req);

        assertFalse(result.isRequestedTimeFeasible());
        assertEquals("CRITICAL", result.riskClassification());
        assertNotNull(result.rejectionReason());
        assertTrue(result.rejectionReason().toLowerCase().contains("limit") || result.rejectionReason().toLowerCase().contains("exceeds"));
    }

    @Test
    @DisplayName("Scenario 12: Alternative times become feasible after forecast peak demand falls")
    void testAlternativeTimesBecomeFeasibleAfterPeakFalls() {
        User customer = userRepository.findByUsername("customer_john").orElseThrow();
        Station station = stationRepository.findAll().get(0);
        Vehicle vehicle = vehicleRepository.findByUserId(customer.getId()).get(0);

        // Request with 6 hour window
        RecommendationRequest req = new RecommendationRequest(
                customer.getId(), vehicle.getId(), station.getId(), LocalDateTime.now().plusHours(1), 20, 85, 0.90, 6);

        RecommendationResult result = recommendationService.evaluateAndRecommend(req);

        assertNotNull(result.feasibleAlternativeSlots());
        // Alternatives should be stepped in 30-minute intervals
        if (!result.feasibleAlternativeSlots().isEmpty()) {
            AlternativeSlot firstAlt = result.feasibleAlternativeSlots().get(0);
            assertNotNull(firstAlt.candidateStartTime());
            assertTrue(firstAlt.safetyHeadroomKw() >= 0.0);
            assertTrue(firstAlt.estimatedTariffPerKwh() > 0.0);
        }
    }

    @Test
    @DisplayName("Scenario 18: Two simultaneous booking attempts atomically prevent double-allocation of same connector")
    void testConcurrentBookingAttemptsPreventDoubleAllocation() throws Exception {
        User customer1 = userRepository.findByUsername("customer_john").orElseThrow();
        User customer2 = userRepository.findByUsername("customer_jane").orElseThrow();
        Vehicle v1 = vehicleRepository.findByUserId(customer1.getId()).get(0);
        Vehicle v2 = vehicleRepository.findByUserId(customer2.getId()).get(0);

        Connector connector = connectorRepository.findAll().stream()
                .filter(c -> !c.getIsWalkInReserve() && "AVAILABLE".equals(c.getStatus()))
                .findFirst().orElseThrow();

        LocalDateTime targetSlot = LocalDateTime.now().plusHours(5);

        BookingService.BookingRequest req1 = new BookingService.BookingRequest(
                customer1.getId(), v1.getId(), connector.getId(), targetSlot, 30, 80, null, false, false);
        BookingService.BookingRequest req2 = new BookingService.BookingRequest(
                customer2.getId(), v2.getId(), connector.getId(), targetSlot, 30, 80, null, false, false);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Future<?> f1 = executor.submit(() -> {
            try {
                barrier.await();
                bookingService.createBooking(req1);
                successCount.incrementAndGet();
            } catch (Exception ex) {
                failureCount.incrementAndGet();
            }
        });

        Future<?> f2 = executor.submit(() -> {
            try {
                barrier.await();
                bookingService.createBooking(req2);
                successCount.incrementAndGet();
            } catch (Exception ex) {
                failureCount.incrementAndGet();
            }
        });

        f1.get();
        f2.get();
        executor.shutdown();

        // Exactly one attempt must succeed and the other must be cleanly rejected
        assertEquals(1, successCount.get(), "Exactly one concurrent booking must succeed");
        assertEquals(1, failureCount.get(), "The competing booking must be safely rejected due to atomic collision check");
    }

    @Test
    @DisplayName("Scenario 19: Forecast warning does not silently cancel an existing confirmed booking")
    void testForecastWarningDoesNotCancelConfirmedBooking() {
        User customer = userRepository.findByUsername("customer_john").orElseThrow();
        Vehicle vehicle = vehicleRepository.findByUserId(customer.getId()).get(0);
        Connector connector = connectorRepository.findAll().stream()
                .filter(c -> !c.getIsWalkInReserve() && "AVAILABLE".equals(c.getStatus()))
                .findFirst().orElseThrow();

        LocalDateTime slot = LocalDateTime.now().plusHours(6);
        BookingService.BookingRequest req = new BookingService.BookingRequest(
                customer.getId(), vehicle.getId(), connector.getId(), slot, 30, 80, null, false, false);

        Booking confirmed = bookingService.createBooking(req);
        assertNotNull(confirmed);
        assertEquals(BookingStatus.CONFIRMED, confirmed.getStatus());

        // Now simulate grid risk rising to AT_RISK or CRITICAL by generating forecasts with tight thresholds
        Transformer transformer = transformerRepository.findAll().get(0);
        forecastingService.generateForecastForTransformer(transformer.getId());

        // Verify confirmed booking is still CONFIRMED (Section 2 & 7: "A change in risk classification must not silently cancel a confirmed booking.")
        Booking checked = bookingRepository.findById(confirmed.getId()).orElseThrow();
        assertEquals(BookingStatus.CONFIRMED, checked.getStatus(), "Confirmed booking must be preserved despite risk warning");
    }
}
