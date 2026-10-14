package com.evplatform.controller;

import com.evplatform.domain.*;
import com.evplatform.repository.*;
import com.evplatform.service.BookingService;
import com.evplatform.service.CapacityManagementService;
import com.evplatform.service.ChargingSessionService;
import com.evplatform.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/shop")
public class ShopController {

    private final ShopRepository shopRepository;
    private final StationRepository stationRepository;
    private final ChargerRepository chargerRepository;
    private final ConnectorRepository connectorRepository;
    private final BookingRepository bookingRepository;
    private final ChargingSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final BookingService bookingService;
    private final ChargingSessionService sessionService;
    private final CapacityManagementService capacityManagementService;
    private final NotificationService notificationService;

    public ShopController(
            ShopRepository shopRepository,
            StationRepository stationRepository,
            ChargerRepository chargerRepository,
            ConnectorRepository connectorRepository,
            BookingRepository bookingRepository,
            ChargingSessionRepository sessionRepository,
            UserRepository userRepository,
            VehicleRepository vehicleRepository,
            BookingService bookingService,
            ChargingSessionService sessionService,
            CapacityManagementService capacityManagementService,
            NotificationService notificationService) {
        this.shopRepository = shopRepository;
        this.stationRepository = stationRepository;
        this.chargerRepository = chargerRepository;
        this.connectorRepository = connectorRepository;
        this.bookingRepository = bookingRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.bookingService = bookingService;
        this.sessionService = sessionService;
        this.capacityManagementService = capacityManagementService;
        this.notificationService = notificationService;
    }

    // --- DASHBOARD OVERVIEW ---
    @GetMapping("/dashboard")
    public ResponseEntity<?> getShopDashboard(@RequestParam(required = false, defaultValue = "1") Long shopId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new IllegalArgumentException("Shop not found: " + shopId));

        List<Connector> connectors = connectorRepository.findByShopId(shopId);
        long totalConnectors = connectors.size();
        long walkInReserves = connectors.stream().filter(Connector::getIsWalkInReserve).count();
        long onlineInventory = totalConnectors - walkInReserves;
        long occupiedConnectors = connectors.stream().filter(c -> "OCCUPIED".equals(c.getStatus())).count();

        List<ChargingSession> activeSessions = sessionRepository.findActiveSessionsByShopId(shopId);
        double activeEvLoadKw = activeSessions.stream().mapToDouble(ChargingSession::getCurrentPowerKw).sum();

