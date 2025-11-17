package com.intermet.mycelium;

import java.awt.*;

public class MyceliumFonts {
    // Base font - use system default for best cross-platform compatibility
    private static final String FONT_FAMILY = Font.SANS_SERIF; // Or "Segoe UI" for Windows

    // Font sizes
    public static final Font HEADER = new Font(FONT_FAMILY, Font.BOLD, 24);
    public static final Font SUBHEADER = new Font(FONT_FAMILY, Font.BOLD, 18);
    public static final Font BODY = new Font(FONT_FAMILY, Font.PLAIN, 17);
    public static final Font SMALL = new Font(FONT_FAMILY, Font.PLAIN, 14);
    public static final Font MONOSPACE = new Font("Monospaced", Font.PLAIN, 14); // For serial data
}
