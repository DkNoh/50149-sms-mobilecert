package scbk.cert.util;

/** Test-only no-op replacement for the production Log4j adapter. */
public final class Log {
    private Log() {
    }

    public static void write(String category, String message, Object reference) {
        // Baseline cases do not exercise logging; keep the harness dependency-free.
    }

    public static void debug(String category, String message, Object reference) { }
    public static void info(String category, String message, Object reference) { }
    public static void warn(String category, String message, Object reference) { }
    public static void warn(String category, String message, Throwable failure, Object reference) { }
    public static void error(String category, String message, Object reference) { }
    public static void error(String category, String message, Throwable failure, Object reference) { }
    public static String code(String value) {
        return value != null && value.matches("[A-Z0-9]{1,4}") ? value : "INVALID";
    }
}
