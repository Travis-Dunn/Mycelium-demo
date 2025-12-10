package temp;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public final class ErrorHandler {
    private static boolean init;

    private static List<Runnable> shutdownCallbacks;
    private static volatile boolean shutdownInitiated;

    private ErrorHandler() {}

    public static boolean Init() {
        assert(!init);

        shutdownInitiated = false;

        try {
            shutdownCallbacks = new ArrayList<>();
        } catch (OutOfMemoryError e) {
            AttemptToNotifyUserOfOOMAndExit(e);
            return init = false;
        }

        return init = true;
    }

    public static void registerShutdownCallback(Runnable callback) {
        assert(init);

        synchronized (shutdownCallbacks) {
            shutdownCallbacks.add(callback);
        }
    }

    private static void performOrderlyShutdown() {
        assert(init);

        int i;

        if (shutdownInitiated) {
            return;
        }
        shutdownInitiated = true;

        synchronized (shutdownCallbacks) {
            for (i = shutdownCallbacks.size() - 1; i >= 0; --i) {
                try {
                    shutdownCallbacks.get(i).run();
                } catch (Exception e) {
                    Logger.LogFatal("Cleanup handler failed.\n" + e.getMessage(), e);
                }
            }
        }
    }

    public static void AttemptToNotifyUserOfOOMAndExit(Throwable t) {
        /* Attempt to show error box. */
        try {
            JOptionPane.showMessageDialog(null,
                    "Out of Memory - program must exit", "Fatal Error",
                    JOptionPane.ERROR_MESSAGE);
        } catch (Throwable ignored) {}

        /* Printing to console is the only thing that has a decent chance of
        working. */
        System.err.println("Fatal Error: Out of Memory");
        if (t != null) {
            t.printStackTrace(System.err);
        }

        System.exit(1);
    }

    public static void LogFatalAndExit(String s) {
        JOptionPane.showMessageDialog(null,
                s, "Fatal Error",
                JOptionPane.ERROR_MESSAGE);
        Logger.LogFatal(s, null);

        performOrderlyShutdown();
        System.exit(1);
    }

    public static void LogFatalExcpAndExit(String s, Throwable t) {
        JOptionPane.showMessageDialog(null,
                s + "\nSee session.log for stack trace.", "Fatal Error",
                JOptionPane.ERROR_MESSAGE);
        Logger.LogFatal(s, t);

        performOrderlyShutdown();
        System.exit(1);
    }

    public static final String CLASS = ErrorHandler.class.getSimpleName();
}
