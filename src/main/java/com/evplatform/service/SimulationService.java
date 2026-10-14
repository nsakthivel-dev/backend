package com.evplatform.service;

import com.evplatform.domain.*;
import com.evplatform.repository.*;
import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SimulationService {

    private final TransformerRepository transformerRepository;
    private final ShopRepository shopRepository;
    private final StationRepository stationRepository;
    private final ChargerRepository chargerRepository;
    private final ConnectorRepository connectorRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final BookingRepository bookingRepository;
    private final ChargingSessionRepository sessionRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final ForecastingService forecastingService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final TransformerRiskConfigRepository riskConfigRepository;

    // Simulation states
    private double simulatedNonEvSpikeKw = 0.0;
    private double walkInSurgeMultiplier = 1.0;

    public SimulationService(
            TransformerRepository transformerRepository,
            ShopRepository shopRepository,
            StationRepository stationRepository,
            ChargerRepository chargerRepository,
            ConnectorRepository connectorRepository,
            UserRepository userRepository,
            VehicleRepository vehicleRepository,
            BookingRepository bookingRepository,
            ChargingSessionRepository sessionRepository,
            MeterReadingRepository meterReadingRepository,
            ForecastingService forecastingService,
            AuditService auditService,
            NotificationService notificationService,
            PasswordEncoder passwordEncoder,
            TransformerRiskConfigRepository riskConfigRepository) {
        this.transformerRepository = transformerRepository;
        this.shopRepository = shopRepository;
        this.stationRepository = stationRepository;
        this.chargerRepository = chargerRepository;
        this.connectorRepository = connectorRepository;
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.bookingRepository = bookingRepository;
        this.sessionRepository = sessionRepository;
        this.meterReadingRepository = meterReadingRepository;
        this.forecastingService = forecastingService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
        this.riskConfigRepository = riskConfigRepository;
    }

    @PostConstruct
    @Transactional
    public void seedInitialData() {
        if (transformerRepository.count() > 0) {
            return; // Already seeded
        }

        // 1. Seed Transformer: TR-DOWNTOWN-01
        // 500 kVA nameplate, 0.95 power factor, 450 kW approved limit, 10% safety margin, 75 kW non-EV baseline
        // Usable EV Budget = (450 * 0.90) - 75 = 330 kW
        Transformer transformer = new Transformer(
                "TR-DOWNTOWN-01",
                "Downtown Central Substation T-1",
                "Downtown Metro Grid - Block 4",
                500.0,
                0.95,
                450.0,
                10.0,
                75.0
        );
        transformer = transformerRepository.save(transformer);

        // Seed initial User-Configured Risk Configuration (v1: 90% warning, 98% critical, 10% safety margin)
        TransformerRiskConfig initialRiskConfig = new TransformerRiskConfig(
                transformer,
                1,
                450.0,
                90.0, // 90% warning threshold (405 kW)
                98.0, // 98% critical threshold (441 kW)
                10.0, // 10% safety margin
                75.0, // 75 kW non-EV baseline
                "RESTRICT_NEW_ONLINE_BOOKINGS,OFFER_ALTERNATIVES",
                "EMERGENCY_THROTTLING,PROHIBIT_NEW_DEMAND",
                "UTILITY_SYSTEM",
                "Initial operational risk profile configured by Grid Authority"
        );
        riskConfigRepository.save(initialRiskConfig);

        // 2. Seed 3 Connected Shops
        Shop shop1 = new Shop("Metro Charge Hub", "Central Plaza, Sector 12", transformer, "24/7", "+1-555-0101", 180.0);
        Shop shop2 = new Shop("EcoVolt Express", "North Green Way, Sector 8", transformer, "06:00 - 23:00", "+1-555-0102", 90.0);
        Shop shop3 = new Shop("FastTrack EV Hub", "Transit Corridor, Sector 14", transformer, "24/7", "+1-555-0103", 60.0);
        shop1 = shopRepository.save(shop1);
        shop2 = shopRepository.save(shop2);
        shop3 = shopRepository.save(shop3);

        // 3. Seed Stations
        Station station1 = new Station(shop1, "Metro Central Supercharge", "Central Plaza Suite A", 37.7749, -122.4194, 0.22, 0.65, 0.35, 10);
        Station station2 = new Station(shop2, "EcoVolt Green Bay", "North Green Way 45", 37.7833, -122.4167, 0.20, 0.55, 0.30, 10);
        Station station3 = new Station(shop3, "FastTrack Highway Bay", "Transit Corridor Gate 2", 37.7650, -122.4250, 0.25, 0.70, 0.40, 10);
        station1 = stationRepository.save(station1);
        station2 = stationRepository.save(station2);
        station3 = stationRepository.save(station3);

        // 4. Seed Shop 1: 15 Connectors (10 Online, 5 Walk-in Reserve) across 8 chargers
        List<Connector> shop1Connectors = new ArrayList<>();
        int connectorCounter = 1;
        for (int c = 1; c <= 8; c++) {
            Charger charger = new Charger(station1, "CH-M" + c, "Terra 184 High-Power DC", 50.0, "DYNAMIC_POWER_CURTAILMENT,SMART_THROTTLING");
            charger = chargerRepository.save(charger);

            int plugsOnThisCharger = (c <= 7) ? 2 : 1; // 7*2 + 1 = 15 connectors
            for (int p = 1; p <= plugsOnThisCharger; p++) {
                boolean isWalkInReserve = (connectorCounter > 10); // First 10 online, last 5 walk-in
                Connector conn = new Connector(charger, p, "CCS2", 50.0, isWalkInReserve);
                shop1Connectors.add(connectorRepository.save(conn));
                connectorCounter++;
            }
        }

        // 5. Seed Shop 2: 8 Connectors (6 Online, 2 Walk-in Reserve)
        for (int c = 1; c <= 4; c++) {
            Charger charger = new Charger(station2, "CH-E" + c, "HyperCharger 100", 50.0, "DYNAMIC_POWER_CURTAILMENT");
            charger = chargerRepository.save(charger);
            for (int p = 1; p <= 2; p++) {
                boolean isWalkIn = (c == 4); // 2 connectors walk-in
                connectorRepository.save(new Connector(charger, p, "CCS2", 50.0, isWalkIn));
            }
        }

        // 6. Seed Shop 3: 6 Connectors (4 Online, 2 Walk-in Reserve)
        for (int c = 1; c <= 3; c++) {
            Charger charger = new Charger(station3, "CH-F" + c, "Kempower Sat-50", 50.0, "DYNAMIC_POWER_CURTAILMENT");
            charger = chargerRepository.save(charger);
            for (int p = 1; p <= 2; p++) {
                boolean isWalkIn = (c == 3);
                connectorRepository.save(new Connector(charger, p, (p == 1 ? "CCS2" : "CHAdeMO"), 50.0, isWalkIn));
            }
        }

        // 7. Seed Users with various roles
        User adminUser = new User("admin", "admin@gridportal.gov", passwordEncoder.encode("admin123"),
                "Dr. Alan Turing", "+1-555-9000", Role.SYSTEM_ADMIN, null);
        User utilityOp = new User("utility_op", "operator@gridpower.com", passwordEncoder.encode("utility123"),
                "Sarah Connor", "+1-555-9001", Role.UTILITY_OPERATOR, null);
        User utilityAdmin = new User("utility_admin", "admin@gridpower.com", passwordEncoder.encode("utility123"),
                "David Vance", "+1-555-9002", Role.UTILITY_ADMIN, null);
        User shopOwner = new User("shop_owner", "manager@metrocharge.com", passwordEncoder.encode("shop123"),
                "Marcus Aurelius", "+1-555-9003", Role.SHOP_OWNER, shop1.getId());
        User shopEmp = new User("shop_emp", "staff@metrocharge.com", passwordEncoder.encode("shop123"),
                "Elena Fisher", "+1-555-9004", Role.SHOP_EMPLOYEE, shop1.getId());
        User customer1 = new User("customer_john", "john@evuser.com", passwordEncoder.encode("pass123"),
                "John Doe", "+1-555-7001", Role.CUSTOMER, null);
        User customer2 = new User("customer_jane", "jane@evuser.com", passwordEncoder.encode("pass123"),
                "Jane Smith", "+1-555-7002", Role.CUSTOMER, null);

        adminUser = userRepository.save(adminUser);
        utilityOp = userRepository.save(utilityOp);
        utilityAdmin = userRepository.save(utilityAdmin);
        shopOwner = userRepository.save(shopOwner);
        shopEmp = userRepository.save(shopEmp);
        customer1 = userRepository.save(customer1);
        customer2 = userRepository.save(customer2);

        // 8. Seed Vehicles
        Vehicle v1 = new Vehicle(customer1, "Tesla", "Model 3 Long Range", "EV-TESLA-301", 75.0, 50.0, "CCS2", 35);
        Vehicle v2 = new Vehicle(customer1, "Hyundai", "Ioniq 5 AWD", "EV-IONIQ-502", 77.4, 50.0, "CCS2", 20);
        Vehicle v3 = new Vehicle(customer2, "Nissan", "Leaf e+", "EV-LEAF-403", 62.0, 50.0, "CHAdeMO", 45);
        v1 = vehicleRepository.save(v1);
        v2 = vehicleRepository.save(v2);
        v3 = vehicleRepository.save(v3);

        // 9. Seed Active Charging Sessions on Shop 1 (Connector 1 & 2)
        Connector conn1 = shop1Connectors.get(0);
        conn1.setStatus("OCCUPIED");
        connectorRepository.save(conn1);

        Booking activeBooking1 = new Booking(
                "BK-INIT-001", customer1, v1, conn1,
                LocalDateTime.now().minusMinutes(25), LocalDateTime.now().plusMinutes(25),
                40, 10, 30, 80, 0.35, 13.12, 50.0, false, false);
        activeBooking1.setStatus(BookingStatus.IN_PROGRESS);
        activeBooking1 = bookingRepository.save(activeBooking1);

        ChargingSession session1 = new ChargingSession(
                "SES-INIT-001", activeBooking1, conn1, v1, 50.0, 30);
        session1.setActualStartTime(LocalDateTime.now().minusMinutes(25));
        session1.setCurrentSoC(58);
        session1.setMeteredKwh(21.4);
        sessionRepository.save(session1);

        // 10. Seed Confirmed Future Bookings (Upcoming)
        Connector conn2 = shop1Connectors.get(1);
        Booking futureBooking1 = new Booking(
                "BK-INIT-002", customer2, v3, conn2,
                LocalDateTime.now().plusMinutes(30), LocalDateTime.now().plusMinutes(80),
                40, 10, 25, 80, 0.38, 14.25, 50.0, false, false);
        bookingRepository.save(futureBooking1);

        // 11. Seed Historical Meter Readings
        LocalDateTime now = LocalDateTime.now();
        for (int h = 12; h >= 0; h--) {
            double activeLoad = 120.0 + (Math.sin(h) * 40.0) + (h == 0 ? 50.0 : 0.0);
            double apparent = activeLoad / 0.95;
            meterReadingRepository.save(new MeterReading(
                    transformer, null, activeLoad, apparent, 0.95,
                    now.minusHours(h), "SIMULATED", "VALID"));
        }

        // 12. Run Forecast
        forecastingService.generateForecastForTransformer(transformer.getId());

        auditService.logAction("SYSTEM", "SYSTEM", "PLATFORM_INITIALIZED",
                "Transformer", transformer.getId(), "Initialized default 500kVA transformer with 3 connected shops and 29 connectors.", "127.0.0.1");
    }

    public void injectNonEvSpike(double additionalKw) {
        this.simulatedNonEvSpikeKw = additionalKw;
        Transformer t = transformerRepository.findAll().get(0);
        t.setNonEvBaselineKw(75.0 + additionalKw);
        transformerRepository.save(t);
        forecastingService.generateForecastForTransformer(t.getId());

        auditService.logAction("SIMULATOR", "OPERATOR", "SIM_NON_EV_SPIKE",
                "Transformer", t.getId(), "Injected non-EV load spike of +" + additionalKw + " kW", "127.0.0.1");
    }

    public void simulateChargerFault(Long chargerId) {
        Charger c = chargerRepository.findById(chargerId).orElseThrow();
        c.setStatus("FAULTED");
        chargerRepository.save(c);

        List<Connector> conns = connectorRepository.findByChargerId(chargerId);
        for (Connector conn : conns) {
            conn.setStatus("FAULTED");
            connectorRepository.save(conn);
        }

        Transformer t = c.getStation().getShop().getTransformer();
        forecastingService.generateForecastForTransformer(t.getId());

        notificationService.sendNotification(null, Role.SHOP_OWNER, "Charger Fault Triggered",
                "Charger " + c.getCode() + " reported a critical hardware fault.", "WARNING", c.getId());
    }

    public void restoreCharger(Long chargerId) {
        Charger c = chargerRepository.findById(chargerId).orElseThrow();
        c.setStatus("OPERATIONAL");
        chargerRepository.save(c);

        List<Connector> conns = connectorRepository.findByChargerId(chargerId);
        for (Connector conn : conns) {
            conn.setStatus("AVAILABLE");
            connectorRepository.save(conn);
        }
        Transformer t = c.getStation().getShop().getTransformer();
        forecastingService.generateForecastForTransformer(t.getId());
    }

    public double getSimulatedNonEvSpikeKw() { return simulatedNonEvSpikeKw; }
    public double getWalkInSurgeMultiplier() { return walkInSurgeMultiplier; }
    public void setWalkInSurgeMultiplier(double walkInSurgeMultiplier) { this.walkInSurgeMultiplier = walkInSurgeMultiplier; }
}
