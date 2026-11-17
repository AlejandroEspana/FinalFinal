package com.empresa.mantenimiento.domain.service;

import com.empresa.mantenimiento.shared.util.NumberRounderUtil;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Servicio de Dominio Puro (sin dependencias de Spring) encargado de generar valores
 * estocásticos realistas de vibración RMS sin saturar el flujo de alertas.
 */
public class TelemetryGeneratorDomainService {

    /**
     * Genera un valor aleatorio de vibración (mm/s) con distribución industrial realista:
     * - 97% probabilidad: operación normal estable (22.0 - 54.0 mm/s)
     * - 2% probabilidad: degradación / advertencia (76.0 - 84.0 mm/s)
     * - 1% probabilidad: pico crítico (> 90.0 mm/s)
     */
    public double calculateNextRandomVibration() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int roll = random.nextInt(100);

        double raw;
        if (roll < 97) {
            raw = 22.0 + random.nextDouble() * 32.0;
        } else if (roll < 99) {
            raw = 76.0 + random.nextDouble() * 8.0;
        } else {
            raw = 91.0 + random.nextDouble() * 7.5;
        }
        return NumberRounderUtil.roundToTwoDecimals(raw);
    }
}
