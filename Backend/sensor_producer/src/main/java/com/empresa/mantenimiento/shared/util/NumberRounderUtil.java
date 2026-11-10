package com.empresa.mantenimiento.shared.util;

public final class NumberRounderUtil {

    private NumberRounderUtil() {}

    public static double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
