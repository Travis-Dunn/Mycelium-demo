package temp;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class Logger {
    private static final String PERSISTENT_FILENAME = "persistent.log";
    private static final String FATAL_FILENAME      = "fatal.log";
    private static final String SESSION_FILENAME    = "session.log";

    private static PrintWriter persistentWriter;
    private static PrintWriter fatalWriter;
    private static PrintWriter sessionWriter;

    private static boolean init;

    private static final SimpleDateFormat sdf =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private Logger() {}

    public static boolean Init() {
        try {
            persistentWriter = new PrintWriter(new BufferedWriter(
                    new FileWriter(PERSISTENT_FILENAME, true)));
            fatalWriter = new PrintWriter(new BufferedWriter(
                    new FileWriter(FATAL_FILENAME, true)));
            sessionWriter = new PrintWriter(new BufferedWriter(
                    new FileWriter(SESSION_FILENAME, false)));
            return init = true;
        } catch (IOException e) {
            System.err.println("Error creating logger" + e.getMessage());
            return init = false;
        }
    }

    public static void Shutdown() {
        assert(init);

        persistentWriter.close();
        fatalWriter.close();
        sessionWriter.close();
    }

    public static void LogPersistent(LogLevel l, String msg) {
        Log(persistentWriter, l.toString(), msg, null);
    }

    public static void LogFatal(String msg, Throwable t) {
        Log(fatalWriter, LogLevel.FATAL.toString(), msg, t);
    }

    public static void LogSession(LogLevel l, String msg) {
        Log(sessionWriter, l.toString(), msg, null);
    }

    public static void LogSessionExcp(LogLevel l, String msg, Throwable t) {
        Log(sessionWriter, l.toString(), msg, t);
    }

    public static void LogSessionNoTime(String msg) {
        LogNoTime(sessionWriter, msg, null);
    }

    private static void Log(PrintWriter writer, String level, String msg,
                            Throwable t) {
        assert(init);

        String time = sdf.format(new Date());
        writer.println("[" + time + "][" + level + "] " + msg);

        if (t != null) t.printStackTrace(writer);

        writer.flush();
    }

    private static void LogNoTime(PrintWriter writer, String msg,
                                  Throwable t) {
        assert(init);

        writer.println(msg);

        if (t != null) t.printStackTrace(writer);

        writer.flush();
    }
}

