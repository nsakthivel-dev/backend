package com.evplatform;

import com.evplatform.domain.*;
import com.evplatform.repository.*;
import com.evplatform.service.BookingService;
import com.evplatform.service.ChargingSessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BookingServiceIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private ChargingSessionService sessionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private TransformerRepository transformerRepository;

    @Test
    @DisplayName("Test 1 & 4: Prevent overlapping booking on same connector")
    void testOverlappingBookingPrevention() {
        User customer1 = userRepository.findByUsername("customer_john").orElseThrow();
        User customer2 = userRepository.findByUsername("customer_jane").orElseThrow();
        Vehicle v1 = vehicleRepository.findByUserId(customer1.getId()).get(0);
        Vehicle v2 = vehicleRepository.findByUserId(customer2.getId()).get(0);

        // Find an online available connector
        Connector connector = connectorRepository.findAll().stream()
                .filter(c -> !c.getIsWalkInReserve() && "AVAILABLE".equals(c.getStatus()) && "CCS2".equals(c.getConnectorType()))
                .findFirst().orElseThrow();

        LocalDateTime slotStart = LocalDateTime.now().plusHours(2);

        // First booking succeeds
        BookingService.BookingRequest req1 = new BookingService.BookingRequest(
                customer1.getId(), v1.getId(), connector.getId(), slotStart, 30, 80, null, false, false);
        Booking booking1 = bookingService.createBooking(req1);
        assertNotNull(booking1);
        assertEquals(BookingStatus.CONFIRMED, booking1.getStatus());

        // Confirmed price preservation: verify price is saved
        assertTrue(booking1.getBookedPricePerKwh() > 0.0);

        // Overlapping booking for same connector within the occupancy window must fail with IllegalStateException!
        BookingService.BookingRequest req2 = new BookingService.BookingRequest(
                customer2.getId(), v1.getId(), connector.getId(), slotStart.plusMinutes(15), 30, 80, null, false, false);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            bookingService.createBooking(req2);
        });
        assertTrue(ex.getMessage().contains("already booked or occupied"));
    }

    @Test
    @DisplayName("Test 3: Connector compatibility check rejects incompatible plugs")
    void testConnectorCompatibility() {
        User customer2 = userRepository.findByUsername("customer_jane").orElseThrow();
        // v3 is Nissan Leaf with CHAdeMO
        Vehicle leaf = vehicleRepository.findByUserId(customer2.getId()).stream()
                .filter(v -> "CHAdeMO".equals(v.getConnectorType()))
                .findFirst().orElseThrow();

        // Find a CCS2 connector
        Connector ccs2Connector = connectorRepository.findAll().stream()
                .filter(c -> "CCS2".equals(c.getConnectorType()))
                .findFirst().orElseThrow();

        BookingService.BookingRequest req = new BookingService.BookingRequest(
                customer2.getId(), leaf.getId(), ccs2Connector.getId(), LocalDateTime.now().plusHours(3), 30, 80, null, false, false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            bookingService.createBooking(req);
        });
        assertTrue(ex.getMessage().contains("Connector type mismatch"));
    }

    @Test
    @DisplayName("Test 11: Protect walk-in reserves from ordinary online bookings")
    void testWalkInReserveProtection() {
        User customer = userRepository.findByUsername("customer_john").orElseThrow();
        Vehicle v = vehicleRepository.findByUserId(customer.getId()).get(0);

        // Find a walk-in reserve connector
        Connector walkInConn = connectorRepository.findAll().stream()
                .filter(Connector::getIsWalkInReserve)
                .findFirst().orElseThrow();

        LocalDateTime slotStart = LocalDateTime.now().plusHours(5);

        // Attempting to book a walk-in reserve via ordinary online booking must fail!
        BookingService.BookingRequest req = new BookingService.BookingRequest(
                customer.getId(), v.getId(), walkInConn.getId(), slotStart, 30, 80, null, false, false);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            bookingService.createBooking(req);
        });
        assertTrue(ex.getMessage().contains("walk-in reserve"));
    }

    @Test
    @DisplayName("Test 8: Capacity rejection when transformer limit is insufficient")
    void testTransformerCapacityRejection() {
        Transformer t = transformerRepository.findAll().get(0);
        double originalLimit = t.getApprovedLimitKw();

        try {
            // Artificially lower transformer limit so usable EV budget becomes 0
            t.setApprovedLimitKw(70.0); // 70 kW limit with 75 kW non-EV baseline => 0 kW EV budget!
            transformerRepository.save(t);

            User customer = userRepository.findByUsername("customer_john").orElseThrow();
            Vehicle v = vehicleRepository.findByUserId(customer.getId()).get(0);
            Connector connector = connectorRepository.findAll().stream()
                    .filter(c -> !c.getIsWalkInReserve() && "CCS2".equals(c.getConnectorType()))
                    .findFirst().orElseThrow();

            BookingService.BookingRequest req = new BookingService.BookingRequest(
                    customer.getId(), v.getId(), connector.getId(), LocalDateTime.now().plusHours(8), 30, 80, null, false, false);

            assertThrows(IllegalStateException.class, () -> {
                bookingService.createBooking(req);
            });
        } finally {
            // Restore transformer limit
            t.setApprovedLimitKw(originalLimit);
            transformerRepository.save(t);
        }
    }

    @Test
    @DisplayName("Test 5: Concurrency race condition prevention for simultaneous booking attempts")
    void testConcurrentSimultaneousBookingRaceCondition() throws InterruptedException {
        User customer1 = userRepository.findByUsername("customer_john").orElseThrow();
        User customer2 = userRepository.findByUsername("customer_jane").orElseThrow();
        Vehicle v1 = vehicleRepository.findByUserId(customer1.getId()).get(0);
        Vehicle v2 = vehicleRepository.findByUserId(customer2.getId()).get(0);

        // Choose a clean connector
        Connector targetConnector = connectorRepository.findAll().stream()
                .filter(c -> !c.getIsWalkInReserve() && "CCS2".equals(c.getConnectorType()) && c.getId() > 5)
                .findFirst().orElseThrow();

        LocalDateTime slotStart = LocalDateTime.now().plusHours(12);

        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Runnable task1 = () -> {
            try {
                readyLatch.countDown();
                startLatch.await();
                bookingService.createBooking(new BookingService.BookingRequest(
                        customer1.getId(), v1.getId(), targetConnector.getId(), slotStart, 30, 80, null, false, false));
                successCount.incrementAndGet();
            } catch (Exception ex) {
                failureCount.incrementAndGet();
            }
        };

        Runnable task2 = () -> {
            try {
                readyLatch.countDown();
                startLatch.await();
                bookingService.createBooking(new BookingService.BookingRequest(
                        customer2.getId(), v2.getId(), targetConnector.getId(), slotStart, 30, 80, null, false, false));
                successCount.incrementAndGet();
            } catch (Exception ex) {
                failureCount.incrementAndGet();
            }
        };

        executor.submit(task1);
        executor.submit(task2);

        readyLatch.await();
        startLatch.countDown(); // Release both threads at the exact same instant

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Exactly one must succeed, and one must fail!
        assertEquals(1, successCount.get(), "Exactly one simultaneous booking attempt must succeed");
        assertEquals(1, failureCount.get(), "Conflicting simultaneous booking attempt must fail");
    }

    @Test
    @DisplayName("Test 20: Correct session completion and capacity release")
    void testSessionCompletionAndCapacityRelease() {
        User customer = userRepository.findByUsername("customer_john").orElseThrow();
        Vehicle v = vehicleRepository.findByUserId(customer.getId()).get(0);
        Connector connector = connectorRepository.findAll().stream()
                .filter(c -> !c.getIsWalkInReserve() && "CCS2".equals(c.getConnectorType()) && c.getId() > 8)
                .findFirst().orElseThrow();

        // 1. Create booking
        Booking booking = bookingService.createBooking(new BookingService.BookingRequest(
                customer.getId(), v.getId(), connector.getId(), LocalDateTime.now().plusMinutes(5), 30, 80, null, false, false));
        assertNotNull(booking);

        // 2. Start session
        ChargingSession session = sessionService.startSession(booking.getId());
        assertEquals("ACTIVE", session.getStatus());
        assertEquals("OCCUPIED", connectorRepository.findById(connector.getId()).orElseThrow().getStatus());

        // 3. Complete session
        ChargingSession completed = sessionService.completeSession(session.getId(), "Normal finish");
        assertEquals("COMPLETED", completed.getStatus());
        assertEquals("AVAILABLE", connectorRepository.findById(connector.getId()).orElseThrow().getStatus());
        assertEquals(BookingStatus.COMPLETED, bookingRepository.findById(booking.getId()).orElseThrow().getStatus());
    }
}
