package util;

import config.AppLogger;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * ModuleLoader - Comprehensive dependency loader for all external libraries
 * Handles loading of native libraries and JAR dependencies required by the application
 */
public class ModuleLoader {
    // OpenCV dependencies
    private static final String OPENCV_NATIVE_LIBRARY = "opencv_java480";
    private static final String OPENCV_DLL_RELATIVE_PATH = "lib/opencv_java480.dll";
    private static final boolean OPENCV_LOADED;

    // PostgreSQL JDBC driver
    private static final String POSTGRESQL_DRIVER = "org.postgresql.Driver";
    private static final boolean POSTGRESQL_LOADED;

    // Apache POI (Excel operations)
    private static final boolean POI_LOADED;

    // Logging frameworks
    private static final boolean LOGGING_LOADED;

    // Apache Commons libraries
    private static final boolean COMMONS_LOADED;

    // Overall loading status
    private static final boolean ALL_MODULES_LOADED;
    private static final List<String> LOAD_ERRORS = new ArrayList<>();

    static {
        boolean opencvLoaded = false;
        boolean postgresqlLoaded = false;
        boolean poiLoaded = false;
        boolean loggingLoaded = false;
        boolean commonsLoaded = false;

        // Load OpenCV native library
        try {
            System.loadLibrary(OPENCV_NATIVE_LIBRARY);
            opencvLoaded = true;
            AppLogger.info("OpenCV native library loaded successfully from system path");
        } catch (UnsatisfiedLinkError primaryError) {
            try {
                String absolutePath = new File(OPENCV_DLL_RELATIVE_PATH).getAbsolutePath();
                System.load(absolutePath);
                opencvLoaded = true;
                AppLogger.info("OpenCV native library loaded successfully from absolute path: " + absolutePath);
            } catch (UnsatisfiedLinkError fallbackError) {
                LOAD_ERRORS.add("OpenCV: " + primaryError.getMessage() + " | " + fallbackError.getMessage());
                AppLogger.error("Failed to load OpenCV from library path: " + primaryError.getMessage());
                AppLogger.error("Also failed to load OpenCV from absolute path: " + fallbackError.getMessage());
            }
        }

        // Load PostgreSQL JDBC driver
        try {
            Class.forName(POSTGRESQL_DRIVER);
            postgresqlLoaded = true;
            AppLogger.info("PostgreSQL JDBC driver loaded successfully");
        } catch (ClassNotFoundException e) {
            LOAD_ERRORS.add("PostgreSQL JDBC: " + e.getMessage());
            AppLogger.error("Failed to load PostgreSQL JDBC driver: " + e.getMessage());
        }

        // Load Apache POI classes
        try {
            Class.forName("org.apache.poi.ss.usermodel.Workbook");
            Class.forName("org.apache.poi.xssf.usermodel.XSSFWorkbook");
            poiLoaded = true;
            AppLogger.info("Apache POI libraries loaded successfully");
        } catch (ClassNotFoundException e) {
            LOAD_ERRORS.add("Apache POI: " + e.getMessage());
            AppLogger.error("Failed to load Apache POI libraries: " + e.getMessage());
        }

        // Load logging frameworks
        try {
            Class.forName("org.apache.logging.log4j.Logger");
            Class.forName("ch.qos.logback.classic.Logger");
            loggingLoaded = true;
            AppLogger.info("Logging frameworks loaded successfully");
        } catch (ClassNotFoundException e) {
            LOAD_ERRORS.add("Logging: " + e.getMessage());
            AppLogger.error("Failed to load logging frameworks: " + e.getMessage());
        }

        // Load Apache Commons libraries
        try {
            Class.forName("org.apache.commons.io.IOUtils");
            Class.forName("org.apache.commons.codec.binary.Base64");
            Class.forName("org.apache.commons.compress.archivers.zip.ZipArchiveEntry");
            commonsLoaded = true;
            AppLogger.info("Apache Commons libraries loaded successfully");
        } catch (ClassNotFoundException e) {
            LOAD_ERRORS.add("Apache Commons: " + e.getMessage());
            AppLogger.error("Failed to load Apache Commons libraries: " + e.getMessage());
        }

        OPENCV_LOADED = opencvLoaded;
        POSTGRESQL_LOADED = postgresqlLoaded;
        POI_LOADED = poiLoaded;
        LOGGING_LOADED = loggingLoaded;
        COMMONS_LOADED = commonsLoaded;

        ALL_MODULES_LOADED = opencvLoaded && postgresqlLoaded && poiLoaded &&
                           loggingLoaded && commonsLoaded;

        if (!ALL_MODULES_LOADED) {
            AppLogger.error("Some modules failed to load. Check LOAD_ERRORS for details.");
            for (String error : LOAD_ERRORS) {
                AppLogger.error("Module load error: " + error);
            }
        } else {
            AppLogger.info("All external modules loaded successfully");
        }
    }

