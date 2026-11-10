package com.empresa.mantenimiento.shared.util;

import java.util.UUID;

public final class CommandIdGeneratorUtil {

    private CommandIdGeneratorUtil() {
    }

    public static String nextId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
