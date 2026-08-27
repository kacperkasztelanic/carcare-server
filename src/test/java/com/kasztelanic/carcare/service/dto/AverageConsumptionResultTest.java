package com.kasztelanic.carcare.service.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

class AverageConsumptionResultTest {

    @Test
    void returnsZeroForZeroMileage() {
        assertThat(AverageConsumptionResult.of(null, 45.0, 0).getAverageConsumption()).isEqualTo(0.0);
    }

    @Test
    void preservesPositiveAndNegativeNonZeroResults() {
        assertThat(Double.doubleToLongBits(AverageConsumptionResult.of(null, 45.0, 10_000).getAverageConsumption()))
            .isEqualTo(Double.doubleToLongBits(baselineAverageConsumption(45.0, 10_000)));
        assertThat(Double.doubleToLongBits(AverageConsumptionResult.of(null, 45.0, -10_000).getAverageConsumption()))
            .isEqualTo(Double.doubleToLongBits(baselineAverageConsumption(45.0, -10_000)));
    }

    private static double baselineAverageConsumption(double volume, int mileage) {
        return BigDecimal.valueOf(volume * 100.0 / mileage)
            .setScale(1, RoundingMode.HALF_UP)
            .doubleValue();
    }
}