    // OpenCV specific methods (backward compatibility)
    public static boolean isOpenCVLoaded() {
        return OPENCV_LOADED;
    }

    public static void ensureOpenCVLoaded() {
        if (!OPENCV_LOADED) {
            throw new RuntimeException("OpenCV native library is not loaded. Face detection features are unavailable.");
        }
    }

    // PostgreSQL specific methods
    public static boolean isPostgreSQLLoaded() {
        return POSTGRESQL_LOADED;
    }

    public static void ensurePostgreSQLLoaded() {
        if (!POSTGRESQL_LOADED) {
            throw new RuntimeException("PostgreSQL JDBC driver is not loaded. Database operations are unavailable.");
        }
    }

    // Apache POI specific methods
    public static boolean isPOILoaded() {
        return POI_LOADED;
    }

    public static void ensurePOILoaded() {
        if (!POI_LOADED) {
            throw new RuntimeException("Apache POI libraries are not loaded. Excel operations are unavailable.");
        }
    }

    // Logging specific methods
    public static boolean isLoggingLoaded() {
        return LOGGING_LOADED;
    }

    public static void ensureLoggingLoaded() {
        if (!LOGGING_LOADED) {
            throw new RuntimeException("Logging frameworks are not loaded. Logging features are unavailable.");
        }
    }

    // Apache Commons specific methods
    public static boolean isCommonsLoaded() {
        return COMMONS_LOADED;
    }

    public static void ensureCommonsLoaded() {
        if (!COMMONS_LOADED) {
            throw new RuntimeException("Apache Commons libraries are not loaded. Utility functions are unavailable.");
        }
    }

    // Overall status methods
    public static boolean areAllModulesLoaded() {
        return ALL_MODULES_LOADED;
    }

    public static List<String> getLoadErrors() {
        return new ArrayList<>(LOAD_ERRORS);
    }

    public static void ensureAllModulesLoaded() {
        if (!ALL_MODULES_LOADED) {
            StringBuilder errorMsg = new StringBuilder("Some required modules failed to load:\n");
            for (String error : LOAD_ERRORS) {
                errorMsg.append("- ").append(error).append("\n");
            }
            throw new RuntimeException(errorMsg.toString());
        }
    }

    /**
     * Get a summary of module loading status
     */
    public static String getModuleStatusSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Module Loading Status:\n");
        summary.append("OpenCV: ").append(OPENCV_LOADED ? "LOADED" : "FAILED").append("\n");
        summary.append("PostgreSQL JDBC: ").append(POSTGRESQL_LOADED ? "LOADED" : "FAILED").append("\n");
        summary.append("Apache POI: ").append(POI_LOADED ? "LOADED" : "FAILED").append("\n");
        summary.append("Logging Frameworks: ").append(LOGGING_LOADED ? "LOADED" : "FAILED").append("\n");
        summary.append("Apache Commons: ").append(COMMONS_LOADED ? "LOADED" : "FAILED").append("\n");

        if (!LOAD_ERRORS.isEmpty()) {
            summary.append("\nErrors:\n");
            for (String error : LOAD_ERRORS) {
                summary.append("- ").append(error).append("\n");
            }
        }

        return summary.toString();
    }
}






