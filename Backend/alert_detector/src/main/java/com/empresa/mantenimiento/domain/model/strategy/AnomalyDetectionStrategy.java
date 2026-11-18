package com.empresa.mantenimiento.domain.model.strategy;

import com.empresa.mantenimiento.domain.model.VibrationReadingRecord;

import java.util.List;

public interface AnomalyDetectionStrategy {

    String getStrategyName();

    DetectionResult evaluate(List<VibrationReadingRecord> readings);
}
