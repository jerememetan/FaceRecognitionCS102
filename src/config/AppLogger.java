package config;

import java.io.IOException;
import java.io.PrintStream;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.logging.*;

public class AppLogger {

    // 1. Private Logger Instance
    private static final Logger logger = Logger.getLogger(AppLogger.class.getName());

    // Static block runs once when the class is first loaded
    static {
        try {
            // Stop the logger from printing to the console (default behavior)
            logger.setUseParentHandlers(false); 
            logger.setLevel(Level.INFO); // Set the default minimum logging level

       
            java.io.File logsDir = new java.io.File(".\\logs\\");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }

           
            String tsName = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                    .format(LocalDateTime.now()) + ".log";

            FileHandler fileHandler = new FileHandler(".\\logs\\" + tsName, false);
            
            fileHandler.setFormatter(new LogFormatter());
            logger.addHandler(fileHandler);
            logger.info("Application Logger Initialized.");

            System.setOut(new PrintStream(new LoggingOutputStream(logger, Level.INFO), true));
            System.setErr(new PrintStream(new LoggingOutputStream(logger, Level.SEVERE), true));

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not set up file logger!", e);
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
        logger.log(Level.INFO, message);
    }

    /** Logs a warning message. Used for non-critical issues or unexpected events. */
    public static void warn(String message) {
        logger.log(Level.WARNING, message);
    }

    /** Logs an error or severe message. Used for program failures or exceptions. */
    public static void error(String message) {
        logger.log(Level.SEVERE, message);
    }
    
    /** Logs an error along with an exception. */
    public static void error(String message, Throwable thrown) {
        logger.log(Level.SEVERE, message, thrown);
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







