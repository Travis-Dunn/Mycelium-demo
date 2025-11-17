package com.intermet.mycelium;

import java.awt.*;

public class MyceliumColors {
    private static boolean init;

    public static Color tertiary =     new Color(0xF21616);
    public static Color secondary;
    public static Color primary;
    public static Color background;
    public static Color foreground;
    public static Color foregroundLight;
    public static Color backgroundLight;

    private static final Color seafoam = new Color(0x00cc9e);
    private static final Color plum = new Color(0x5c3045);
    private static final Color pearl = new Color(0xd5e3d3);
    private static final Color lignite = new Color(0x262124);
    private static final Color lightPearl = new Color(0xe5ece4);
    private static final Color lightLignite = new Color(0x332f31);

    public static boolean Init(boolean darkMode, boolean highContrast) {
        assert(!init);

        if (darkMode) {
            primary = plum;
            secondary = seafoam;
            foreground = pearl;
            background = lignite;
            foregroundLight = lightPearl;
            backgroundLight = lightLignite;
        } else {
            primary = seafoam;
            secondary = plum;
            foreground = lignite;
            background = pearl;
            foregroundLight = lightLignite;
            backgroundLight = lightPearl;
        }

        return init = true;
    }
}
