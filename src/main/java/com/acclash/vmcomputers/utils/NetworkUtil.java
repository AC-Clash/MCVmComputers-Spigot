package com.acclash.vmcomputers.utils;

import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.lang.reflect.Field;

public class NetworkUtil {

    private static Field connectionField = null;

    // not public in spigot
    public static Connection getConnection(ServerGamePacketListenerImpl listener) {
        try {
            if (connectionField == null) {
                Field f = null;
                Class<?> clazz = ServerGamePacketListenerImpl.class;
                do {
                    for (Field check : clazz.getDeclaredFields()) {
                        if (check.getType().isAssignableFrom(Connection.class)) {
                            f = check;
                            break;
                        }
                    }
                } while (f == null && (clazz = clazz.getSuperclass()) != null);
                f.setAccessible(true);
                connectionField = f;
            }
            return (Connection) connectionField.get(listener);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Error while getting network connection", e);
        }
    }


}
