package com.empresa.mantenimiento.domain.model;

public interface MachineOperationalState {

    String stateName();

    MachineOperationalState onEmergencyStop();

    MachineOperationalState onReset();
}
