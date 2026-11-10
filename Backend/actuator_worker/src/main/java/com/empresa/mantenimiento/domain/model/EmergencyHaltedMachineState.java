package com.empresa.mantenimiento.domain.model;

public final class EmergencyHaltedMachineState implements MachineOperationalState {

    @Override
    public String stateName() {
        return "EMERGENCY_HALTED";
    }

    @Override
    public MachineOperationalState onEmergencyStop() {
        return this;
    }
}
