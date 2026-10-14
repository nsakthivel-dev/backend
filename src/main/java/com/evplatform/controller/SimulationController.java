package com.evplatform.controller;

import com.evplatform.domain.ChargingSession;
import com.evplatform.domain.Transformer;
import com.evplatform.repository.ChargerRepository;
import com.evplatform.repository.ChargingSessionRepository;
import com.evplatform.repository.TransformerRepository;
import com.evplatform.service.ChargingSessionService;
import com.evplatform.service.ForecastingService;
import com.evplatform.service.SimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private final SimulationService simulationService;
    private final ChargingSessionService sessionService;
    private final ChargingSessionRepository sessionRepository;
    private final TransformerRepository transformerRepository;
    private final ChargerRepository chargerRepository;
    private final ForecastingService forecastingService;

    public SimulationController(
            SimulationService simulationService,
            ChargingSessionService sessionService,
            ChargingSessionRepository sessionRepository,
            TransformerRepository transformerRepository,
            ChargerRepository chargerRepository,
            ForecastingService forecastingService) {
        this.simulationService = simulationService;
        this.sessionService = sessionService;
        this.sessionRepository = sessionRepository;
        this.transformerRepository = transformerRepository;
        this.chargerRepository = chargerRepository;
        this.forecastingService = forecastingService;
    }

    @GetMapping("/state")
    public ResponseEntity<?> getSimulationState() {
        Transformer t = transformerRepository.findAll().get(0);
        List<ChargingSession> active = sessionRepository.findByStatus("ACTIVE");

        return ResponseEntity.ok(Map.of(
                "simulationActive", true,
                "simulatedNonEvSpikeKw", simulationService.getSimulatedNonEvSpikeKw(),
                "walkInSurgeMultiplier", simulationService.getWalkInSurgeMultiplier(),
                "transformerNonEvBaselineKw", t.getNonEvBaselineKw(),
                "activeSessionsCount", active.size(),
                "chargers", chargerRepository.findAll()
        ));
    }

    @PostMapping("/spike-load")
    public ResponseEntity<?> spikeLoad(@RequestBody Map<String, Double> body) {
        double kw = body.getOrDefault("additionalKw", 50.0);
        simulationService.injectNonEvSpike(kw);
        return ResponseEntity.ok(Map.of("message", "Injected non-EV load spike of +" + kw + " kW"));
    }

    @PostMapping("/trigger-fault")
    public ResponseEntity<?> triggerFault(@RequestBody Map<String, Long> body) {
        Long chargerId = body.get("chargerId");
        simulationService.simulateChargerFault(chargerId);
        return ResponseEntity.ok(Map.of("message", "Charger fault simulated for charger #" + chargerId));
    }

    @PostMapping("/restore-fault")
    public ResponseEntity<?> restoreFault(@RequestBody Map<String, Long> body) {
        Long chargerId = body.get("chargerId");
        simulationService.restoreCharger(chargerId);
        return ResponseEntity.ok(Map.of("message", "Charger restored to operational for charger #" + chargerId));
    }

    @PostMapping("/advance-sessions")
    public ResponseEntity<?> advanceSessions(@RequestBody(required = false) Map<String, Integer> body) {
        int percentIncrease = (body != null && body.containsKey("percentIncrease")) ? body.get("percentIncrease") : 15;
        List<ChargingSession> active = sessionRepository.findByStatus("ACTIVE");

        for (ChargingSession s : active) {
            int newSoC = Math.min(100, s.getCurrentSoC() + percentIncrease);
            double addedKwh = (s.getCurrentPowerKw() * (percentIncrease / 100.0) * 0.7);
            sessionService.updateProgress(s.getId(), newSoC, addedKwh);
        }

        Transformer t = transformerRepository.findAll().get(0);
        forecastingService.generateForecastForTransformer(t.getId());

        return ResponseEntity.ok(Map.of(
                "message", "Advanced " + active.size() + " active sessions by +" + percentIncrease + "% SoC",
                "activeSessionsRemaining", sessionRepository.findByStatus("ACTIVE").size()
        ));
    }
}
