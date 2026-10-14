package com.evplatform.controller;

import com.evplatform.domain.*;
import com.evplatform.repository.*;
import com.evplatform.service.BookingService;
import com.evplatform.service.ChargingSessionService;
import com.evplatform.service.DynamicPricingService;
import com.evplatform.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/customer")
public class CustomerController {

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final StationRepository stationRepository;
    private final ConnectorRepository connectorRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final ChargingSessionService sessionService;
    private final DynamicPricingService dynamicPricingService;
    private final NotificationService notificationService;

    public CustomerController(
            VehicleRepository vehicleRepository,
            UserRepository userRepository,
            StationRepository stationRepository,
            ConnectorRepository connectorRepository,
            BookingRepository bookingRepository,
            BookingService bookingService,
            ChargingSessionService sessionService,
            DynamicPricingService dynamicPricingService,
            NotificationService notificationService) {
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
        this.stationRepository = stationRepository;
        this.connectorRepository = connectorRepository;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.sessionService = sessionService;
        this.dynamicPricingService = dynamicPricingService;
        this.notificationService = notificationService;
    }

    // --- VEHICLES ---
    @GetMapping("/vehicles")
    public ResponseEntity<List<Vehicle>> getCustomerVehicles(@RequestParam(required = false, defaultValue = "1") Long customerId) {
        return ResponseEntity.ok(vehicleRepository.findByUserId(customerId));
    }

