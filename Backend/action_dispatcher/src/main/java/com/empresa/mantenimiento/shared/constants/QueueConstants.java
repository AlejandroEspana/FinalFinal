package com.empresa.mantenimiento.shared.constants;

public final class QueueConstants {

    public static final String CRITICAL_ACTIONS_QUEUE = "critical_actions_queue";
    public static final String MAINTENANCE_QUEUE = "maintenance_queue";
    public static final String DELAYED_ACTIONS_EXCHANGE = "delayed_actions_exchange";
    public static final String DELAYED_ROUTING_KEY = "maintenance_route";

    private QueueConstants() {
    }
}
