package com.intermet.mycelium;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class MyceliumConfig {
    private static boolean init;

    private static final String CFG_FILE_NAME = "config.ini";
    private static final String CFG_COMMENT_CHAR = "#";
    private static final String CFG_ASSIGNMENT_CHAR = "=";

    private static final String CFG_STR_FULLSCREEN = "bFullscreen";
    private static final String CFG_STR_DARK_MODE = "bDarkMode";
    private static final String CFG_STR_HIGH_CONTRAST = "bHighContrast";

    private static final boolean DEF_FULLSCREEN = true;
    private static final boolean DEF_DARK_MODE = false;
    private static final boolean DEF_HIGH_CONTRAST = true;

    private static boolean FULLSCREEN;
    private static boolean DARK_MODE;
    private static boolean HIGH_CONTRAST;

    private MyceliumConfig() {}

    public static boolean Init() {
        assert(!init);

        FULLSCREEN = DEF_FULLSCREEN;
        DARK_MODE = DEF_DARK_MODE;
        HIGH_CONTRAST = DEF_HIGH_CONTRAST;

        ParseFile();

        return init = true;
    }

    /* Silently falls back to default behavior if an entry can't be parsed. */
    private static void ParseFile() {
        assert(init);

        String line, key, val = null;
        File f = new File(CFG_FILE_NAME);
        if (!f.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith(CFG_COMMENT_CHAR))
                    continue;

                int split = line.indexOf(CFG_ASSIGNMENT_CHAR);
                if (split == -1) continue;

                key = line.substring(0, split).trim();
                val = line.substring(split + 1);

                switch (key) {
                    case (CFG_STR_FULLSCREEN): {
                        FULLSCREEN = CoerceBool(val, DEF_FULLSCREEN);
                    } break;
                    case (CFG_STR_DARK_MODE): {
                        DARK_MODE = CoerceBool(val, DEF_DARK_MODE);
                    } break;
                    case (CFG_STR_HIGH_CONTRAST): {
                        HIGH_CONTRAST = CoerceBool(val, DEF_HIGH_CONTRAST);
                    } break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean CoerceBool(String boolStr, boolean def) {
        assert(init);

        if (boolStr == null || boolStr.isEmpty()) return def;

        switch (boolStr.trim().toLowerCase()) {
            case "true":
            case "1":
            case "yes":
            case "y":
            case "on":
                return true;

            case "false":
            case "0":
            case "no":
            case "n":
            case "off":
                return false;

            default:
                return def;
        }
    }

    public static boolean GetFullscreen() { return FULLSCREEN; }
    public static boolean GetDarkMode() { return DARK_MODE; }
    public static boolean GetHighContrast() { return HIGH_CONTRAST ; }
}
