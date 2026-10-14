package com.evplatform.controller;

import com.evplatform.service.SmartChargingRecommendationService;
import com.evplatform.service.SmartChargingRecommendationService.RecommendationRequest;
import com.evplatform.service.SmartChargingRecommendationService.RecommendationResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class RecommendationController {

    private final SmartChargingRecommendationService recommendationService;

    public RecommendationController(SmartChargingRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    /**
     * Section 8 API: POST /api/v1/charging-recommendations
     * Also mapped to /api/customer/recommendations for frontend convenience.
     */
    @PostMapping(path = {"/api/v1/charging-recommendations", "/api/customer/recommendations"})
    public ResponseEntity<?> getChargingRecommendations(@RequestBody RecommendationRequest req) {
        try {
            RecommendationResult result = recommendationService.evaluateAndRecommend(req);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }
}
