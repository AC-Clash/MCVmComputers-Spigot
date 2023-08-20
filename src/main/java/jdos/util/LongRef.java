package jdos.util;

import org.apache.logging.log4j.Level;

public class LongRef {
    public LongRef(long value) {
        this.value = value;
    }
    public long value;
    public String toString() {
        Log.getLogger().log(Level.ERROR, "Ref error: ");
        return null;
    }
}
