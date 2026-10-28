package com.ionixx.Event_Ticket_Booking.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.LoggerFactory;

/**
 * Records log events together with a COPY of the MDC taken at log time. (A plain ListAppender reads
 * the MDC lazily, and RequestIdFilter clears it right after logging, so it would always look
 * empty.)
 */
public final class LogCapture extends AppenderBase<ILoggingEvent> {

  public static final String REQUEST_LOGGER =
      "com.ionixx.eventticketbooking.filter.RequestIdFilter";

  public record Entry(Level level, String message, Map<String, String> mdc, boolean hasThrowable) {}

  private final List<Entry> entries = new CopyOnWriteArrayList<>();
  private Logger logger;
  private Level previousLevel;

  public static LogCapture attach(String loggerName) {
    LogCapture capture = new LogCapture();
    capture.logger = (Logger) LoggerFactory.getLogger(loggerName);
    capture.previousLevel = capture.logger.getLevel();
    capture.logger.setLevel(Level.INFO);
    capture.setContext(capture.logger.getLoggerContext());
    capture.start();
    capture.logger.addAppender(capture);
    return capture;
  }

  public void detach() {
    logger.detachAppender(this);
    logger.setLevel(previousLevel);
    stop();
  }

  public List<Entry> entries() {
    return entries;
  }

  public void clear() {
    entries.clear();
  }

  @Override
  protected void append(ILoggingEvent e) {
    Map<String, String> mdc =
        e.getMDCPropertyMap() == null ? Map.of() : new HashMap<>(e.getMDCPropertyMap());
    entries.add(
        new Entry(e.getLevel(), e.getFormattedMessage(), mdc, e.getThrowableProxy() != null));
  }
}
