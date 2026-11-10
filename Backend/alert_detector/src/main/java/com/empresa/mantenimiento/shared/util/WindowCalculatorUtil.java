package com.empresa.mantenimiento.shared.util;

import java.util.List;

public final class WindowCalculatorUtil {

    private WindowCalculatorUtil() {}

    public static double calculateAverage(List<Double> values) {
        if (values == null || values.isEmpty()) return 0.0;
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}
