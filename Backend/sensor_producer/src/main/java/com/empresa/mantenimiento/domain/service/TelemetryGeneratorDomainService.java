package com.empresa.mantenimiento.domain.service;

import com.empresa.mantenimiento.shared.util.NumberRounderUtil;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Servicio de Dominio Puro (sin dependencias de Spring) encargado de generar valores
 * estocásticos realistas de vibración RMS.
 */
public class TelemetryGeneratorDomainService {

    /**
     * Genera un valor aleatorio de vibración (mm/s):
     * - 90% probabilidad: operación normal (20.0 - 58.0 mm/s)
     * - 8% probabilidad: degradación / advertencia (76.0 - 88.0 mm/s)
     * - 2% probabilidad: pico crítico (> 90.0 mm/s)
     */
    public double calculateNextRandomVibration() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int roll = random.nextInt(100);

        double raw;
        if (roll < 90) {
            raw = 20.0 + random.nextDouble() * 38.0;
        } else if (roll < 98) {
            raw = 76.0 + random.nextDouble() * 12.0;
        } else {
            raw = 91.0 + random.nextDouble() * 8.5;
        }
        return NumberRounderUtil.roundToTwoDecimals(raw);
    }
}
