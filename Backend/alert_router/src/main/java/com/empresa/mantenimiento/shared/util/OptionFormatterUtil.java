package com.empresa.mantenimiento.shared.util;

public final class OptionFormatterUtil {

    private OptionFormatterUtil() {}

    public static String normalizeOption(String option) {
        return option == null ? "" : option.trim().toUpperCase();
    }
}