    @PostMapping("/vehicles")
    public ResponseEntity<?> addVehicle(@RequestParam(required = false, defaultValue = "1") Long customerId, @RequestBody VehicleRequest req) {
        User user = userRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + customerId));

        Vehicle v = new Vehicle(
                user,
                req.make(),
                req.model(),
                req.licensePlate(),
                req.batteryCapacityKwh(),
                req.maxChargingRateKw(),
                req.connectorType(),
                req.currentSoC() != null ? req.currentSoC() : 30
        );
        return ResponseEntity.ok(vehicleRepository.save(v));
    }

    @PutMapping("/vehicles/{id}")
    public ResponseEntity<?> updateVehicle(@PathVariable Long id, @RequestBody VehicleRequest req) {
        Vehicle v = vehicleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Vehicle not found: " + id));
        if (req.make() != null && !req.make().isBlank()) v.setMake(req.make());
        if (req.model() != null && !req.model().isBlank()) v.setModel(req.model());
        if (req.licensePlate() != null && !req.licensePlate().isBlank()) v.setLicensePlate(req.licensePlate());
        if (req.batteryCapacityKwh() != null && req.batteryCapacityKwh() > 0) v.setBatteryCapacityKwh(req.batteryCapacityKwh());
        if (req.maxChargingRateKw() != null && req.maxChargingRateKw() > 0) v.setMaxChargingRateKw(req.maxChargingRateKw());
        if (req.connectorType() != null && !req.connectorType().isBlank()) v.setConnectorType(req.connectorType());
        if (req.currentSoC() != null) v.setCurrentSoC(req.currentSoC());
        return ResponseEntity.ok(vehicleRepository.save(v));
    }

    @DeleteMapping("/vehicles/{id}")
    public ResponseEntity<?> deleteVehicle(@PathVariable Long id) {
        if (!vehicleRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        try {
            vehicleRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "Vehicle deleted successfully"));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete vehicle with historical bookings or active sessions."));
        }
    }

    // --- STATIONS DISCOVERY ---
    @GetMapping("/stations")
    public ResponseEntity<?> getStations(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String connectorType) {

        List<Station> stations = stationRepository.findAll();
        List<Map<String, Object>> response = new ArrayList<>();

        for (Station s : stations) {
            List<Connector> connectors = connectorRepository.findByStationId(s.getId());

            // Filter out walk-in reserves from ordinary online booking count
            long availableOnlineConnectors = connectors.stream()
                    .filter(c -> !c.getIsWalkInReserve() && "AVAILABLE".equals(c.getStatus()))
                    .count();

            long totalOnlineConnectors = connectors.stream()
                    .filter(c -> !c.getIsWalkInReserve())
                    .count();

            long walkInReserveConnectors = connectors.stream()
                    .filter(Connector::getIsWalkInReserve)
                    .count();

            // Match filters
            if (search != null && !search.isBlank()) {
                String q = search.toLowerCase();
                if (!s.getName().toLowerCase().contains(q) && !s.getAddress().toLowerCase().contains(q)) {
                    continue;
                }
            }

            if (connectorType != null && !connectorType.isBlank()) {
                boolean hasConnector = connectors.stream()
                        .anyMatch(c -> c.getConnectorType().equalsIgnoreCase(connectorType));
                if (!hasConnector) continue;
            }

            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getId());
            map.put("name", s.getName());
            map.put("address", s.getAddress());
            map.put("latitude", s.getLatitude());
            map.put("longitude", s.getLongitude());
            map.put("baseTariffPerKwh", s.getBaseTariffPerKwh());
            map.put("pricingFloorPerKwh", s.getPricingFloorPerKwh());
            map.put("pricingCapPerKwh", s.getPricingCapPerKwh());
            map.put("turnaroundBufferMinutes", s.getTurnaroundBufferMinutes());
            map.put("status", s.getStatus());
            map.put("availableOnlineConnectors", availableOnlineConnectors);
            map.put("totalOnlineConnectors", totalOnlineConnectors);
            map.put("walkInReserveConnectors", walkInReserveConnectors);
            map.put("transformerCode", s.getShop().getTransformer().getCode());
            map.put("shopName", s.getShop().getName());

            response.add(map);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/stations/{id}")
    public ResponseEntity<?> getStationDetails(@PathVariable Long id) {
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Station not found: " + id));

        List<Connector> connectors = connectorRepository.findByStationId(id);

        return ResponseEntity.ok(Map.of(
                "station", station,
                "shop", station.getShop(),
                "transformer", station.getShop().getTransformer(),
                "connectors", connectors
        ));
    }

    // --- DYNAMIC PRICE QUOTE ---
    @PostMapping("/quote")
    public ResponseEntity<?> getQuote(@RequestBody QuoteRequest req) {
        LocalDateTime slotStart = req.slotStart() != null ? req.slotStart() : LocalDateTime.now().plusMinutes(10);
        LocalDateTime slotEnd = req.slotEnd() != null ? req.slotEnd() : slotStart.plusMinutes(45);

        PriceQuote quote = dynamicPricingService.generateQuote(req.stationId(), slotStart, slotEnd);
        return ResponseEntity.ok(quote);
    }

    // --- BOOKINGS ---
    @PostMapping("/bookings")
    public ResponseEntity<?> createBooking(@RequestBody BookingService.BookingRequest req) {
        try {
            Booking booking = bookingService.createBooking(req);
            return ResponseEntity.ok(booking);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/bookings")
    public ResponseEntity<?> getCustomerBookings(@RequestParam(required = false, defaultValue = "1") Long customerId) {
        return ResponseEntity.ok(bookingService.getCustomerBookings(customerId));
    }

    @PostMapping("/bookings/{id}/cancel")
    public ResponseEntity<?> cancelBooking(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "customer") String username) {
        try {
            Booking booking = bookingService.cancelBooking(id, username);
            return ResponseEntity.ok(booking);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    // --- URGENT CHARGING REQUEST ---
    @PostMapping("/urgent-request")
    public ResponseEntity<?> requestUrgentCharging(@RequestBody UrgentRequest req) {
        try {
            // Find an eligible connector at the station (can utilize walk-in reserve)
            List<Connector> connectors = connectorRepository.findByStationId(req.stationId());
            Connector suitableConnector = connectors.stream()
                    .filter(c -> c.getConnectorType().equalsIgnoreCase(req.connectorType()))
                    .filter(c -> !"FAULTED".equals(c.getStatus()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No compatible connectors found at station."));

            BookingService.BookingRequest bookingReq = new BookingService.BookingRequest(
                    req.customerId(),
                    req.vehicleId(),
                    suitableConnector.getId(),
                    LocalDateTime.now().plusMinutes(2),
                    req.currentSoC(),
                    req.targetSoC(),
                    null,
                    false,
                    true // isUrgent = true
            );

            Booking booking = bookingService.createBooking(bookingReq);
            return ResponseEntity.ok(booking);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    // --- ACTIVE CHARGING SESSION ---
    @GetMapping("/sessions/active")
    public ResponseEntity<?> getActiveSession(@RequestParam(required = false, defaultValue = "1") Long customerId) {
        List<ChargingSession> sessions = sessionService.getCustomerSessions(customerId);
        Optional<ChargingSession> active = sessions.stream()
                .filter(s -> "ACTIVE".equals(s.getStatus()))
                .findFirst();

        return active.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok().build());
    }

    // --- BILLING AND TRANSACTIONS ---
    @GetMapping("/billing")
    public ResponseEntity<?> getBillingHistory(@RequestParam(required = false, defaultValue = "1") Long customerId) {
        List<ChargingSession> completedSessions = sessionService.getCustomerSessions(customerId).stream()
                .filter(s -> "COMPLETED".equals(s.getStatus()))
                .toList();

        return ResponseEntity.ok(completedSessions);
    }

    // --- NOTIFICATIONS ---
    @GetMapping("/notifications")
    public ResponseEntity<?> getNotifications(@RequestParam(required = false, defaultValue = "1") Long customerId) {
        return ResponseEntity.ok(notificationService.getNotificationsForUser(customerId, Role.CUSTOMER));
    }

    public record VehicleRequest(String make, String model, String licensePlate, Double batteryCapacityKwh, Double maxChargingRateKw, String connectorType, Integer currentSoC) {}
    public record QuoteRequest(Long stationId, LocalDateTime slotStart, LocalDateTime slotEnd) {}
    public record UrgentRequest(Long customerId, Long vehicleId, Long stationId, String connectorType, Integer currentSoC, Integer targetSoC) {}
}
