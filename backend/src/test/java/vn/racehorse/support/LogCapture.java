package vn.racehorse.support;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Collects what the application logs while a test runs, straight from the logging system, so a test that says "the code
 * is never logged" cannot pass just because another test changed where the console output goes.
 */
public final class LogCapture implements AutoCloseable {

    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    public LogCapture() {
        appender.start();
        root.addAppender(appender);
    }

    /** Every formatted message logged so far, with the text of any exception. */
    public List<String> messages() {
        return appender.list.stream().map(e -> e.getFormattedMessage()
                + (e.getThrowableProxy() == null ? "" : " " + e.getThrowableProxy().getMessage())).toList();
    }

    public String all() {
        return String.join("\n", messages());
    }

    @Override
    public void close() {
        root.detachAppender(appender);
        appender.stop();
    }
}
