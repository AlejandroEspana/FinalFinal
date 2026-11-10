package com.empresa.mantenimiento.domain.service;

import com.empresa.mantenimiento.shared.util.NumberRounderUtil;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Servicio de Dominio encargado de generar valores estocásticos realistas de vibración RMS.
 */
@Service
public class TelemetryGeneratorDomainService {

    /**
     * Genera un valor aleatorio de vibración (mm/s):
     * - 75% probabilidad: operación normal (20.0 - 55.0 mm/s)
     * - 18% probabilidad: degradación / advertencia (76.0 - 88.0 mm/s)
     * - 7% probabilidad: pico crítico (> 90.0 mm/s)
     */
    public double calculateNextRandomVibration() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int roll = random.nextInt(100);

        double raw;
        if (roll < 75) {
            raw = 20.0 + random.nextDouble() * 35.0;
        } else if (roll < 93) {
            raw = 76.0 + random.nextDouble() * 12.0;
        } else {
            raw = 91.0 + random.nextDouble() * 8.5;
        }
        return NumberRounderUtil.roundToTwoDecimals(raw);
    }
}
