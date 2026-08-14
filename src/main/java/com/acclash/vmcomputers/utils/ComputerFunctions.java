package com.acclash.vmcomputers.utils;

import com.acclash.vmcomputers.emu.VirtualMachine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of the virtual machines currently running, keyed by computer id.
 *
 * <p>Concurrent because VMs are started and stopped from the server main thread but looked up from
 * the netty threads that handle input.
 */
public class ComputerFunctions {

    private static final Map<Integer, VirtualMachine> MACHINES =
            new ConcurrentHashMap<Integer, VirtualMachine>();

    public static Map<Integer, VirtualMachine> getMachines() {
        return MACHINES;
    }

    public static VirtualMachine get(int computerId) {
        return MACHINES.get(Integer.valueOf(computerId));
    }

    public static void register(VirtualMachine machine) {
        MACHINES.put(Integer.valueOf(machine.computerId()), machine);
    }

    /** Stops and forgets one machine. Safe to call when nothing is running. */
    public static void stop(int computerId) {
        VirtualMachine machine = MACHINES.remove(Integer.valueOf(computerId));
        if (machine != null) {
            machine.shutdown();
        }
    }

    /** Stops everything; called on plugin disable so no QEMU process outlives the server. */
    public static void stopAll() {
        for (Integer id : MACHINES.keySet()) {
            stop(id.intValue());
        }
    }
}
