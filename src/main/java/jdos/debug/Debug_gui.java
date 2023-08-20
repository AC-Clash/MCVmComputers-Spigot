package jdos.debug;

import jdos.util.Log;
import jdos.misc.setup.Section;
import jdos.misc.setup.Section_prop;
import jdos.types.LogType;
import org.apache.logging.log4j.Level;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.Map;

public class Debug_gui {
    private static class _LogGroup {
        String front;
        boolean enabled;
    }

    private static final Map<LogType, _LogGroup> logGroups = new EnumMap<>(LogType.class);
    private static OutputStream debuglog;

    private static final Section.SectionFunction LOG_Destroy = section -> {
        try {
            if (debuglog != null) {
                debuglog.close();
            }
        } catch (IOException e) {
            Log.getLogger().log(Level.ERROR, "Runtime error: ", e);
        }
    };

    private static final Section.SectionFunction LOG_Init = section -> {
        Section_prop sect = (Section_prop) section;
        String logFilePath = sect.Get_string("logfile");
        if (logFilePath != null && !logFilePath.isEmpty()) {
            try {
                debuglog = Files.newOutputStream(Paths.get(logFilePath));
            } catch (IOException e) {
                Log.getLogger().log(Level.ERROR, "Could not init specializedLog: ", e);
            }
        }
        sect.AddDestroyFunction(LOG_Destroy);
        for (LogType logType : LogType.values()) {
            _LogGroup logGroup = logGroups.get(logType);
            if (logGroup != null) {
                logGroup.enabled = sect.Get_bool(logType.getName().toLowerCase());
            }
        }
    };

    public static void LOG_StartUp() {
        for (LogType logType : LogType.values()) {
            _LogGroup logGroup = new _LogGroup();
            logGroup.front = logType.getName();
            logGroups.put(logType, logGroup);
        }

        /* Register the specializedLog section */
//        Section_prop sect= Dosbox.control.AddSection_prop("specializedLog",LOG_Init);
//        Prop_string Pstring = sect.Add_string("logfile", Property.Changeable.Always,"");
//        Pstring.Set_help("file where the specializedLog messages will be saved to");
//        for (int i=1;i<LogType.LOG_MAX;i++) {
//            Prop_bool Pbool = sect.Add_bool(loggrp[i].front.toLowerCase(),Property.Changeable.Always,true);
//            Pbool.Set_help("Enable/Disable logging of this type.");
//        }
//        Msg.add("LOG_CONFIGFILE_HELP","Logging related options for the debugger.\n");
    }
}
