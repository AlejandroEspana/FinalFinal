package com.empresa.mantenimiento.domain.model;

public final class RunningMachineState implements MachineOperationalState {

    @Override
    public String stateName() {
        return "RUNNING";
    }

    @Override
    public MachineOperationalState onEmergencyStop() {
        return new EmergencyHaltedMachineState();
    }

    @Override
    public MachineOperationalState onReset() {
        return this;
    }
}
