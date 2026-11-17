package com.empresa.mantenimiento.domain.model;

/**
 * Patrón State para modelar polimórficamente el estado operativo de una máquina industrial.
 */
public interface MachineOperationalState {

    String stateName();

    MachineOperationalState onEmergencyStop();

    MachineOperationalState onReset();
}
