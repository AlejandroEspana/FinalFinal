package com.empresa.mantenimiento.domain.model;

public enum AlertSeverity {
    NORMAL("Normal", 0),
    WARNING("Advertencia", 1),
    CRITICAL("Crítico", 2);

    private final String displayName;
    private final int level;

    AlertSeverity(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getLevel() {
        return level;
    }
}
