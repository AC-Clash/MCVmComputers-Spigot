package jdos.util;

import com.acclash.vmcomputers.VMComputers;
import jdos.Dosbox;
import jdos.debug.Debug;
import jdos.types.LogType;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class Log {
    private static final Logger logger = LogManager.getLogger(Log.class);

    public static Logger getLogger() {
        return logger;
    }

    static public final int level = 1;

    static public void exit(String msg, Level severity) {
        if (severity.equals(Level.ERROR)) {
            logger.error(msg);
        } else {
            logger.info(msg);
        }
        Debug.close();
        if (!Dosbox.applet) {
            //System.exit(0);
            VMComputers.getPlugin().getLogger().severe("The emulator has crashed. VMComputers is shutting down...");
            Bukkit.getPluginManager().disablePlugin(VMComputers.getPlugin());
        } else {
            logger.error("This is an applet. Unable to exit");
        }
    }

    static public void specializedLog(LogType logtype, Level severity, String msg) {
        if (severity.equals(Level.ERROR)) {
            logger.error("[" + logtype.getName() + "] " + msg);
        } else if (severity.equals(Level.WARN)) {
            logger.warn("[" + logtype.getName() + "] " + msg);
        } else {
            logger.info("[" + logtype.getName() + "] " + msg);
        }
    }
}
