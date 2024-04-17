package com.acclash.vmcomputers.utils;

import jdos.gui.MainFrame;

import java.util.HashMap;

public class ComputerFunctions {

    static HashMap<Integer, Integer> taskMap = new HashMap<>();

    static HashMap<Integer, MainFrame> frameMap = new HashMap<>();

    public static HashMap<Integer, MainFrame> getFrameMap() {
        return frameMap;
    }


}
