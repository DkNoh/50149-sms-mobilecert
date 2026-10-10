/*
 * Project Name	:   휴대폰 본인 인증 
 * File Name	:	Log.java
 * Date			:	2012-03-01 
 * Version		:	1.0
 * Author		:	TrustMate 02-3446-3730 
 * Comment      :   Log4j 를 이용한 로그 설정 .
 */

package scbk.cert.util;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/** Logging only: no authentication decision, protocol field or transaction changes. */
public class Log {
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<String>();
    private static final AtomicLong REQUEST_SEQUENCE = new AtomicLong();
    private static final String PROCESS_ID = UUID.randomUUID().toString().replace("-", "");
    private static final AtomicBoolean LOG_FAILURE_REPORTED = new AtomicBoolean();
    private static final Pattern RESULT_CODE = Pattern.compile("[A-Z0-9]{1,4}");
    private static final Pattern SQL_STATE = Pattern.compile("[A-Z0-9]{5}");
    private static final Pattern SYMBOL = Pattern.compile("[A-Za-z0-9_.$<>]{1,200}");
    private static final int MAX_MESSAGE_LENGTH = 512;
    private static final int MAX_EXCEPTIONS = 4;
    private static final int MAX_FRAMES = 6;

    private Log() { }

    /** Compatibility entry point for existing manual tools/charset tests. */
    public static void write(String category, String msg, Object ref) {
        // Retain the historical level and exact text. Runtime service call sites
        // use the explicit-level API below and never supply raw business data.
        try {
            logger(category).error(msg, null);
        } catch (RuntimeException failure) {
            loggingFailed();
        }
    }

    /** Call once at request entry; the ID is never placed in a business packet. */
    public static String beginRequest() {
        String id = PROCESS_ID + "-" + Long.toUnsignedString(REQUEST_SEQUENCE.incrementAndGet(), 36);
        REQUEST_ID.set(id);
        return id;
    }

    /** Always call in the request boundary's finally block. */
    public static void endRequest() {
        REQUEST_ID.remove();
    }

    /** Only short provider result/mode codes belong here; not identifiers or PII. */
    public static String code(String value) {
        return value != null && RESULT_CODE.matcher(value).matches() ? value : "INVALID";
    }

    public static void debug(String category, String message, Object ref) {
        publish(Level.DEBUG, category, message, null);
    }

    public static void info(String category, String message, Object ref) {
        publish(Level.INFO, category, message, null);
    }

    public static void warn(String category, String message, Object ref) {
        publish(Level.WARN, category, message, null);
    }

    public static void warn(String category, String message, Throwable failure, Object ref) {
        publish(Level.WARN, category, message, failure);
    }

    public static void error(String category, String message, Object ref) {
        publish(Level.ERROR, category, message, null);
    }

    public static void error(String category, String message, Throwable failure, Object ref) {
        publish(Level.ERROR, category, message, failure);
    }

    private static Logger logger(String category) {
        return category == null ? LogManager.getRootLogger() : LogManager.getLogger(category);
    }

    private static void publish(Level level, String category, String message, Throwable failure) {
        try {
            Logger target = logger(category);
            if (!target.isEnabledFor(level)) return;
            // Callers supply fixed events and numeric/whitelisted values only.
            // This line bounding is not a general-purpose PII masking function.
            StringBuilder output = new StringBuilder(singleLine(message));
            String id = REQUEST_ID.get();
            if (id != null) output.append(" requestId=").append(id);
            if (failure != null) appendFailure(output, failure);
            // Never hand the original Throwable to the logging framework: its
            // message, causes and suppressed exceptions can contain secrets.
            target.log(level, output.toString());
        } catch (RuntimeException failureInLogging) {
            // A broken appender/formatter must not change the authentication flow.
            loggingFailed();
        }
    }

    private static String singleLine(String value) {
        if (value == null) return "event=UNSPECIFIED";
        StringBuilder output = new StringBuilder(Math.min(value.length(), MAX_MESSAGE_LENGTH));
        for (int index = 0; index < value.length() && index < MAX_MESSAGE_LENGTH; index++) {
            char ch = value.charAt(index);
            output.append(Character.isISOControl(ch) || ch == '\u2028' || ch == '\u2029' ? '_' : ch);
        }
        return output.toString();
    }

    private static String symbol(String value) {
        return value != null && SYMBOL.matcher(value).matches() ? value : "UNKNOWN";
    }

    private static void appendFailure(StringBuilder output, Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        ArrayDeque<Throwable> pending = new ArrayDeque<Throwable>();
        pending.add(failure);
        int count = 0;
        while (!pending.isEmpty() && count < MAX_EXCEPTIONS) {
            Throwable item = pending.removeFirst();
            if (!visited.add(item)) continue;
            String suffix = count == 0 ? "" : Integer.toString(count);
            output.append(" exceptionType").append(suffix).append('=').append(symbol(item.getClass().getName()));
            if (item instanceof SQLException) {
                SQLException sql = (SQLException) item;
                String state = sql.getSQLState();
                output.append(" sqlState").append(suffix).append('=')
                        .append(state != null && SQL_STATE.matcher(state).matches() ? state : "UNKNOWN");
                output.append(" vendorCode").append(suffix).append('=').append(sql.getErrorCode());
                if (sql.getNextException() != null && pending.size() < MAX_EXCEPTIONS) pending.addLast(sql.getNextException());
            }
            StackTraceElement[] frames = item.getStackTrace();
            for (int index = 0; index < frames.length && index < MAX_FRAMES; index++) {
                StackTraceElement frame = frames[index];
                output.append(" at").append(suffix).append('_').append(index).append('=')
                        .append(symbol(frame.getClassName())).append('.')
                        .append(symbol(frame.getMethodName())).append(':').append(frame.getLineNumber());
            }
            Throwable cause = item.getCause();
            if (cause != null && pending.size() < MAX_EXCEPTIONS) pending.addLast(cause);
            Throwable[] suppressed = item.getSuppressed();
            for (int index = 0; index < suppressed.length && pending.size() < MAX_EXCEPTIONS; index++) {
                pending.addLast(suppressed[index]);
            }
            count++;
        }
    }

    private static void loggingFailed() {
        // Fixed text, once per JVM. No original message or exception is printed.
        if (LOG_FAILURE_REPORTED.compareAndSet(false, true)) {
            System.err.println("event=LOGGING_FAILED diagnostics=CHECK_APPENDER");
        }
    }
}
