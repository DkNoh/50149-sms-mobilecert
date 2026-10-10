package scbk.cert.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

/** Capture actual Log4j bridge output, even though the default test logger is OFF. */
public final class LogCapture implements AutoCloseable {
    private final LoggerContext context = (LoggerContext) LogManager.getContext(false);
    private final Configuration configuration = context.getConfiguration();
    private final List<LogEvent> events = new CopyOnWriteArrayList<>();
    private final List<String> categories = new ArrayList<>();
    private final List<LoggerConfig> previous = new ArrayList<>();
    private final AbstractAppender appender = new AbstractAppender("SyntheticCapture", null,
            PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY) {
        @Override public void append(LogEvent event) { events.add(event.toImmutable()); }
    };

    public LogCapture(String... names) {
        appender.start();
        for (String category : names) {
            categories.add(category);
            previous.add(configuration.getLoggers().get(category));
            LoggerConfig logger = new LoggerConfig(category, Level.ALL, false);
            logger.addAppender(appender, Level.ALL, null);
            configuration.addLogger(category, logger);
        }
        context.updateLoggers();
    }

    public List<LogEvent> events() { return java.util.Collections.unmodifiableList(new ArrayList<>(events)); }

    public String text() {
        StringBuilder output = new StringBuilder();
        for (LogEvent event : events) output.append(event.getMessage().getFormattedMessage()).append('\n');
        return output.toString();
    }

    public void setLevel(String category, Level level) {
        configuration.getLoggerConfig(category).setLevel(level);
        context.updateLoggers();
    }

    @Override public void close() {
        for (int index = 0; index < categories.size(); index++) {
            configuration.removeLogger(categories.get(index));
            if (previous.get(index) != null) configuration.addLogger(categories.get(index), previous.get(index));
        }
        context.updateLoggers();
        appender.stop();
    }
}
