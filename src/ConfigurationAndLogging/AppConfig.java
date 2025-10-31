package ConfigurationAndLogging;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class AppConfig {
    // Settings
    public final static String KEY_DATABASE_STORAGE_PATH = "database.storage_path";
    public final static String KEY_CASCADE_PATH = "cascade.path";
    public final static String KEY_LOG_FILE_NAME = "log.file.name";
    public final static String KEY_CAMERA_INDEX = "camera.index";
    public final static String KEY_DETECTION_SCALE_FACTOR = "detection.scale_factor";
    public final static String KEY_DETECTION_MIN_NEIGHBORS = "detection.min.neighbors";
    public final static String KEY_DETECTION_MIN_SIZE_PX = "detection.min_size_px";
    public final static String KEY_RECOGNITION_THRESHOLD = "recognition.threshold";
    public final static String KEY_RECOGNITION_IMAGE_FORMAT = "recognition.image_format";

    // constants used in methods
    public final static int KEY_RECOGNITION_CROP_SIZE_PX = 200;
    public final static int KEY_PREPROCESSING_GAUSSIAN_KERNEL_SIZE = 5;
    public final static int KEY_PREPROCESSING_GAUSSIAN_SIGMA_X = 0;
    public final static double KEY_PREPROCESSING_CLAHE_CLIP_LIMIT = 2.0;
    public final static int KEY_PREPROCESSING_CLAHE_GRID_SIZE = 8;

    // --- Added/missing keys (restored from app.properties) ---
    // capture.*
    public final static String KEY_CAPTURE_MIN_CONFIDENCE_SCORE = "capture.min_confidence_score";
    public final static String KEY_CAPTURE_MIN_FACE_SIZE = "capture.min_face_size";
    public final static String KEY_CAPTURE_INTERVAL_MS = "capture.capture_interval_ms";
    public final static String KEY_CAPTURE_ATTEMPT_MULTIPLIER = "capture.capture_attempt_mutliplier";
    public final static String KEY_CAPTURE_FRAME_WAIT_TIMEOUT_MS = "capture.frame_wait_timeout_ms";
    public final static String KEY_CAPTURE_FACE_PERSISTENCE_NS = "capture.face_persistence_ns";

    // embedding.*
    public final static String KEY_EMBEDDING_MODEL_PATH = "embedding.model_path";
    public final static String KEY_EMBEDDING_SIZE = "embedding.embedding.size";
    public final static String KEY_EMBEDDING_INPUT_SIZE = "embedding.input_size";

    // database.*
    public final static String KEY_DATABASE_URL = "database.URL";
    public final static String KEY_DATABASE_USER = "database.user";
    public final static String KEY_DATABASE_PASSWORD = "database.password";

    // detection model files (alternate keys present in app.properties)
    public final static String KEY_DETECTION_MODEL_CONFIG = "detection.model_configuration_path";
    public final static String KEY_DETECTION_MODEL_WEIGHTS = "detection.model_weights";

    // dnn keys (detection.dnn.*)
    public final static String KEY_DNN_ENABLED = "detection.dnn.enabled";
    public final static String KEY_DNN_MODEL_PATH = "detection.dnn.model_path";
    public final static String KEY_DNN_CONFIG_PATH = "detection.dnn.config_path";
    public final static String KEY_DNN_CONFIDENCE = "detection.dnn.confidence";

    // preprocessing thresholds
    public final static String KEY_PREPROCESSING_MIN_SHARPNESS_THRESHOLD = "preprocessing.min_sharpness_threshold";
    public final static String KEY_PREPROCESSING_MIN_BRIGHTNESS = "preprocessing.min_brightness";
    public final static String KEY_PREPROCESSING_MAX_BRIGHTNESS = "preprocessing.max_brightness";
    public final static String KEY_PREPROCESSING_MIN_CONTRAST = "preprocessing.min_contrast";

    // export folders
    public final static String KEY_EXPORT_CSV_FOLDER = "export.csv_exported_folder_path";
    public final static String KEY_EXPORT_EXCEL_FOLDER = "export.excel_exported_folder_path";
    public final static String KEY_EXPORT_PDF_FOLDER = "export.pdf_exported_folder_path";

    // recognition detailed keys
    public final static String KEY_RECOGNITION_TOP_K = "recognition.top_k";
    public final static String KEY_RECOGNITION_MARGIN_DEEP = "recognition.margin.deep";
    public final static String KEY_RECOGNITION_MARGIN_FALLBACK = "recognition.margin.fallback";
    public final static String KEY_RECOGNITION_SOFT_THRESHOLD = "recognition.soft_threshold";
    public final static String KEY_RECOGNITION_HIGH_THRESHOLD = "recognition.high_threshold";
    public final static String KEY_RECOGNITION_CONSISTENCY_WINDOW = "recognition.consistency.window";
    public final static String KEY_RECOGNITION_CONSISTENCY_MIN_COUNT = "recognition.consistency.min_count";
    public final static String KEY_RECOGNITION_COHORT_ENABLED = "recognition.cohort.enabled";
    public final static String KEY_RECOGNITION_COHORT_SIZE = "recognition.cohort.size";
    public final static String KEY_RECOGNITION_COHORT_Z_MIN = "recognition.cohort.z_min";
    public final static String KEY_RECOGNITION_MIN_FACE_WIDTH_PX = "recognition.min.face.width.px";

    // pruning / person thresholds
    public final static String KEY_PRUNING_ENABLED = "recognition.pruning.enabled";
    public final static String KEY_PRUNING_STD_FACTOR = "recognition.pruning.std_factor";
    public final static String KEY_PRUNING_MIN_KEEP = "recognition.pruning.min_keep";
    public final static String KEY_PERSON_THRESHOLDS_ENABLED = "recognition.thresholds.person.enabled";
    public final static String KEY_PERSON_THRESHOLDS_BETA = "recognition.thresholds.person.beta";
    // --- end added keys ---

    // --- Singleton Implementation ---
    private static AppConfig instance = null;
    private final Properties properties = new Properties();

    // 1. Private Constructor: Prevents outside classes from calling 'new
    // AppConfig()'
    private AppConfig() {
        // We call the loading logic inside the constructor
        this.load();
    }

    // 2. Public Static Access Method: The only way to get the instance
    public static AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    private void load() {
        InputStream input = null;
        try {
            input = getClass().getClassLoader().getResourceAsStream("app.properties");
            if (input != null) {
                properties.load(input);
                normalizeProperties();
                AppLogger.info("Configuration loaded from classpath: app.properties");
                return;
            }
            java.nio.file.Path p = java.nio.file.Paths.get("app.properties").toAbsolutePath().normalize();
            if (java.nio.file.Files.exists(p)) {
                try (InputStream fsIn = java.nio.file.Files.newInputStream(p)) {
                    properties.load(fsIn);
                    normalizeProperties();
                    AppLogger.info("Configuration loaded from file: " + p);
                    return;
                }
            }

            AppLogger.error("Sorry, unable to find app.properties (classpath or " +
                    java.nio.file.Paths.get("app.properties").toAbsolutePath().normalize() + ")");
        } catch (IOException ex) {
            AppLogger.error("Error reading app.properties file.", ex);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Clean up loaded properties:
     * - Trim keys and values
     * - Remove trailing semicolons from values (e.g. "50;")
     * - Map known alias keys to canonical KEY_* names used by AppConfig
     */
    private void normalizeProperties() {
        java.util.Map<String, String> aliases = new java.util.HashMap<>();
        aliases.put("embedding.modelPath", KEY_EMBEDDING_MODEL_PATH);
        aliases.put("detection.min_neighbors", KEY_DETECTION_MIN_NEIGHBORS);
        // keep detection.dnn.* keys already match KEY_DNN_*
        // add any other aliases you encounter:
        // aliases.put("some.alias", KEY_WHATEVER);

        Properties cleaned = new Properties();

        for (java.util.Map.Entry<Object, Object> e : properties.entrySet()) {
            String rawKey = String.valueOf(e.getKey());
            String rawVal = String.valueOf(e.getValue());

            String key = rawKey.trim();
            String val = rawVal.trim();

            // Remove trailing semicolons that appear in your properties file
            if (val.endsWith(";")) {
                val = val.substring(0, val.length() - 1).trim();
            }

            // Map aliases -> canonical key names
            if (aliases.containsKey(key)) {
                key = aliases.get(key);
            }

            // If the cleaned key already exists we overwrite with the latest (last wins).
            cleaned.setProperty(key, val);
        }

        // Replace original properties with cleaned version
        properties.clear();
        properties.putAll(cleaned);

        AppLogger.info("app.properties normalized (" + properties.size() + " entries).");
    }

    public void save() {
        try (FileOutputStream output = new FileOutputStream("app.properties")) {
            properties.store(output, "Configuration saved by user at runtime.");
            AppLogger.info("Configuration saved to file.");
        } catch (IOException ex) {
            AppLogger.error("Failed to save configuration to file!", ex);
        }
    }

    // KEY_DATABASE_STORAGE_PATH = "database.storage_path";
    public String getDatabaseStoragePath() {
        String configured = properties.getProperty(KEY_DATABASE_STORAGE_PATH, "data/facedata");
        Path normalized = Paths.get(configured).toAbsolutePath().normalize();
        return normalized.toString();
    }

    public void setDatabaseStoragePath(String Path) {
        if (Path == null) {
            AppLogger.error("Failed to change " + KEY_DATABASE_STORAGE_PATH + ".Path is null");
        } else {
            Path normalized = Paths.get(Path).toAbsolutePath().normalize();
            this.properties.setProperty(KEY_DATABASE_STORAGE_PATH, normalized.toString());
            AppLogger.info(KEY_DATABASE_STORAGE_PATH + " has been changed to " + normalized);
        }

    }

    // KEY_CASCADE_PATH = "cascade.path";
    public String getCascadePath() {
        return properties.getProperty(KEY_CASCADE_PATH, "./opencv-cascade-classifier/haarcascade_frontalface_alt.xml");
    }

    public void setCascadePath(String Path) {
        if (Path == null) {
            AppLogger.error("Failed to change " + KEY_CASCADE_PATH + ".Path is null");
        } else {
            this.properties.setProperty(KEY_CASCADE_PATH, Path);
            AppLogger.info(KEY_CASCADE_PATH + " has been changed to " + Path);
        }

    }

    // KEY_LOG_FILE_NAME = "log.file.name";
    public String getLogFileName() {
        return properties.getProperty(KEY_LOG_FILE_NAME, "attendance.log");
    }

    public void setLogFileName(String Path) {
        if (Path == null) {
            AppLogger.error("Failed to change " + KEY_LOG_FILE_NAME + ".Name is null");
        } else {
            this.properties.setProperty(KEY_LOG_FILE_NAME, Path);
            AppLogger.info(KEY_LOG_FILE_NAME + " has been changed to " + Path);
        }

    }

    // KEY_CAMERA_INDEX = "camera.index";
    public int getCameraIndex() {
        String indexStr = properties.getProperty(KEY_CAMERA_INDEX, "0"); // Default value "0"
        try {
            return Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            AppLogger.error("Config error: Invalid number format for " + KEY_CAMERA_INDEX, e);
            return 0; // Return safe default on failure
        }
    }

    public void setCameraIndex(int newIndex) {
        // 1. Convert the integer back to a String
        if (newIndex < 0) {
            AppLogger.error("Failed to change " + KEY_CAMERA_INDEX + ".Index is null");
        } else {
            String newIndexStr = String.valueOf(newIndex);
            // 2. Use the final KEY to set the new value in the mutable properties object
            this.properties.setProperty(KEY_CAMERA_INDEX, newIndexStr);

            AppLogger.info(KEY_CAMERA_INDEX + " has been changed to " + newIndex);
        }
    }

    // KEY_DETECTION_SCALE_FACTOR = "detection.scale_factor";
    public double getDetectionScaleFactor() {
        String indexStr = properties.getProperty(KEY_DETECTION_SCALE_FACTOR, "1.05"); // Default value "0"
        try {
            return Double.parseDouble(indexStr);
        } catch (NumberFormatException e) {
            AppLogger.error("Config error: Invalid number format for " + KEY_DETECTION_SCALE_FACTOR, e);
            return 1.05; // Return safe default on failure
        }
    }

    public void setDetectionScaleFactor(Double newIndex) {
        // 1. Convert the integer back to a String
        if (newIndex == null) {
            AppLogger.error("Failed to change " + KEY_DETECTION_SCALE_FACTOR + ".Index is null");
        } else {
            String newIndexStr = String.valueOf(newIndex);
            // 2. Use the final KEY to set the new value in the mutable properties object
            this.properties.setProperty(KEY_DETECTION_SCALE_FACTOR, newIndexStr);

            AppLogger.info(KEY_DETECTION_SCALE_FACTOR + " has been changed to " + newIndex);
        }
    }

    // KEY_DETECTION_MIN_NEIGHBORS = "detection.min.neighbors";
    public int getDetectionMinNeighbors() {
        String indexStr = properties.getProperty(KEY_DETECTION_MIN_NEIGHBORS, "5"); // Default value "0"
        try {
            return Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            AppLogger.error("Config error: Invalid number format for " + KEY_DETECTION_MIN_NEIGHBORS, e);
            return 5; // Return safe default on failure
        }
    }

    public void setDetectionMinNeighbors(int newIndex) {
        // 1. Convert the integer back to a String
        if (newIndex < 0) {
            AppLogger.error("Failed to change " + KEY_DETECTION_MIN_NEIGHBORS + ".Index is null");
        } else {
            String newIndexStr = String.valueOf(newIndex);
            // 2. Use the final KEY to set the new value in the mutable properties object
            this.properties.setProperty(KEY_DETECTION_MIN_NEIGHBORS, newIndexStr);

            AppLogger.info(KEY_DETECTION_MIN_NEIGHBORS + " has been changed to " + newIndex);
        }
    }

    // KEY_DETECTION_MIN_SIZE_PX= "detection.min_size_px";
    public int getDetectionMinSize() {
        String indexStr = properties.getProperty(KEY_DETECTION_MIN_SIZE_PX, "80");
        try {
            return Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            AppLogger.error("Config error: Invalid number format for " + KEY_DETECTION_MIN_SIZE_PX, e);
            return 80; // Return safe default on failure
        }
    }

    public void setDetectionMinSize(int newIndex) {
        // 1. Convert the integer back to a String
        if (newIndex < 0) {
            AppLogger.error("Failed to change " + KEY_DETECTION_MIN_SIZE_PX + ".Index is null");
        } else {
            String newIndexStr = String.valueOf(newIndex);
            // 2. Use the final KEY to set the new value in the mutable properties object
            this.properties.setProperty(KEY_DETECTION_MIN_SIZE_PX, newIndexStr);

            AppLogger.info(KEY_DETECTION_MIN_SIZE_PX + " has been changed to " + newIndex);
        }
    }

    // KEY_RECOGNITION_THRESHOLD = "recognition.threshold";
    public double getRecognitionThreshold() {
        String indexStr = properties.getProperty(KEY_RECOGNITION_THRESHOLD, "0.7"); // Default value "0"
        try {
            return Double.parseDouble(indexStr);
        } catch (NumberFormatException e) {
            AppLogger.error("Config error: Invalid number format for " + KEY_RECOGNITION_THRESHOLD, e);
            return 0.70; // Return safe default on failure
        }
    }

    public void setRecognitionThreshold(Double newIndex) {
        // 1. Convert the integer back to a String
        if (newIndex == null) {
            AppLogger.error("Failed to change " + KEY_RECOGNITION_THRESHOLD + ".Index is null");
        } else {
            String newIndexStr = String.valueOf(newIndex);
            // 2. Use the final KEY to set the new value in the mutable properties object
            this.properties.setProperty(KEY_RECOGNITION_THRESHOLD, newIndexStr);

            AppLogger.info(KEY_RECOGNITION_THRESHOLD + " has been changed to " + newIndex);
        }
    }
    public String getRecognitionImageFormat() {
        return properties.getProperty(KEY_RECOGNITION_IMAGE_FORMAT, "jpg");
    }

    public void setRecognitionImageFormat(String Path) {
        if (Path == null) {
            AppLogger.error("Failed to change " + KEY_RECOGNITION_IMAGE_FORMAT + ".Name is null");
        } else {
            this.properties.setProperty(KEY_RECOGNITION_IMAGE_FORMAT, Path);
            AppLogger.info(KEY_RECOGNITION_IMAGE_FORMAT + " has been changed to " + Path);
        }

    }

    // capture.min_confidence_score (double)
    public double getCaptureMinConfidenceScore() {
        String s = properties.getProperty(KEY_CAPTURE_MIN_CONFIDENCE_SCORE, "0.5");
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: Invalid number format for " + KEY_CAPTURE_MIN_CONFIDENCE_SCORE, ex);
            return 0.5;
        }
    }

    public void setCaptureMinConfidenceScore(Double value) {
        if (value == null) {
            AppLogger.error("Failed to change " + KEY_CAPTURE_MIN_CONFIDENCE_SCORE + ".Value is null");
            return;
        }
        properties.setProperty(KEY_CAPTURE_MIN_CONFIDENCE_SCORE, String.valueOf(value));
        AppLogger.info(KEY_CAPTURE_MIN_CONFIDENCE_SCORE + " has been changed to " + value);
    }

    // capture.min_face_size (int)
    public int getCaptureMinFaceSize() {
        String s = properties.getProperty(KEY_CAPTURE_MIN_FACE_SIZE, "50");
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: Invalid number format for " + KEY_CAPTURE_MIN_FACE_SIZE, ex);
            return 50;
        }
    }

    public void setCaptureMinFaceSize(int value) {
        if (value < 0) {
            AppLogger.error("Failed to change " + KEY_CAPTURE_MIN_FACE_SIZE + ".Value is invalid");
            return;
        }
        properties.setProperty(KEY_CAPTURE_MIN_FACE_SIZE, String.valueOf(value));
        AppLogger.info(KEY_CAPTURE_MIN_FACE_SIZE + " has been changed to " + value);
    }

    // capture.capture_interval_ms (int)
    public int getCaptureIntervalMs() {
        String s = properties.getProperty(KEY_CAPTURE_INTERVAL_MS, "900");
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: Invalid number format for " + KEY_CAPTURE_INTERVAL_MS, ex);
            return 900;
        }
    }

    public void setCaptureIntervalMs(int value) {
        if (value < 0) {
            AppLogger.error("Failed to change " + KEY_CAPTURE_INTERVAL_MS + ".Value is invalid");
            return;
        }
        properties.setProperty(KEY_CAPTURE_INTERVAL_MS, String.valueOf(value));
        AppLogger.info(KEY_CAPTURE_INTERVAL_MS + " has been changed to " + value);
    }

    // capture.capture_attempt_mutliplier (int) -- keep same misspelling
    public int getCaptureAttemptMultiplier() {
        String s = properties.getProperty(KEY_CAPTURE_ATTEMPT_MULTIPLIER, "12");
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: Invalid number format for " + KEY_CAPTURE_ATTEMPT_MULTIPLIER, ex);
            return 12;
        }
    }

    public void setCaptureAttemptMultiplier(int value) {
        if (value < 0) {
            AppLogger.error("Failed to change " + KEY_CAPTURE_ATTEMPT_MULTIPLIER + ".Value is invalid");
            return;
        }
        properties.setProperty(KEY_CAPTURE_ATTEMPT_MULTIPLIER, String.valueOf(value));
        AppLogger.info(KEY_CAPTURE_ATTEMPT_MULTIPLIER + " has been changed to " + value);
    }

    // capture.frame_wait_timeout_ms (int)
    public int getCaptureFrameWaitTimeoutMs() {
        String s = properties.getProperty(KEY_CAPTURE_FRAME_WAIT_TIMEOUT_MS, "500");
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: Invalid number format for " + KEY_CAPTURE_FRAME_WAIT_TIMEOUT_MS, ex);
            return 500;
        }
    }

    public void setCaptureFrameWaitTimeoutMs(int value) {
        if (value < 0) {
            AppLogger.error("Failed to change " + KEY_CAPTURE_FRAME_WAIT_TIMEOUT_MS + ".Value is invalid");
            return;
        }
        properties.setProperty(KEY_CAPTURE_FRAME_WAIT_TIMEOUT_MS, String.valueOf(value));
        AppLogger.info(KEY_CAPTURE_FRAME_WAIT_TIMEOUT_MS + " has been changed to " + value);
    }

    // capture.face_persistence_ns (long)
    public long getCaptureFacePersistenceNs() {
        String s = properties.getProperty(KEY_CAPTURE_FACE_PERSISTENCE_NS, "350");
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: Invalid number format for " + KEY_CAPTURE_FACE_PERSISTENCE_NS, ex);
            return 350L;
        }
    }

    public void setCaptureFacePersistenceNs(long value) {
        if (value < 0) {
            AppLogger.error("Failed to change " + KEY_CAPTURE_FACE_PERSISTENCE_NS + ".Value is invalid");
            return;
        }
        properties.setProperty(KEY_CAPTURE_FACE_PERSISTENCE_NS, String.valueOf(value));
        AppLogger.info(KEY_CAPTURE_FACE_PERSISTENCE_NS + " has been changed to " + value);
    }

    // embedding.modelPath (String)
    public String getEmbeddingModelPath() {
        return properties.getProperty(KEY_EMBEDDING_MODEL_PATH, "data/resources/arcface.onnx");
    }

    public void setEmbeddingModelPath(String path) {
        if (path == null) {
            AppLogger.error("Failed to change " + KEY_EMBEDDING_MODEL_PATH + ".Path is null");
            return;
        }
        properties.setProperty(KEY_EMBEDDING_MODEL_PATH, path);
        AppLogger.info(KEY_EMBEDDING_MODEL_PATH + " has been changed to " + path);
    }

    // embedding.embedding.size (int)
    public int getEmbeddingSize() {
        String s = properties.getProperty(KEY_EMBEDDING_SIZE, "512");
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: Invalid number format for " + KEY_EMBEDDING_SIZE, ex);
            return 512;
        }
    }

    public void setEmbeddingSize(int value) {
        if (value <= 0) {
            AppLogger.error("Failed to change " + KEY_EMBEDDING_SIZE + ".Value is invalid");
            return;
        }
        properties.setProperty(KEY_EMBEDDING_SIZE, String.valueOf(value));
        AppLogger.info(KEY_EMBEDDING_SIZE + " has been changed to " + value);
    }

    // embedding.input_size (int)
    public int getEmbeddingInputSize() {
        String s = properties.getProperty(KEY_EMBEDDING_INPUT_SIZE, "112");
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: Invalid number format for " + KEY_EMBEDDING_INPUT_SIZE, ex);
            return 112;
        }
    }

    public int getRecognitionMinFaceWidthPx() {
        String s = properties.getProperty(KEY_RECOGNITION_MIN_FACE_WIDTH_PX, "96");
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: Invalid number format for " + KEY_RECOGNITION_MIN_FACE_WIDTH_PX, ex);
            return 96;
        }
    }

    public void setEmbeddingInputSize(int value) {
        if (value <= 0) {
            AppLogger.error("Failed to change " + KEY_EMBEDDING_INPUT_SIZE + ".Value is invalid");
            return;
        }
        properties.setProperty(KEY_EMBEDDING_INPUT_SIZE, String.valueOf(value));
        AppLogger.info(KEY_EMBEDDING_INPUT_SIZE + " has been changed to " + value);
    }

    // database.URL, database.user, database.password (String)
    public String getDatabaseURL() {
        return properties.getProperty(KEY_DATABASE_URL, "");
    }

    public void setDatabaseURL(String url) {
        if (url == null) {
            AppLogger.error("Failed to change " + KEY_DATABASE_URL + ".Value is null");
            return;
        }
        properties.setProperty(KEY_DATABASE_URL, url);
        AppLogger.info(KEY_DATABASE_URL + " has been changed to " + url);
    }

    public String getDatabaseUser() {
        return properties.getProperty(KEY_DATABASE_USER, "");
    }

    public void setDatabaseUser(String user) {
        if (user == null) {
            AppLogger.error("Failed to change " + KEY_DATABASE_USER + ".Value is null");
            return;
        }
        properties.setProperty(KEY_DATABASE_USER, user);
        AppLogger.info(KEY_DATABASE_USER + " has been changed to " + user);
    }

    public String getDatabasePassword() {
        return properties.getProperty(KEY_DATABASE_PASSWORD, "");
    }

    public void setDatabasePassword(String password) {
        if (password == null) {
            AppLogger.error("Failed to change " + KEY_DATABASE_PASSWORD + ".Value is null");
            return;
        }
        properties.setProperty(KEY_DATABASE_PASSWORD, password);
        AppLogger.info(KEY_DATABASE_PASSWORD + " has been changed.");
    }

    // detection.model_configuration_path & detection.model_weights
    public String getDetectionModelConfigurationPath() {
        return properties.getProperty(KEY_DETECTION_MODEL_CONFIG, "data/resources/opencv_face_detector.pbtxt");
    }

    public void setDetectionModelConfigurationPath(String path) {
        if (path == null) {
            AppLogger.error("Failed to change " + KEY_DETECTION_MODEL_CONFIG + ".Path is null");
            return;
        }
        properties.setProperty(KEY_DETECTION_MODEL_CONFIG, path);
        AppLogger.info(KEY_DETECTION_MODEL_CONFIG + " has been changed to " + path);
    }

    public String getDetectionModelWeightsPath() {
        return properties.getProperty(KEY_DETECTION_MODEL_WEIGHTS, "data/resources/opencv_face_detector_uint8.pb");
    }

    public void setDetectionModelWeightsPath(String path) {
        if (path == null) {
            AppLogger.error("Failed to change " + KEY_DETECTION_MODEL_WEIGHTS + ".Path is null");
            return;
        }
        properties.setProperty(KEY_DETECTION_MODEL_WEIGHTS, path);
        AppLogger.info(KEY_DETECTION_MODEL_WEIGHTS + " has been changed to " + path);
    }

    // preprocessing.* numeric thresholds
    public double getPreprocessingMinSharpnessThreshold() {
        String s = properties.getProperty(KEY_PREPROCESSING_MIN_SHARPNESS_THRESHOLD, "45.0");
        try {
            double value = Double.parseDouble(s);
            double clamped = Math.max(30.0, Math.min(120.0, value));
            if (clamped > 60.0) {
                AppLogger.warn(String.format(
                        "Configured %s %.1f is too strict; using 60.0 instead for better tolerance.",
                        KEY_PREPROCESSING_MIN_SHARPNESS_THRESHOLD, clamped));
                clamped = 60.0;
            }
            return clamped;
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: Invalid number format for " + KEY_PREPROCESSING_MIN_SHARPNESS_THRESHOLD, ex);
            return 45.0;
        }
    }

    public void setPreprocessingMinSharpnessThreshold(Double value) {
        if (value == null) {
            AppLogger.error("Failed to change " + KEY_PREPROCESSING_MIN_SHARPNESS_THRESHOLD + ".Value is null");
            return;
        }
        double clamped = Math.max(30.0, Math.min(120.0, value));
        if (!clampedEquals(clamped, value)) {
            AppLogger.warn(String.format(
                    "Requested %s %.1f outside supported range; clamped to %.1f.",
                    KEY_PREPROCESSING_MIN_SHARPNESS_THRESHOLD, value, clamped));
        }
        properties.setProperty(KEY_PREPROCESSING_MIN_SHARPNESS_THRESHOLD, String.valueOf(clamped));
        AppLogger.info(KEY_PREPROCESSING_MIN_SHARPNESS_THRESHOLD + " has been changed to " + clamped);
    }

    private boolean clampedEquals(double a, double b) {
        return Math.abs(a - b) < 1e-6;
    }

    public int getPreprocessingMinBrightness() {
        String s = properties.getProperty(KEY_PREPROCESSING_MIN_BRIGHTNESS, "30");
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: Invalid number format for " + KEY_PREPROCESSING_MIN_BRIGHTNESS, ex);
            return 30;
        }
    }

    public void setPreprocessingMinBrightness(int value) {
        properties.setProperty(KEY_PREPROCESSING_MIN_BRIGHTNESS, String.valueOf(value));
        AppLogger.info(KEY_PREPROCESSING_MIN_BRIGHTNESS + " has been changed to " + value);
    }

    public int getPreprocessingMaxBrightness() {
        String s = properties.getProperty(KEY_PREPROCESSING_MAX_BRIGHTNESS, "230");
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: Invalid number format for " + KEY_PREPROCESSING_MAX_BRIGHTNESS, ex);
            return 230;
        }
    }

    public void setPreprocessingMaxBrightness(int value) {
        properties.setProperty(KEY_PREPROCESSING_MAX_BRIGHTNESS, String.valueOf(value));
        AppLogger.info(KEY_PREPROCESSING_MAX_BRIGHTNESS + " has been changed to " + value);
    }

    public int getPreprocessingMinContrast() {
        String s = properties.getProperty(KEY_PREPROCESSING_MIN_CONTRAST, "20");
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: Invalid number format for " + KEY_PREPROCESSING_MIN_CONTRAST, ex);
            return 20;
        }
    }

    public void setPreprocessingMinContrast(int value) {
        properties.setProperty(KEY_PREPROCESSING_MIN_CONTRAST, String.valueOf(value));
        AppLogger.info(KEY_PREPROCESSING_MIN_CONTRAST + " has been changed to " + value);
    }

    // export.* folder paths
    public String getExportCsvFolderPath() {
        return properties.getProperty(KEY_EXPORT_CSV_FOLDER, "./data/export/CSV/");
    }

    public void setExportCsvFolderPath(String path) {
        if (path == null) {
            AppLogger.error("Failed to change " + KEY_EXPORT_CSV_FOLDER + ".Path is null");
            return;
        }
        properties.setProperty(KEY_EXPORT_CSV_FOLDER, path);
        AppLogger.info(KEY_EXPORT_CSV_FOLDER + " has been changed to " + path);
    }

    public String getExportExcelFolderPath() {
        return properties.getProperty(KEY_EXPORT_EXCEL_FOLDER, "./data/export/Excel/");
    }

    public void setExportExcelFolderPath(String path) {
        if (path == null) {
            AppLogger.error("Failed to change " + KEY_EXPORT_EXCEL_FOLDER + ".Path is null");
            return;
        }
        properties.setProperty(KEY_EXPORT_EXCEL_FOLDER, path);
        AppLogger.info(KEY_EXPORT_EXCEL_FOLDER + " has been changed to " + path);
    }

    public String getExportPdfFolderPath() {
        return properties.getProperty(KEY_EXPORT_PDF_FOLDER, "./data/export/PDF/");
    }

    public void setExportPdfFolderPath(String path) {
        if (path == null) {
            AppLogger.error("Failed to change " + KEY_EXPORT_PDF_FOLDER + ".Path is null");
            return;
        }
        properties.setProperty(KEY_EXPORT_PDF_FOLDER, path);
        AppLogger.info(KEY_EXPORT_PDF_FOLDER + " has been changed to " + path);
    }

    // export.* folder paths




    public boolean isDnnDetectionEnabled() {
        return Boolean.parseBoolean(properties.getProperty(KEY_DNN_ENABLED, "true"));
    }

    public String getDnnModelPath() {
        return properties.getProperty(KEY_DNN_MODEL_PATH, "data/resources/opencv_face_detector_uint8.pb");
    }

    public String getDnnConfigPath() {
        return properties.getProperty(KEY_DNN_CONFIG_PATH, "data/resources/opencv_face_detector.pbtxt");
    }

    public double getDnnConfidence() {
        try {
            return Double.parseDouble(properties.getProperty(KEY_DNN_CONFIDENCE, "0.55"));
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: invalid number for " + KEY_DNN_CONFIDENCE, ex);
            return 0.55;
        }
    }

    public int getRecognitionTopK() {
        try {
            return Integer.parseInt(properties.getProperty(KEY_RECOGNITION_TOP_K, "5"));
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: invalid number for " + KEY_RECOGNITION_TOP_K, ex);
            return 5;
        }
    }

    public double getRecognitionMarginDeep() {
        try {
            return Double.parseDouble(properties.getProperty(KEY_RECOGNITION_MARGIN_DEEP, "0.10"));
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: invalid number for " + KEY_RECOGNITION_MARGIN_DEEP, ex);
            return 0.10;
        }
    }

    public double getRecognitionMarginFallback() {
        try {
            return Double.parseDouble(properties.getProperty(KEY_RECOGNITION_MARGIN_FALLBACK, "0.18"));
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: invalid number for " + KEY_RECOGNITION_MARGIN_FALLBACK, ex);
            return 0.18;
        }
    }

    public double getRecognitionSoftThreshold() {
        try {
            return Double.parseDouble(properties.getProperty(KEY_RECOGNITION_SOFT_THRESHOLD, "0.72"));
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: invalid number for " + KEY_RECOGNITION_SOFT_THRESHOLD, ex);
            return 0.72;
        }
    }

    public double getRecognitionHighThreshold() {
        try {
            return Double.parseDouble(properties.getProperty(KEY_RECOGNITION_HIGH_THRESHOLD, "0.82"));
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: invalid number for " + KEY_RECOGNITION_HIGH_THRESHOLD, ex);
            return 0.82;
        }
    }

    public int getConsistencyWindow() {
        try {
            return Integer.parseInt(properties.getProperty(KEY_RECOGNITION_CONSISTENCY_WINDOW, "5"));
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: invalid number for " + KEY_RECOGNITION_CONSISTENCY_WINDOW, ex);
            return 5;
        }
    }

    public int getConsistencyMinCount() {
        try {
            return Integer.parseInt(properties.getProperty(KEY_RECOGNITION_CONSISTENCY_MIN_COUNT, "3"));
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: invalid number for " + KEY_RECOGNITION_CONSISTENCY_MIN_COUNT, ex);
            return 3;
        }
    }

    public boolean isCohortEnabled() {
        return Boolean.parseBoolean(properties.getProperty(KEY_RECOGNITION_COHORT_ENABLED, "true"));
    }

    public int getCohortSize() {
        try {
            return Integer.parseInt(properties.getProperty(KEY_RECOGNITION_COHORT_SIZE, "6"));
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: invalid number for " + KEY_RECOGNITION_COHORT_SIZE, ex);
            return 6;
        }
    }

    public double getCohortZMin() {
        try {
            return Double.parseDouble(properties.getProperty(KEY_RECOGNITION_COHORT_Z_MIN, "-1.0"));
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: invalid number for " + KEY_RECOGNITION_COHORT_Z_MIN, ex);
            return -1.0;
        }
    }

    public boolean isPruningEnabled() {
        return Boolean.parseBoolean(properties.getProperty(KEY_PRUNING_ENABLED, "true"));
    }

    public double getPruningStdFactor() {
        try {
            return Double.parseDouble(properties.getProperty(KEY_PRUNING_STD_FACTOR, "1.5"));
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: invalid number for " + KEY_PRUNING_STD_FACTOR, ex);
            return 1.5;
        }
    }

    public int getPruningMinKeep() {
        try {
            return Integer.parseInt(properties.getProperty(KEY_PRUNING_MIN_KEEP, "5"));
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: invalid number for " + KEY_PRUNING_MIN_KEEP, ex);
            return 5;
        }
    }

    public boolean isPersonThresholdsEnabled() {
        return Boolean.parseBoolean(properties.getProperty(KEY_PERSON_THRESHOLDS_ENABLED, "true"));
    }

    public double getPersonThresholdsBeta() {
        try {
            return Double.parseDouble(properties.getProperty(KEY_PERSON_THRESHOLDS_BETA, "0.15"));
        } catch (NumberFormatException ex) {
            AppLogger.error("Config error: invalid number for " + KEY_PERSON_THRESHOLDS_BETA, ex);
            return 0.15;
        }
    }
}