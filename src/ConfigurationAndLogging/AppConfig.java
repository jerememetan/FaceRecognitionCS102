package src.ConfigurationAndLogging;
import java.io.InputStream;
import java.io.IOException;
import java.util.Properties;
import java.io.FileOutputStream;

public class AppConfig{
    // Settings
    public final static String KEY_DATABASE_STORAGE_PATH = "database.storage_path";
    public final static String KEY_CASCADE_PATH = "cascade.path";
    public final static String KEY_LOG_FILE_NAME = "log.file.name";
    public final static String KEY_CAMERA_INDEX = "camera.index";
    public final static String KEY_DETECTION_SCALE_FACTOR = "detection.scale_factor";
    public final static String KEY_DETECTION_MIN_NEIGHBORS = "detection.min.neighbors";
    public final static String KEY_DETECTION_MIN_SIZE_PX= "detection.min_size_px";
    public final static String KEY_RECOGNITION_THRESHOLD = "recognition.threshold";
    public final static String KEY_RECOGNITION_CROP_SIZE_PX = "recognition.crop_size_px";
    public final static String KEY_RECOGNITION_IMAGE_FORMAT = "recognition.image_format";
    public final static String KEY_PREPROCESSING_GAUSSIAN_KERNEL_SIZE = "preprocessing.gaussian.kernel_size";
    public final static String KEY_PREPROCESSING_GAUSSIAN_SIGMA_X = "preprocessing.gaussian.sigma_x";
    public final static String KEY_PREPROCESSING_CLAHE_CLIP_LIMIT = "preprocessing.clahe.clip_limit";
    public final static String KEY_PREPROCESSING_CLAHE_GRID_SIZE = "preprocessing.clahe.grid_size";
    
    // --- Singleton Implementation ---
    private static AppConfig instance = null;
    private final Properties properties = new Properties();

    // 1. Private Constructor: Prevents outside classes from calling 'new AppConfig()'
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
    
