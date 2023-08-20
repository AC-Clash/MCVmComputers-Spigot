package jdos.util;

import org.apache.logging.log4j.Level;

public class StringRef {
    public StringRef() {}
    public StringRef(String value) {
        this.value = value;
    }
    public String value;
    public String toString() {
        Log.getLogger().log(Level.ERROR, "Ref error: ");
        return null;
    }
}
