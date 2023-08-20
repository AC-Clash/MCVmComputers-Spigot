package jdos.win.system;

import jdos.gui.Main;
import jdos.util.Log;
import jdos.win.builtin.user32.Input;
import jdos.win.builtin.user32.WinWindow;

import java.awt.event.MouseEvent;

public class WinMouse {
    static public final Main.MouseHandler defaultMouseHandler = event -> {
        int msg = 0;
        int wParam = 0;
        WinPoint point = new WinPoint(event.getX(), event.getY());

        if (event.getID() == MouseEvent.MOUSE_MOVED || event.getID() == MouseEvent.MOUSE_DRAGGED) {
            msg = WinWindow.WM_MOUSEMOVE;
        } else if (event.getID() == MouseEvent.MOUSE_PRESSED) {
            if (event.getButton() == MouseEvent.BUTTON1) {
                msg = WinWindow.WM_LBUTTONDOWN;
            } else if (event.getButton() == MouseEvent.BUTTON2) {
                msg = WinWindow.WM_MBUTTONDOWN;
            } else if (event.getButton() == MouseEvent.BUTTON3) {
                msg = WinWindow.WM_RBUTTONDOWN;
            }
        } else if (event.getID() == MouseEvent.MOUSE_RELEASED) {
            if (event.getButton() == MouseEvent.BUTTON1) {
                msg = WinWindow.WM_LBUTTONUP;
            } else if (event.getButton() == MouseEvent.BUTTON2) {
                msg = WinWindow.WM_MBUTTONUP;
            } else if (event.getButton() == MouseEvent.BUTTON3) {
                msg = WinWindow.WM_RBUTTONUP;
            }
        }
        if (msg == 0) {
            Log.getLogger().warn("Unknown mouse message: "+ event);
            return;
        }
        StaticData.currentPos = point.copy();
        Input.addMouseMsg(msg, point, wParam);
    };
}