        List<Booking> bookings = bookingRepository.findByShopId(shopId);
        long upcomingBookings = bookings.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).count();

        Transformer t = shop.getTransformer();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("shop", shop);
        resp.put("transformer", t);
        resp.put("totalConnectors", totalConnectors);
        resp.put("walkInReserves", walkInReserves);
        resp.put("onlineInventory", onlineInventory);
        resp.put("occupiedConnectors", occupiedConnectors);
        resp.put("activeSessionsCount", activeSessions.size());
        resp.put("activeEvLoadKw", Math.round(activeEvLoadKw * 10.0) / 10.0);
        resp.put("upcomingBookings", upcomingBookings);
        resp.put("allocatedCapacityKw", shop.getAllocatedCapacityKw());
        resp.put("transformerApprovedLimitKw", t.getApprovedLimitKw());
        resp.put("transformerUsableBudgetKw", t.getUsableEvBudgetKw());
        return ResponseEntity.ok(resp);
    }

    // --- STATIONS & CONNECTORS ---
    @GetMapping("/stations")
    public ResponseEntity<?> getShopStations(@RequestParam(required = false, defaultValue = "1") Long shopId) {
        List<Station> stations = stationRepository.findByShopId(shopId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Station s : stations) {
            List<Charger> chargers = chargerRepository.findByStationId(s.getId());
            List<Connector> connectors = connectorRepository.findByStationId(s.getId());
            result.add(Map.of(
                    "station", s,
                    "chargers", chargers,
                    "connectors", connectors
            ));
        }

        return ResponseEntity.ok(result);
    }

    // --- CONFIGURE WALK-IN RESERVES ---
    @PostMapping("/reserve-config")
    public ResponseEntity<?> configureReserves(
            @RequestParam(required = false, defaultValue = "1") Long stationId,
            @RequestBody ReserveConfigRequest req) {

        List<Connector> connectors = connectorRepository.findByStationId(stationId);
        int total = connectors.size();
        if (req.walkInReserveCount() > total) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Reserve count (" + req.walkInReserveCount() + ") cannot exceed total physical connectors (" + total + ")"));
        }

        // Configure: first (total - reserveCount) as online, last reserveCount as walk-in
        int onlineTarget = total - req.walkInReserveCount();
        for (int i = 0; i < total; i++) {
            Connector c = connectors.get(i);
            c.setIsWalkInReserve(i >= onlineTarget);
            connectorRepository.save(c);
        }

        return ResponseEntity.ok(Map.of(
                "message", "Reserve configuration updated successfully",
                "totalConnectors", total,
                "walkInReserveCount", req.walkInReserveCount(),
                "onlineCount", onlineTarget
        ));
    }

    // --- PRICING & TURNAROUND BUFFER CONFIG ---
    @PostMapping("/pricing-config")
    public ResponseEntity<?> configurePricing(
            @RequestParam(required = false, defaultValue = "1") Long stationId,
            @RequestBody PricingConfigRequest req) {

        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new IllegalArgumentException("Station not found: " + stationId));

        if (req.baseTariff() != null) station.setBaseTariffPerKwh(req.baseTariff());
        if (req.floorPrice() != null) station.setPricingFloorPerKwh(req.floorPrice());
        if (req.capPrice() != null) station.setPricingCapPerKwh(req.capPrice());
        if (req.turnaroundBufferMinutes() != null) station.setTurnaroundBufferMinutes(req.turnaroundBufferMinutes());

        stationRepository.save(station);
        return ResponseEntity.ok(station);
    }

    // --- BOOKINGS & CALENDAR ---
    @GetMapping("/bookings")
    public ResponseEntity<?> getShopBookings(@RequestParam(required = false, defaultValue = "1") Long shopId) {
        return ResponseEntity.ok(bookingRepository.findByShopId(shopId));
    }

    // --- WALK-IN CUSTOMER ADMISSION ---
    @PostMapping("/walk-in")
    public ResponseEntity<?> admitWalkIn(
            @RequestParam(required = false, defaultValue = "1") Long shopId,
            @RequestBody WalkInAdmissionRequest req) {

        try {
            // Find an available reserve connector
            List<Connector> connectors = connectorRepository.findByShopId(shopId);
            Connector reserveConnector = connectors.stream()
                    .filter(Connector::getIsWalkInReserve)
                    .filter(c -> "AVAILABLE".equals(c.getStatus()))
                    .filter(c -> c.getConnectorType().equalsIgnoreCase(req.connectorType()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No available walk-in reserve connectors found with connector type " + req.connectorType()));

            // Find or create customer
            User customer = userRepository.findByEmail(req.customerEmail())
                    .orElseGet(() -> userRepository.save(new User(
                            "walkin_" + System.currentTimeMillis() % 10000,
                            req.customerEmail(),
                            "walkin123",
                            req.customerName(),
                            req.phone(),
                            Role.CUSTOMER,
                            null
                    )));

            // Find or create vehicle
            Vehicle vehicle = vehicleRepository.findByLicensePlate(req.licensePlate())
                    .orElseGet(() -> vehicleRepository.save(new Vehicle(
                            customer,
                            req.make() != null ? req.make() : "Generic EV",
                            req.model() != null ? req.model() : "EV",
                            req.licensePlate(),
                            req.batteryCapacityKwh() != null ? req.batteryCapacityKwh() : 60.0,
                            50.0,
                            req.connectorType(),
                            req.currentSoC()
                    )));

            // Create walk-in booking atomically
            BookingService.BookingRequest bookingReq = new BookingService.BookingRequest(
                    customer.getId(),
                    vehicle.getId(),
                    reserveConnector.getId(),
                    LocalDateTime.now(),
                    req.currentSoC(),
                    req.targetSoC(),
                    null,
                    true, // isWalkIn = true
                    false
            );

            Booking booking = bookingService.createBooking(bookingReq);

            // Auto-start charging session for walk-in
            ChargingSession session = sessionService.startSession(booking.getId());

            return ResponseEntity.ok(Map.of(
                    "booking", booking,
                    "session", session,
                    "connector", reserveConnector
            ));

        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    // --- SESSIONS MANAGEMENT ---
    @GetMapping("/sessions")
    public ResponseEntity<?> getShopSessions(@RequestParam(required = false, defaultValue = "1") Long shopId) {
        return ResponseEntity.ok(sessionService.getShopSessions(shopId));
    }

    @PostMapping("/sessions/{id}/start")
    public ResponseEntity<?> startSession(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(sessionService.startSession(id));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/sessions/{id}/complete")
    public ResponseEntity<?> completeSession(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "Completed by operator") String notes) {
        try {
            return ResponseEntity.ok(sessionService.completeSession(id, notes));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    // --- CAPACITY REQUESTS ---
    @PostMapping("/capacity-request")
    public ResponseEntity<?> requestAdditionalCapacity(
            @RequestParam(required = false, defaultValue = "1") Long shopId,
            @RequestBody ShopCapacityReq req) {
        try {
            CapacityRequest request = capacityManagementService.submitCapacityRequest(
                    shopId, req.requestedKw(), req.reason());
            return ResponseEntity.ok(request);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    // --- STAFF ---
    @GetMapping("/staff")
    public ResponseEntity<?> getStaff(@RequestParam(required = false, defaultValue = "1") Long shopId) {
        return ResponseEntity.ok(userRepository.findByAssignedShopId(shopId));
    }

    public record ReserveConfigRequest(int walkInReserveCount) {}
    public record PricingConfigRequest(Double baseTariff, Double floorPrice, Double capPrice, Integer turnaroundBufferMinutes) {}
    public record WalkInAdmissionRequest(String customerName, String customerEmail, String phone, String licensePlate, String make, String model, Double batteryCapacityKwh, String connectorType, Integer currentSoC, Integer targetSoC) {}
    public record ShopCapacityReq(double requestedKw, String reason) {}
}
