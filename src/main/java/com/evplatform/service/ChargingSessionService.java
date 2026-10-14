package com.evplatform.service;

import com.evplatform.domain.*;
import com.evplatform.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ChargingSessionService {

    private final ChargingSessionRepository sessionRepository;
    private final BookingRepository bookingRepository;
    private final ConnectorRepository connectorRepository;
    private final VehicleRepository vehicleRepository;
    private final ForecastingService forecastingService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public ChargingSessionService(
            ChargingSessionRepository sessionRepository,
            BookingRepository bookingRepository,
            ConnectorRepository connectorRepository,
            VehicleRepository vehicleRepository,
            ForecastingService forecastingService,
            NotificationService notificationService,
            AuditService auditService) {
        this.sessionRepository = sessionRepository;
        this.bookingRepository = bookingRepository;
        this.connectorRepository = connectorRepository;
        this.vehicleRepository = vehicleRepository;
        this.forecastingService = forecastingService;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    /**
     * Start a charging session from a confirmed booking or walk-in.
     */
    @Transactional
    public ChargingSession startSession(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.COMPLETED) {
            throw new IllegalStateException("Cannot start session for booking with status " + booking.getStatus());
        }

        Connector connector = booking.getConnector();
        connector.setStatus("OCCUPIED");
        connectorRepository.save(connector);

        booking.setStatus(BookingStatus.IN_PROGRESS);
        bookingRepository.save(booking);

        String sessionRef = "SES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ChargingSession session = new ChargingSession(
                sessionRef,
                booking,
                connector,
                booking.getVehicle(),
                booking.getAllocatedPowerKw(),
                booking.getInitialSoC()
        );

        ChargingSession saved = sessionRepository.save(session);

        auditService.logAction(booking.getCustomer().getUsername(), "CUSTOMER", "SESSION_STARTED",
                "ChargingSession", saved.getId(),
                "Charging session " + sessionRef + " started on connector #" + connector.getConnectorNumber(), "127.0.0.1");

        notificationService.sendNotification(booking.getCustomer(), null, "Charging Started",
                "Your vehicle is now charging at " + connector.getCharger().getStation().getName(), "INFO", saved.getId());

        // Refresh forecast
        forecastingService.generateForecastForTransformer(
                connector.getCharger().getStation().getShop().getTransformer().getId());

        return saved;
    }

    /**
     * Updates live telemetry progress (e.g. SoC, metered kWh).
     */
    @Transactional
    public ChargingSession updateProgress(Long sessionId, int currentSoC, double additionalKwh) {
        ChargingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (!"ACTIVE".equals(session.getStatus())) {
            return session;
        }

        session.setCurrentSoC(currentSoC);
        session.setMeteredKwh(session.getMeteredKwh() + additionalKwh);

        // Update vehicle's current SoC
        Vehicle vehicle = session.getVehicle();
        vehicle.setCurrentSoC(currentSoC);
        vehicleRepository.save(vehicle);

        // Auto-complete if reached target SoC
        if (session.getBooking() != null && currentSoC >= session.getBooking().getTargetSoC()) {
            return completeSession(sessionId, "Target state-of-charge reached.");
        }

        return sessionRepository.save(session);
    }

    /**
     * Algorithm 8: Session completion and capacity release.
     */
    @Transactional
    public ChargingSession completeSession(Long sessionId, String notes) {
        ChargingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if ("COMPLETED".equals(session.getStatus())) {
            return session;
        }

        session.setStatus("COMPLETED");
        session.setActualEndTime(LocalDateTime.now());
        session.setCurrentPowerKw(0.0);
        session.setSessionNotes(notes);

        Booking booking = session.getBooking();
        if (booking != null) {
            booking.setStatus(BookingStatus.COMPLETED);
            bookingRepository.save(booking);

            double bookedPrice = booking.getBookedPricePerKwh();
            double finalBill = Math.round(session.getMeteredKwh() * bookedPrice * 100.0) / 100.0;
            session.setBilledAmount(finalBill);
        }

        // Release connector
        Connector connector = session.getConnector();
        connector.setStatus("AVAILABLE");
        connectorRepository.save(connector);

        ChargingSession saved = sessionRepository.save(session);

        if (booking != null) {
            auditService.logAction(booking.getCustomer().getUsername(), "CUSTOMER", "SESSION_COMPLETED",
                    "ChargingSession", saved.getId(),
                    String.format("Session %s completed: %.2f kWh delivered, Total: $%.2f",
                            saved.getSessionReference(), saved.getMeteredKwh(), saved.getBilledAmount()), "127.0.0.1");

            notificationService.sendNotification(booking.getCustomer(), null, "Charging Complete",
                    String.format("Your vehicle is ready. Total delivered: %.2f kWh ($%.2f).",
                            saved.getMeteredKwh(), saved.getBilledAmount()), "INFO", saved.getId());
        }

        // Refresh forecast and capacity allocation after capacity release
        forecastingService.generateForecastForTransformer(
                connector.getCharger().getStation().getShop().getTransformer().getId());

        return saved;
    }

    public List<ChargingSession> getActiveSessions() {
        return sessionRepository.findByStatus("ACTIVE");
    }

    public List<ChargingSession> getShopSessions(Long shopId) {
        return sessionRepository.findByShopId(shopId);
    }

    public List<ChargingSession> getCustomerSessions(Long customerId) {
        return sessionRepository.findByCustomerId(customerId);
    }
}
