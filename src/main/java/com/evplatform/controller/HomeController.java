package com.evplatform.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HomeController {

    @GetMapping({"/", "/api", "/api/health"})
    public ResponseEntity<Map<String, Object>> getHealth() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "VoltGrid EV Platform Backend",
                "version", "1.0.0",
                "database", "Supabase PostgreSQL Connected",
                "endpoints", Map.of(
                        "stations", "/api/customer/stations",
                        "vehicles", "/api/customer/vehicles?customerId=1",
                        "shopDashboard", "/api/shop/dashboard?shopId=1",
                        "utilityDashboard", "/api/utility/dashboard",
                        "authUsers", "/api/auth/users"
                ),
                "frontends", Map.of(
                        "evBooking", "https://ev-booking.netlify.app/",
                        "shopManager", "https://ev-shop-management.netlify.app/",
                        "gridAdmin", "https://grid-admin.netlify.app/"
                )
        ));
    }
}
