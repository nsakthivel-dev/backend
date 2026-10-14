package com.evplatform.service;

import com.evplatform.domain.*;
import com.evplatform.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ConnectorRepository connectorRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final CapacityManagementService capacityManagementService;
    private final AlgorithmEngineService algorithmEngineService;
    private final DynamicPricingService dynamicPricingService;
    private final ForecastingService forecastingService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public BookingService(
            BookingRepository bookingRepository,
            ConnectorRepository connectorRepository,
            VehicleRepository vehicleRepository,
            UserRepository userRepository,
            CapacityManagementService capacityManagementService,
            AlgorithmEngineService algorithmEngineService,
            DynamicPricingService dynamicPricingService,
            ForecastingService forecastingService,
            NotificationService notificationService,
            AuditService auditService) {
        this.bookingRepository = bookingRepository;
        this.connectorRepository = connectorRepository;
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
        this.capacityManagementService = capacityManagementService;
        this.algorithmEngineService = algorithmEngineService;
        this.dynamicPricingService = dynamicPricingService;
        this.forecastingService = forecastingService;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    /**
     * Algorithm 5: Atomic Booking Validation and Reservation.
     * Uses Isolation.SERIALIZABLE / REPEATABLE_READ transaction and connector locking.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public synchronized Booking createBooking(BookingRequest request) {
        // 1. Validate Customer
        User customer = userRepository.findById(request.customerId())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + request.customerId()));

        // 2. Validate Vehicle
        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
                .orElseThrow(() -> new IllegalArgumentException("Vehicle not found: " + request.vehicleId()));

        // 3. Atomically lock & validate connector
        Connector connector = connectorRepository.findByIdForUpdate(request.connectorId())
                .orElseThrow(() -> new IllegalArgumentException("Connector not found: " + request.connectorId()));

        if ("FAULTED".equals(connector.getStatus()) || "OFFLINE".equals(connector.getStatus())) {
            throw new IllegalStateException("Selected connector is currently out of service.");
        }

        // 4. Verify Connector Compatibility
        if (!connector.getConnectorType().equalsIgnoreCase(vehicle.getConnectorType())) {
            throw new IllegalArgumentException(String.format(
                    "Connector type mismatch: Vehicle requires %s but connector is %s",
                    vehicle.getConnectorType(), connector.getConnectorType()));
        }

        // 5. Walk-in reserve enforcement: ordinary online booking cannot claim walk-in reserve!
        if (connector.getIsWalkInReserve() && !request.isWalkIn() && !request.isUrgent()) {
            throw new IllegalStateException("Connector is strictly designated as a walk-in reserve. Please select an online booking connector.");
        }

        // 6. Algorithm 1: Calculate duration and mandatory 10-minute turnaround buffer
        Station station = connector.getCharger().getStation();
        int currentSoC = (request.currentSoC() != null) ? request.currentSoC() : vehicle.getCurrentSoC();
        int targetSoC = (request.targetSoC() != null) ? request.targetSoC() : 80;

        var occupancy = algorithmEngineService.calculateOccupancy(
                currentSoC, targetSoC, 60, station.getTurnaroundBufferMinutes());

        LocalDateTime startTime = request.startTime();
        if (startTime.isBefore(LocalDateTime.now().minusMinutes(5))) {
            throw new IllegalArgumentException("Booking start time cannot be in the past.");
        }

        LocalDateTime endTime = startTime.plusMinutes(occupancy.totalOccupancyMinutes());

        // 7. Check connector overlapping bookings in the occupancy interval
        List<BookingStatus> activeStatuses = Arrays.asList(
                BookingStatus.CONFIRMED,
                BookingStatus.CHECKED_IN,
                BookingStatus.IN_PROGRESS
        );

        List<Booking> overlappingConnectorBookings = bookingRepository.findOverlappingBookings(
                connector.getId(), startTime, endTime, activeStatuses);

        if (!overlappingConnectorBookings.isEmpty()) {
            throw new IllegalStateException("Connector is already booked or occupied during the requested interval.");
        }

        // 8. Shared transformer capacity check (Algorithm 3 & 5)
        Transformer transformer = station.getShop().getTransformer();
        double requestedChargingPowerKw = Math.min(connector.getMaxPowerKw(), vehicle.getMaxChargingRateKw());

        var capacityResult = capacityManagementService.checkTransformerCapacity(
                transformer, startTime, endTime, requestedChargingPowerKw);

        if (!capacityResult.isFeasible()) {
            // For urgent requests, attempt safe power reallocation (Algorithm 6)
            if (request.isUrgent()) {
                boolean reallocated = capacityManagementService.attemptSafePowerReallocation(
                        transformer, requestedChargingPowerKw);
                if (!reallocated) {
                    throw new IllegalStateException("Urgent charging cannot be authorized: " + capacityResult.message());
                }
            } else {
                throw new IllegalStateException("Booking rejected due to electrical limits: " + capacityResult.message());
            }
        }

        // 9. Pricing: Validate price quote or generate tariff (Algorithm 4)
        double bookedPricePerKwh;
        if (request.quoteToken() != null && !request.quoteToken().isBlank()) {
            PriceQuote quote = dynamicPricingService.validateQuote(request.quoteToken());
            bookedPricePerKwh = quote.getCalculatedPricePerKwh();
            quote.setStatus("ACCEPTED");
        } else {
            var dynamicPrice = algorithmEngineService.calculateDynamicPrice(
                    station.getBaseTariffPerKwh(),
                    station.getPricingFloorPerKwh(),
                    station.getPricingCapPerKwh(),
                    capacityResult.maxCommittedKw(),
                    capacityResult.usableBudgetKw()
            );
            bookedPricePerKwh = dynamicPrice.calculatedPricePerKwh();
        }

        // Calculate estimated energy (kWh) = batteryCapacity * (targetSoC - currentSoC) / 100
        double estimatedKwh = vehicle.getBatteryCapacityKwh() * (targetSoC - currentSoC) / 100.0;
        double totalEstimatedCost = Math.round(estimatedKwh * bookedPricePerKwh * 100.0) / 100.0;

        // 10. Persist Booking
        String bookingRef = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Booking booking = new Booking(
                bookingRef,
                customer,
                vehicle,
                connector,
                startTime,
                endTime,
                occupancy.estimatedChargingDurationMinutes(),
                occupancy.turnaroundBufferMinutes(),
                currentSoC,
                targetSoC,
                bookedPricePerKwh,
                totalEstimatedCost,
                requestedChargingPowerKw,
                request.isWalkIn(),
                request.isUrgent()
        );

        Booking savedBooking = bookingRepository.save(booking);

        // Update vehicle's current SoC
        vehicle.setCurrentSoC(currentSoC);
        vehicleRepository.save(vehicle);

        // Log audit and notification
        auditService.logAction(customer.getUsername(), customer.getRole().name(), "BOOKING_CREATED",
                "Booking", savedBooking.getId(),
                String.format("Created booking %s at station %s (Connector #%d), Allocated: %.1f kW, Price: $%.2f/kWh",
                        bookingRef, station.getName(), connector.getConnectorNumber(), requestedChargingPowerKw, bookedPricePerKwh),
                "127.0.0.1");

        notificationService.sendNotification(customer, null, "Booking Confirmed",
                String.format("Your booking %s at %s has been confirmed for %s.",
                        bookingRef, station.getName(), startTime.toLocalTime()),
                "INFO", savedBooking.getId());

        // Refresh transformer forecast asynchronously or synchronously
        forecastingService.generateForecastForTransformer(transformer.getId());

        return savedBooking;
    }

    @Transactional
    public Booking cancelBooking(Long bookingId, String cancelledByUsername) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        if (booking.getStatus() == BookingStatus.COMPLETED || booking.getStatus() == BookingStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot cancel a booking that is currently in progress or already completed.");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        Booking updated = bookingRepository.save(booking);

        auditService.logAction(cancelledByUsername, "USER", "BOOKING_CANCELLED",
                "Booking", booking.getId(), "Booking cancelled by " + cancelledByUsername, "127.0.0.1");

        notificationService.sendNotification(booking.getCustomer(), null, "Booking Cancelled",
                "Your booking " + booking.getBookingReference() + " has been cancelled.", "INFO", booking.getId());

        // Reforecast after cancellation
        forecastingService.generateForecastForTransformer(
                booking.getConnector().getCharger().getStation().getShop().getTransformer().getId());

        return updated;
    }

    public List<Booking> getCustomerBookings(Long customerId) {
        return bookingRepository.findByCustomerIdOrderByStartTimeDesc(customerId);
    }

    public List<Booking> getShopBookings(Long shopId) {
        return bookingRepository.findByShopId(shopId);
    }

    public record BookingRequest(
            Long customerId,
            Long vehicleId,
            Long connectorId,
            LocalDateTime startTime,
            Integer currentSoC,
            Integer targetSoC,
            String quoteToken,
            boolean isWalkIn,
            boolean isUrgent
    ) {}
}
