package jdos.util;

import org.apache.logging.log4j.Level;

public class IntRef {
    public IntRef(int value) {
        this.value = value;
    }
    public int value;
    public String toString() {
        Log.getLogger().log(Level.ERROR, "Ref error: ");
        return null;
    }
}
