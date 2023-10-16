package jdos.misc;

import jdos.Dosbox;
import jdos.debug.Debug;

public class Log {
    static public final int level = 1;
    static public void exit(String msg) {
        System.out.print(msg);

        Debug.close();
        if (!Dosbox.applet)
            System.exit(0);
        else
            throw new RuntimeException("Exit");
    }
    static public void log(int type, int severity, String msg) {
        System.out.println(msg);
    }
}
