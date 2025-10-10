package facecrop;
import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;

import ConfigurationAndLogging.*;

import javax.swing.*;

import java.awt.*;
import java.io.File;

import java.util.ArrayList;
import java.util.List;

public class NewFaceRecognitionDemo extends JFrame implements IConfigChangeListener {

    // --- INSTANCE FIELDS ---
    private final CameraPanel cameraPanel = new CameraPanel(); 
    private VideoCapture capture;
    private CascadeClassifier faceDetector;
    private ArrayList<List<Mat>> personHistograms; // Store model data
    private ArrayList<String> folder_names; // Store labels
    
    // Read Recognition Threshold once at initialization
    private final int RECOGNITION_CROP_SIZE_PX = AppConfig.KEY_RECOGNITION_CROP_SIZE_PX;
    private final int PREPROCESSING_GAUSSIAN_KERNEL_SIZE = AppConfig.KEY_PREPROCESSING_GAUSSIAN_KERNEL_SIZE;
    private final int PREPROCESSING_GAUSSIAN_SIGMA_X = AppConfig.KEY_PREPROCESSING_GAUSSIAN_SIGMA_X;
    private final double PREPROCESSING_CLAHE_CLIP_LIMIT = AppConfig.KEY_PREPROCESSING_CLAHE_CLIP_LIMIT;
    private final double PREPROCESSING_CLAHE_GRID_SIZE = AppConfig.KEY_PREPROCESSING_CLAHE_GRID_SIZE;
    private final double  RECOGNITION_THRESHOLD = AppConfig.getInstance().getRecognitionThreshold();
    private Mat webcamFrame = new Mat();
    private Mat gray = new Mat();
    private volatile boolean running = true;
    private Thread recognitionThread;

    static {
        // Load OpenCV native library
        System.load(new File("lib/opencv_java480.dll").getAbsolutePath());
    }

    public NewFaceRecognitionDemo() {
        super("Real-Time Face Recognition - Configurable Detection");
        
        initializeModelData();
        initializeOpenCV(); 

        // 2. Setup GUI (Camera + Sidebar)
        setLayout(new BorderLayout());
        
        // Pass 'this' as the listener. The panel only shows Detection controls now.
        FaceCropSettingsPanel settingsPanel = new FaceCropSettingsPanel(this,false); 
        
        add(cameraPanel, BorderLayout.CENTER);
        add(settingsPanel, BorderLayout.EAST);
        
        setupFrameAndListener();
        startRecognitionLoop(); 
    }
    

    // --- INITIALIZATION METHODS ---
    private void initializeModelData() {
        // [*** Copy your load/histogram calculation logic from the old main() here ***]
        // This calculates personHistograms and folderNames
        
        // Example:
        String image_folder_path = AppConfig.getInstance().getDatabaseStoragePath(); //
        File folder_directories = new File(image_folder_path); //
        
        folder_names = new ArrayList<String>();
        File[] list_files = folder_directories.listFiles(); // this 
        if (list_files != null){
            for (File file: list_files){  // for each file in list files
                // if its a folder
                if (file.isDirectory()){
                   folder_names.add(image_folder_path+ "\\" +file.getName());
                }

            }
        }

        System.out.println("=== Folder debug ===");
        for (String s : folder_names) {
            System.out.println("Found folder: " + s);
            File f = new File(s);
            if (f.exists()) {
                File[] imgs = f.listFiles();
                if (imgs != null) {
                    System.out.println("  Contains: " + imgs.length + " items");
                } else {
                    System.out.println("  (Cannot list files)");
                }
            } else {
                System.out.println("  (Folder does not exist!)");
            }
        }
        System.out.println("====================");

        // checks if ./project Folder is empty
        if (list_files == null){
            AppLogger.error("No Image Files found at " +image_folder_path +"!");
            return;
        }
        ArrayList<List<Mat>> personImages = new ArrayList<List<Mat>>();
        for (String s: folder_names){
            List<Mat> temp = loadImages(s);
            personImages.add(temp);   
        }        
        // Check empty of any of the files
        for (List<Mat> t: personImages){
            if (t.isEmpty()){
            AppLogger.error("No training images found in one of the directories!");
            return;               
            }
        }
        personHistograms = new ArrayList<List<Mat>>();
        for (List<Mat> s: personImages){
            List<Mat> temp = computeHistograms(s);
            personHistograms.add(temp);
        }
    }
    