    // --- Loading Logic ---
    private void load() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("app.properties")) {
            if (input == null) {
                // Log an error if the file isn't found
                AppLogger.error("Sorry, unable to find app.properties");
                return;
            }
            // Load the configuration data
            properties.load(input);
            AppLogger.info("Configuration loaded from app.properties.");

        } catch (IOException ex) {
            AppLogger.error("Error reading app.properties file.", ex);
        }
    }

    public void save() {
        try (FileOutputStream output = new FileOutputStream("app.properties")) {
            // Write the current state of the properties object back to the file
            properties.store(output, "Configuration saved by user at runtime.");
            AppLogger.info("Configuration saved to file.");
        } catch (IOException ex) {
            AppLogger.error("Failed to save configuration to file!", ex);
        }
    }

    // KEY_DATABASE_STORAGE_PATH = "database.storage_path";
    public String getDatabaseStoragePath(){
        return properties.getProperty(KEY_DATABASE_STORAGE_PATH, ".\\project");
    }

    public void setDatabaseStoragePath(String Path){
        if (Path == null){
            AppLogger.error("Failed to change "+ KEY_DATABASE_STORAGE_PATH +".Path is null");
        }
        else{
            this.properties.setProperty(KEY_DATABASE_STORAGE_PATH, Path);
            AppLogger.info(KEY_DATABASE_STORAGE_PATH + " has been changed to " + Path);
        }

    }
    // KEY_CASCADE_PATH = "cascade.path";
    public String getCascadePath(){
        return properties.getProperty(KEY_CASCADE_PATH, "./opencv-cascade-classifier/haarcascade_frontalface_alt.xml");
    }

    public void setCascadePath(String Path){
        if (Path == null){
            AppLogger.error("Failed to change " + KEY_CASCADE_PATH +".Path is null");
        }
        else{
            this.properties.setProperty(KEY_CASCADE_PATH, Path);
            AppLogger.info(KEY_CASCADE_PATH+ " has been changed to " + Path);
        }

    }
    // KEY_LOG_FILE_NAME = "log.file.name";
    public String getLogFileName(){
        return properties.getProperty(KEY_LOG_FILE_NAME, "attendance.log");
    }
    public void setLogFileName(String Path){
        if (Path == null){
            AppLogger.error("Failed to change " + KEY_LOG_FILE_NAME + ".Name is null");
        }
        else{
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
        if (newIndex < 0){
            AppLogger.error("Failed to change " + KEY_CAMERA_INDEX +".Index is null");
        }
        else{
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
        if (newIndex == null){
            AppLogger.error("Failed to change " + KEY_DETECTION_SCALE_FACTOR +".Index is null");
        }
        else{
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
        if (newIndex < 0){
            AppLogger.error("Failed to change " + KEY_DETECTION_MIN_NEIGHBORS +".Index is null");
        }
        else{
            String newIndexStr = String.valueOf(newIndex);
            // 2. Use the final KEY to set the new value in the mutable properties object
            this.properties.setProperty(KEY_DETECTION_MIN_NEIGHBORS, newIndexStr);

            AppLogger.info(KEY_DETECTION_MIN_NEIGHBORS + " has been changed to " + newIndex);
        }
    }

    // KEY_DETECTION_MIN_SIZE_PX= "detection.min_size_px";
        public int getDetectionMinSize() {
        String indexStr = properties.getProperty(KEY_DETECTION_MIN_SIZE_PX,"80"); 
        try {
            return Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            AppLogger.error("Config error: Invalid number format for " + KEY_DETECTION_MIN_SIZE_PX, e);
            return 80; // Return safe default on failure
        }
    }
    public void setDetectionMinSize(int newIndex) {
    // 1. Convert the integer back to a String
        if (newIndex < 0){
            AppLogger.error("Failed to change " + KEY_DETECTION_MIN_SIZE_PX +".Index is null");
        }
        else{
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
        if (newIndex == null){
            AppLogger.error("Failed to change " + KEY_RECOGNITION_THRESHOLD +".Index is null");
        }
        else{
            String newIndexStr = String.valueOf(newIndex);
            // 2. Use the final KEY to set the new value in the mutable properties object
            this.properties.setProperty(KEY_RECOGNITION_THRESHOLD, newIndexStr);

            AppLogger.info(KEY_RECOGNITION_THRESHOLD + " has been changed to " + newIndex);
        }
    }
    // KEY_RECOGNITION_CROP_SIZE_PX = "recognition.crop_size_px";
        public int getRecognitionCropSizePx() {
        String indexStr = properties.getProperty(KEY_RECOGNITION_CROP_SIZE_PX,"200"); 
        try {
            return Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            AppLogger.error("Config error: Invalid number format for " + KEY_RECOGNITION_CROP_SIZE_PX, e);
            return 200; // Return safe default on failure
        }
    }
    public void setRecognitionCropSizePx(int newIndex) {
    // 1. Convert the integer back to a String
        if (newIndex < 0){
            AppLogger.error("Failed to change " + KEY_RECOGNITION_CROP_SIZE_PX +".Index is null");
        }
        else{
            String newIndexStr = String.valueOf(newIndex);
            // 2. Use the final KEY to set the new value in the mutable properties object
            this.properties.setProperty(KEY_RECOGNITION_CROP_SIZE_PX, newIndexStr);

            AppLogger.info(KEY_RECOGNITION_CROP_SIZE_PX + " has been changed to " + newIndex);
        }
    }  
    
    // KEY_RECOGNITION_IMAGE_FORMAT = "recognition.image_format";
    public String getRecognitionImageFormat(){
        return properties.getProperty(KEY_RECOGNITION_IMAGE_FORMAT , ".png");
    }
    public void setRecognitionImageFormat(String Path){
        if (Path == null){
            AppLogger.error("Failed to change "+ KEY_RECOGNITION_IMAGE_FORMAT  +". String is null");
        }
        else{
            this.properties.setProperty(KEY_RECOGNITION_IMAGE_FORMAT , Path);
            AppLogger.info(KEY_RECOGNITION_IMAGE_FORMAT  + " has been changed to " + Path);
        }

    }

    // KEY_PREPROCESSING_GAUSSIAN_KERNEL_SIZE = "preprocessing.gaussian.kernel_size";
        public int getPreprocessingGaussianKernelSize() {
        String indexStr = properties.getProperty(KEY_PREPROCESSING_GAUSSIAN_KERNEL_SIZE ,"5"); 
        try {
            return Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            AppLogger.error("Config error: Invalid number format for " + KEY_PREPROCESSING_GAUSSIAN_KERNEL_SIZE , e);
            return 5; // Return safe default on failure
        }
    }
    public void setPreprocessingGaussianKernelSize(int newIndex) {
    // 1. Convert the integer back to a String
        if (newIndex < 0){
            AppLogger.error("Failed to change " + KEY_PREPROCESSING_GAUSSIAN_KERNEL_SIZE  +".Index is null");
        }
        else{
            String newIndexStr = String.valueOf(newIndex);
            // 2. Use the final KEY to set the new value in the mutable properties object
            this.properties.setProperty(KEY_PREPROCESSING_GAUSSIAN_KERNEL_SIZE , newIndexStr);

            AppLogger.info(KEY_PREPROCESSING_GAUSSIAN_KERNEL_SIZE  + " has been changed to " + newIndex);
        }
    }  
    // KEY_PREPROCESSING_GAUSSIAN_SIGMA_X = "preprocessing.gaussian.sigma_x";
        public int getPreprocessingGaussianSigmaX() {
        String indexStr = properties.getProperty(KEY_PREPROCESSING_GAUSSIAN_SIGMA_X ,"0"); 
        try {
            return Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            AppLogger.error("Config error: Invalid number format for " + KEY_PREPROCESSING_GAUSSIAN_SIGMA_X , e);
            return 0; // Return safe default on failure
        }
    }
    public void setPreprocessingGaussianSigmaX(int newIndex) {
    // 1. Convert the integer back to a String
        if (newIndex < 0){
            AppLogger.error("Failed to change " + KEY_PREPROCESSING_GAUSSIAN_SIGMA_X + ".Index is null");
        }
        else{
            String newIndexStr = String.valueOf(newIndex);
            // 2. Use the final KEY to set the new value in the mutable properties object
            this.properties.setProperty(KEY_PREPROCESSING_GAUSSIAN_SIGMA_X , newIndexStr);

            AppLogger.info(KEY_PREPROCESSING_GAUSSIAN_SIGMA_X  + " has been changed to " + newIndex);
        }
    }      
    // KEY_PREPROCESSING_CLAHE_CLIP_LIMIT = "preprocessing.clahe.clip_limit";
    public double getPreprocessingClaheClipLimit() {
        String indexStr = properties.getProperty(KEY_PREPROCESSING_CLAHE_CLIP_LIMIT, "2.0"); // Default value "0"
        try {
            return Double.parseDouble(indexStr);
        } catch (NumberFormatException e) {
            AppLogger.error("Config error: Invalid number format for " + KEY_PREPROCESSING_CLAHE_CLIP_LIMIT, e);
            return 2.0; // Return safe default on failure
            }
    }
    public void setPreprocessingClaheClipLimit(Double newIndex) {
    // 1. Convert the integer back to a String
        if (newIndex == null){
            AppLogger.error("Failed to change " + KEY_PREPROCESSING_CLAHE_CLIP_LIMIT +".Index is null");
        }
        else{
            String newIndexStr = String.valueOf(newIndex);
            // 2. Use the final KEY to set the new value in the mutable properties object
            this.properties.setProperty(KEY_PREPROCESSING_CLAHE_CLIP_LIMIT, newIndexStr);

            AppLogger.info(KEY_PREPROCESSING_CLAHE_CLIP_LIMIT + " has been changed to " + newIndex);
        }
    }
    // KEY_PREPROCESSING_CLAHE_GRID_SIZE = "prepr// 
        public int getPreprocessingClaheGridSize() {
        String indexStr = properties.getProperty(KEY_PREPROCESSING_CLAHE_GRID_SIZE ,"8"); 
        try {
            return Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            AppLogger.error("Config error: Invalid number format for " + KEY_PREPROCESSING_CLAHE_GRID_SIZE , e);
            return 8; // Return safe default on failure
        }
    }
    public void setPreprocessingClaheGridSize(int newIndex) {
    // 1. Convert the integer back to a String
        if (newIndex < 0){
            AppLogger.error("Failed to change " + KEY_PREPROCESSING_CLAHE_GRID_SIZE  +".Index is null");
        }
        else{
            String newIndexStr = String.valueOf(newIndex);
            // 2. Use the final KEY to set the new value in the mutable properties object
            this.properties.setProperty(KEY_PREPROCESSING_CLAHE_GRID_SIZE , newIndexStr);

            AppLogger.info(KEY_PREPROCESSING_CLAHE_GRID_SIZE  + " has been changed to " + newIndex);
        }
    }
}

