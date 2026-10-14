package com.evplatform.service;

import com.evplatform.domain.Transformer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AlgorithmEngineService {

    @Value("${app.scheduling.default-full-charge-duration-minutes:60}")
    private int defaultFullChargeDurationMinutes = 60;

    @Value("${app.scheduling.default-turnaround-buffer-minutes:10}")
    private int defaultTurnaroundBufferMinutes = 10;

    /**
     * Algorithm 1: Charging Duration and Connector Occupancy
     * Formula:
     *   Estimated charging duration = configured full-charge duration * (targetSoC - currentSoC) / 100
     *   Connector occupancy = ceil(estimated charging duration) + turnaroundBufferMinutes (mandatory 10 mins)
     */
    public DurationEstimate calculateOccupancy(int currentSoC, int targetSoC, Integer customFullDurationMinutes, Integer customBufferMinutes) {
        if (currentSoC < 0) currentSoC = 0;
        if (targetSoC > 100) targetSoC = 100;
        if (targetSoC <= currentSoC) {
            targetSoC = Math.min(100, currentSoC + 10); // Minimum 10% charge bump
        }

        int fullDuration = (customFullDurationMinutes != null && customFullDurationMinutes > 0)
                ? customFullDurationMinutes : defaultFullChargeDurationMinutes;
        int buffer = (customBufferMinutes != null && customBufferMinutes >= 0)
                ? customBufferMinutes : defaultTurnaroundBufferMinutes;

        int socDelta = targetSoC - currentSoC;
        double estimatedDurationMinutesDouble = (double) fullDuration * socDelta / 100.0;
        int estimatedDurationMinutes = (int) Math.ceil(estimatedDurationMinutesDouble);
        if (estimatedDurationMinutes < 5) {
            estimatedDurationMinutes = 5; // Minimum 5 min physical charging
        }

        int totalOccupancyMinutes = estimatedDurationMinutes + buffer;

        return new DurationEstimate(estimatedDurationMinutes, buffer, totalOccupancyMinutes, currentSoC, targetSoC);
    }

    /**
     * Algorithm 3: Usable EV charging budget for transformer
     * Accounting for power factor, approved kW limit, safety margin, and non-EV baseline.
     */
    public double calculateUsableEvBudget(Transformer transformer) {
        double approvedLimit = transformer.getApprovedLimitKw();
        double safetyMarginPercent = transformer.getSafetyMarginPercent();
        double nonEvBaseline = transformer.getNonEvBaselineKw();

        double safeOperatingCapacity = approvedLimit * (1.0 - (safetyMarginPercent / 100.0));
        double usableBudget = safeOperatingCapacity - nonEvBaseline;
        return Math.max(0.0, usableBudget);
    }

    /**
     * Algorithm 4: Dynamic Pricing calculation
     * Formula:
     *   congestionIndex = forecastedLoad / usableEvBudget
     *   candidatePrice = baseTariff * (1.0 + 0.6 * clamp(congestionIndex, 0.0, 1.5))
     *   clamped between floor and cap.
     */
    public DynamicPriceResult calculateDynamicPrice(double baseTariff, double floorPrice, double capPrice, double forecastedLoad, double usableBudget) {
        double congestionIndex = 0.0;
        if (usableBudget > 0.0) {
            congestionIndex = forecastedLoad / usableBudget;
        } else {
            congestionIndex = 1.5; // Highly congested if budget is zero
        }

        double congestionFactor = Math.min(2.0, Math.max(0.0, congestionIndex));
        double candidatePrice = baseTariff * (1.0 + 0.9 * congestionFactor);

        // Apply floor and cap constraints strictly
        double finalPrice = Math.min(capPrice, Math.max(floorPrice, candidatePrice));

        // Round to 2 decimal places
        finalPrice = Math.round(finalPrice * 100.0) / 100.0;
        congestionIndex = Math.round(congestionIndex * 100.0) / 100.0;

        return new DynamicPriceResult(finalPrice, baseTariff, floorPrice, capPrice, congestionIndex);
    }

    public record DurationEstimate(
            int estimatedChargingDurationMinutes,
            int turnaroundBufferMinutes,
            int totalOccupancyMinutes,
            int initialSoC,
            int targetSoC
    ) {}

    public record DynamicPriceResult(
            double calculatedPricePerKwh,
            double baseTariff,
            double floorPrice,
            double capPrice,
            double congestionIndex
    ) {}
}
