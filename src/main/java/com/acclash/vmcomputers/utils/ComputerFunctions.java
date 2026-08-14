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

    /** Destroys one machine immediately, without waiting for the guest. */
    public static void kill(int computerId) {
        VirtualMachine machine = MACHINES.remove(Integer.valueOf(computerId));
        if (machine != null) {
            machine.kill();
        }
    }

    /** Stops everything; called on plugin disable so no QEMU process outlives the server. */
    public static void stopAll() {
        // Server shutdown must not hang waiting on guests, and a graceful stop can take ten
        // seconds each. The processes are being torn down regardless.
        for (Integer id : MACHINES.keySet()) {
            kill(id.intValue());
        }
    }
}
