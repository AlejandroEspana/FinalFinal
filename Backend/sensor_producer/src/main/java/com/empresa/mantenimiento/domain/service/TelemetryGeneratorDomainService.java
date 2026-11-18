package com.empresa.mantenimiento.domain.service;

import com.empresa.mantenimiento.shared.util.NumberRounderUtil;

import java.util.concurrent.ThreadLocalRandom;

public class TelemetryGeneratorDomainService {

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
