package config;

import java.io.IOException;
import java.io.PrintStream;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.logging.*;

public class AppLogger {

    // 1. Use the root logger so that all java.util.logging records (from any package)
    //    are routed to our FileHandler and therefore into the same log file.
    private static final Logger rootLogger = Logger.getLogger("");

    // Static block runs once when the class is first loaded
    static {
        try {
            // Ensure the root logger does not keep any pre-existing handlers
            // (avoids duplicate output) and then configure it for our file output.
            for (Handler h : rootLogger.getHandlers()) {
                try {
                    rootLogger.removeHandler(h);
                    h.close();
                } catch (Exception ignored) {
                }
            }
            rootLogger.setUseParentHandlers(false);
            rootLogger.setLevel(Level.INFO); // Set the default minimum logging level

       
            java.io.File logsDir = new java.io.File(".\\logs\\");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }

           
            String tsName = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                    .format(LocalDateTime.now()) + ".log";

            FileHandler fileHandler = new FileHandler(".\\logs\\" + tsName, false);
            
            fileHandler.setFormatter(new LogFormatter());
            rootLogger.addHandler(fileHandler);
            rootLogger.info("Application Logger Initialized.");

            System.setOut(new PrintStream(new LoggingOutputStream(rootLogger, Level.INFO), true));
            System.setErr(new PrintStream(new LoggingOutputStream(rootLogger, Level.SEVERE), true));

        } catch (IOException e) {
            // If we can't configure the root logger, fallback to a class-specific logger
            Logger fallback = Logger.getLogger(AppLogger.class.getName());
            fallback.log(Level.SEVERE, "Could not set up file logger!", e);
        }
    }

    // --- Private Static Custom Formatter ---
    // Encapsulates the log format within the Facade class
    private static class LogFormatter extends Formatter {
        private static final DateTimeFormatter DATE_FORMAT = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        @Override
        public String format(LogRecord record) {
            // Build the desired log string format: [TIMESTAMP] LEVEL: MESSAGE
            StringBuilder builder = new StringBuilder();
            
            builder.append("[").append(DATE_FORMAT.format(LocalDateTime.now())).append("] ");
            builder.append(record.getLevel().getName()).append(": ");
            builder.append(formatMessage(record));
            builder.append("\n"); // Newline for the log file

            return builder.toString();
        }
    }

    // --- Public Facade Methods ---

    /** Logs an informational message. Used for successful events. */
    public static void info(String message) {
        rootLogger.log(Level.INFO, message);
    }

    /** Logs a warning message. Used for non-critical issues or unexpected events. */
    public static void warn(String message) {
        rootLogger.log(Level.WARNING, message);
    }

    /** Logs an error or severe message. Used for program failures or exceptions. */
    public static void error(String message) {
        rootLogger.log(Level.SEVERE, message);
    }
    
    /** Logs an error along with an exception. */
    public static void error(String message, Throwable thrown) {
        rootLogger.log(Level.SEVERE, message, thrown);
    }

    // --- Internal helper to redirect System.out/err into logger ---
    private static class LoggingOutputStream extends java.io.OutputStream {
        private final Logger targetLogger;
        private final Level level;
        private final StringBuilder buffer = new StringBuilder(256);

        LoggingOutputStream(Logger logger, Level level) {
            this.targetLogger = logger;
            this.level = level;
        }

        @Override
        public void write(int b) {
            char c = (char) b;
            if (c == '\n' || c == '\r') {
                flushBuffer();
            } else {
                buffer.append(c);
            }
        }

        @Override
        public void flush() {
            flushBuffer();
        }

        private void flushBuffer() {
            if (buffer.length() == 0) return;
            String msg = buffer.toString();
            buffer.setLength(0);
            targetLogger.log(level, msg);
        }
    }
}







