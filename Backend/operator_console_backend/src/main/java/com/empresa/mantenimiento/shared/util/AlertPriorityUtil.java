package com.empresa.mantenimiento.shared.util;

public final class AlertPriorityUtil {

    private AlertPriorityUtil() {}

    public static boolean isCritical(String severity) {
        return "CRITICAL".equalsIgnoreCase(severity);
    }
}