    private void initializeOpenCV() {
        // [*** Copy your faceDetector and VideoCapture initialization here ***]
        String cascadePath = AppConfig.getInstance().getCascadePath(); //
        faceDetector = new CascadeClassifier(cascadePath);
        if (faceDetector.empty()) {
            AppLogger.error("Error loading cascade file: " + cascadePath);
            throw new RuntimeException("Classifier Initialization failed.");
        }
        
        capture = new VideoCapture(AppConfig.getInstance().getCameraIndex());
        if (!capture.isOpened()) {
            AppLogger.error("Error opening webcam!");
            throw new RuntimeException("Camera Initialization failed.");
        }
    }
    private void setupFrameAndListener() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); 
        // Add window listener to call stopRecognitionLoop() when closing
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                stopRecognitionLoop();
            }
        });
        pack();
        setVisible(true);
    }
    // --- RECOGNITION LOOP (Refactored from old while loop) ---
    private void startRecognitionLoop() {
        recognitionThread = new Thread(() -> {
            while (running && capture.read(webcamFrame)) {
                // --- LOAD ADJUSTABLE SETTINGS FROM APPCONFIG ---
                double DETECTION_SCALE_FACTOR = AppConfig.getInstance().getDetectionScaleFactor();
                int DETECTION_MIN_NEIGHBORS = AppConfig.getInstance().getDetectionMinNeighbors();
                int DETECTION_MIN_SIZE_PX = AppConfig.getInstance().getDetectionMinSize();
                
                // ... (Preprocessing using FIXED CONSTANTS) ...
                Imgproc.cvtColor(webcamFrame, gray, Imgproc.COLOR_BGR2GRAY);
                Imgproc.GaussianBlur(gray, gray, new Size(PREPROCESSING_GAUSSIAN_KERNEL_SIZE, PREPROCESSING_GAUSSIAN_KERNEL_SIZE), PREPROCESSING_GAUSSIAN_SIGMA_X);
                Imgproc.createCLAHE(PREPROCESSING_CLAHE_CLIP_LIMIT , new Size(PREPROCESSING_CLAHE_GRID_SIZE, PREPROCESSING_CLAHE_GRID_SIZE)).apply(gray, gray);
                MatOfRect faces = new MatOfRect();
                // Detect faces using ADJUSTABLE detection settings
                faceDetector.detectMultiScale(gray, faces, DETECTION_SCALE_FACTOR, DETECTION_MIN_NEIGHBORS, 0, new Size(DETECTION_MIN_SIZE_PX, DETECTION_MIN_SIZE_PX), new Size());

                for (Rect rect : faces.toArray()) {
                // Draw rectangle
                Imgproc.rectangle(webcamFrame, new Point(rect.x, rect.y),
                        new Point(rect.x + rect.width, rect.y + rect.height),
                        new Scalar(0, 255, 0), 2);

                // Crop and resize face
                Mat face = gray.submat(rect); 
                Imgproc.resize(face, face, new Size(RECOGNITION_CROP_SIZE_PX, RECOGNITION_CROP_SIZE_PX));
                Mat faceHist = computeHistogram(face);
                
                // Compare with training histograms
                ArrayList<Double> personScores = new ArrayList<Double>();
                for (List<Mat> s: personHistograms){
                    double temp = getBestHistogramScore(faceHist,s);
                    personScores.add(temp);
                }

                System.out.println("Person Scores: " + personScores.toString());

                String displayText;
                int maxIdx = 0;
                for (int i = 0; i < personScores.size(); i++) {
                    if (personScores.get(i) > personScores.get(maxIdx)){
                        maxIdx = i;
                    }
                }
                // TODO -> SPLITTING NEEDS 
                if (personScores.get(maxIdx) > RECOGNITION_THRESHOLD){
                    String parts = folder_names.get(maxIdx);
                    String ShowScore = String.format("%.2f", personScores.get(maxIdx));
                    displayText = parts + " - " + ShowScore;
                    
                }
                else{
                    displayText = "unknown";
                }
                    Imgproc.putText(webcamFrame, displayText, new Point(rect.x, rect.y - 10),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.9, new Scalar(15, 255, 15), 2);
                }
                cameraPanel.displayMat(webcamFrame);
                try {Thread.sleep(30); } catch (InterruptedException e) {Thread.currentThread().interrupt();  break; }

            }
            // ... stopRecognitionLoop cleanup ...
            stopRecognitionLoop();
        });
        recognitionThread.start();
    }

    private void stopRecognitionLoop() {
        // ... (Your cleanup logic for running, capture, frame, etc.) ...
    running = false;
    AppLogger.info("Exiting Face Recognition Demo...");

    if (recognitionThread != null) {
        recognitionThread.interrupt(); 

        try {
            // Wait a short time for the thread to recognize the interrupt and close
            recognitionThread.join(100); // Wait up to 100 milliseconds
        } catch (InterruptedException e) {
            // Re-interrupt the calling thread (main thread)
            Thread.currentThread().interrupt(); 
        }
    }
    
    // Cleanup resources
    if (capture != null && capture.isOpened()) {
        capture.release();
        webcamFrame.release();
        gray.release();
    }
    }    

    @Override
    public void onScaleFactorChanged(double newScaleFactor) {
        AppConfig.getInstance().setDetectionScaleFactor(newScaleFactor);
    }
    
    @Override
    public void onMinNeighborsChanged(int newMinNeighbors) {
        AppConfig.getInstance().setDetectionMinNeighbors(newMinNeighbors);
    }

    @Override
    public void onMinSizeChanged(int newMinSize) {
        AppConfig.getInstance().setDetectionMinSize(newMinSize);
    }
    
    // REQUIRED by interface, but UNUSED in this recognition demo.
    @Override
    public void onCaptureFaceRequested() {
        // Log or show message that capture is not supported in this demo
        AppLogger.warn("Face capture requested, but not supported in FaceRecognitionDemo.");
    } 

    @Override
    public void onSaveSettingsRequested() {
        // Saves the current Detection settings
        AppConfig.getInstance().save();
    }

    public static void main(String[] args) {
        AppLogger.info("FaceRecognitionDemo Starting...");

        // 1. Ensure core configurations are loaded
        AppConfig.getInstance(); 

        // 2. Start the application on the Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            try {
                // Instantiate the JFrame, which triggers all initialization (loading model, starting camera).
                new NewFaceRecognitionDemo();
            } catch (Exception e) {
                AppLogger.error("Failed to launch FaceRecognitionDemo: " + e.getMessage());
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "The Recognition Demo failed to start. See logs for details.", "Startup Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    // Load images from a directory
    private static List<Mat> loadImages(String dirPath) {
        List<Mat> images = new ArrayList<>();
        File dir = new File(dirPath);
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".png"));
        if (files != null) {
            for (File file : files) {
                Mat img = Imgcodecs.imread(file.getAbsolutePath(), Imgcodecs.IMREAD_GRAYSCALE);
                if (!img.empty()) {
                    Imgproc.resize(img, img, new Size(200, 200));
                    images.add(img);
                } else {
                    AppLogger.error("Failed to load image: " + file.getAbsolutePath());
                }
            }
        }
        return images;
    }

    // Compute histogram for a single image
    private static Mat computeHistogram(Mat image) {
        Mat hist = new Mat();
        MatOfInt histSize = new MatOfInt(256);
        MatOfFloat ranges = new MatOfFloat(0f, 256f);
        MatOfInt channels = new MatOfInt(0);
        Imgproc.calcHist(List.of(image), channels, new Mat(), hist, histSize, ranges);
        Core.normalize(hist, hist, 0, 1, Core.NORM_MINMAX);
        return hist;
    }

    // Compute histograms for a list of images
    private static List<Mat> computeHistograms(List<Mat> images) {
        List<Mat> histograms = new ArrayList<>();
        for (Mat img : images) {
            histograms.add(computeHistogram(img));
        }
        return histograms;
    }

    // Get best histogram comparison score
    private static double getBestHistogramScore(Mat faceHist, List<Mat> histograms) {
        double bestScore = 0;
        for (Mat hist : histograms) {
            double score = Imgproc.compareHist(faceHist, hist, Imgproc.HISTCMP_CORREL);
            bestScore = Math.max(bestScore, score);
        }
        return bestScore;
    }
}