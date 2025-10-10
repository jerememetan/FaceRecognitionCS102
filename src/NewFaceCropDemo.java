package src;
import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;
import org.opencv.highgui.HighGui;
import java.util.concurrent.atomic.AtomicReference;
import java.awt.*;
import java.io.File;

import src.ConfigurationAndLogging.*;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class NewFaceCropDemo extends JFrame implements IConfigChangeListener{
    private final CameraPanel cameraPanel = new CameraPanel(); // Your display component
    private final String finalPath; // Holds the save folder path
    private VideoCapture capture;
    private CascadeClassifier faceDetector;
    private Mat frame = new Mat();
    private Mat gray = new Mat();
    private volatile boolean running = true;
    private Thread cameraThread;

    // --- NEW CONSTANT FIELDS (Based on previous discussion) ---
    private final int RECOGNITION_CROP_SIZE_PX = AppConfig.KEY_RECOGNITION_CROP_SIZE_PX;
    private final int PREPROCESSING_GAUSSIAN_KERNEL_SIZE = AppConfig.KEY_PREPROCESSING_GAUSSIAN_KERNEL_SIZE;
    private final int PREPROCESSING_GAUSSIAN_SIGMA_X = AppConfig.KEY_PREPROCESSING_GAUSSIAN_SIGMA_X;
    private final double PREPROCESSING_CLAHE_CLIP_LIMIT = AppConfig.KEY_PREPROCESSING_CLAHE_CLIP_LIMIT;
    private final double PREPROCESSING_CLAHE_GRID_SIZE = AppConfig.KEY_PREPROCESSING_CLAHE_GRID_SIZE;
    private final Object frameLock = new Object(); // Object for synchronization
    private Mat currentFrame = new Mat();    // To store the latest frame (BGR)
    private Rect[] currentFaces = new Rect[0]; // To store the latest detected faces   

    static {
        // Load OpenCV native library
        System.load(new File("lib/opencv_java480.dll").getAbsolutePath());
    }
    public NewFaceCropDemo(String finalPath) {
        this.finalPath = finalPath;
        
        setTitle("Face Crop Demo - Collecting to: " + finalPath);

        // 1. Initialize OpenCV components
        initializeOpenCV();

        // 2. Setup GUI Layout (BorderLayout)
        setLayout(new BorderLayout());
        FaceCropSettingsPanel settingsPanel = new FaceCropSettingsPanel(this); 
        
        add(cameraPanel, BorderLayout.CENTER); // Camera view
        add(settingsPanel, BorderLayout.EAST); // Sidebar
        
        // 3. Setup Frame and Listener
        setupFrameAndListener();
        
        // 4. Start the camera loop thread
        startCameraLoop(); 
    }    

    private void initializeOpenCV() {
        String cascadePath = AppConfig.getInstance().getCascadePath();
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
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // Closes this window, not the whole app
        
        // Use a window listener to cleanly stop the thread when the window closes
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                stopCameraLoop();
            }
        });
        
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }
    private void stopCameraLoop() {
        running = false; 
        AppLogger.info("Ending Capturing Session for " + finalPath);

        try {
            if (cameraThread != null) {
                cameraThread.join(500); // Wait briefly
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Cleanup resources
        if (capture != null && capture.isOpened()) capture.release();
        frame.release();
        gray.release();
        
        // If you need the launcher to reappear, your launcher handles this
        // The JFrame.DISPOSE_ON_CLOSE handles the window closing.
    }
    private void startCameraLoop() {
        cameraThread = new Thread(() -> {
            while (running) {
                // --- LOAD ADJUSTABLE SETTINGS FROM APPCONFIG ---
                double DETECTION_SCALE_FACTOR = AppConfig.getInstance().getDetectionScaleFactor();
                int DETECTION_MIN_NEIGHBORS = AppConfig.getInstance().getDetectionMinNeighbors();
                int DETECTION_MIN_SIZE_PX = AppConfig.getInstance().getDetectionMinSize();
                
                // ... (The rest of your while(true) loop code goes here) ...
                
                // The key line is reading the frame:
                if (!capture.read(frame)) {
                    AppLogger.warn("No Frame Captured! Breaking loop.");
                    break;
                }
                
                // ... (Your Imgproc.cvtColor, GaussianBlur, CLAHE code) ...
                Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
                Imgproc.GaussianBlur(gray, gray, new Size(PREPROCESSING_GAUSSIAN_KERNEL_SIZE, PREPROCESSING_GAUSSIAN_KERNEL_SIZE), PREPROCESSING_GAUSSIAN_SIGMA_X);
                // --- DETECTION ---
                MatOfRect faces = new MatOfRect();
                faceDetector.detectMultiScale(gray, faces, DETECTION_SCALE_FACTOR, DETECTION_MIN_NEIGHBORS, 0, new Size(DETECTION_MIN_SIZE_PX, DETECTION_MIN_SIZE_PX), new Size());
                Rect[] faceArray = faces.toArray();
                // --- THREAD-SAFE UPDATE BLOCK (CRITICAL) ---
                synchronized (frameLock) {
                    if (!frame.empty()) {
                        // Copy the frame to 'currentFrame' for safe saving outside this thread
                        frame.copyTo(currentFrame); 
                    }
                    // Update the face array
                    currentFaces = faceArray; 
                }
                // ------------------------------------------                
                // Draw rectangles around detected faces
                for (Rect rect : faceArray) {
                    Imgproc.rectangle(frame, new Point(rect.x, rect.y),
                            new Point(rect.x + rect.width, rect.y + rect.height),
                            new Scalar(0, 255, 0), 2);
                            
                    // Add face count text
                    Imgproc.putText(frame, "Face detected", 
                        new Point(rect.x, rect.y - 10),
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(0, 255, 0), 2);
                }
                // Add instruction text to frame
                String instruction = "Press 'p' to save, 'q' to quit. Faces: " + faceArray.length;
                Imgproc.putText(frame, instruction, new Point(10, 30),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(255, 255, 255), 2);
                // --- DISPLAY FRAME IN CAMERA PANEL ---
                cameraPanel.displayMat(frame); // Your custom frame display method
                
                // This replaced your HighGui.waitKey() logic for saving/quitting:
                // Your saving logic needs to be triggered by an event listener (like a button) now, 
                // not a keyboard press in a HighGui window.

                // Use a short delay instead of waitKey to manage frame rate
                try { Thread.sleep(30); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            stopCameraLoop();
        });
        cameraThread.start();
    }

    @Override
    public void onCaptureFaceRequested() {
        // Synchronize access to currentFrame and currentFaces
        synchronized (frameLock) {
            if (currentFaces.length == 0 || currentFrame.empty()) {
                AppLogger.warn("Capture failed: No face detected or frame is empty.");
                JOptionPane.showMessageDialog(this, 
                                            "Capture failed: Please ensure one face is visible.", 
                                            "Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            // Proceed with capture logic using the FIRST detected face
            Rect rect = currentFaces[0]; 
            
            try {
                // 1. Get the BGR face submat from the shared frame
                Mat bgrFace = currentFrame.submat(rect);
                
                // 2. Preprocessing (Must match fixed constants!)
                Mat grayFace = new Mat();
                Imgproc.cvtColor(bgrFace, grayFace, Imgproc.COLOR_BGR2GRAY);
                
                // Apply Fixed Gaussian Blur
                double kernelSize = (double)AppConfig.KEY_PREPROCESSING_GAUSSIAN_KERNEL_SIZE; 
                double sigmaX = (double)AppConfig.KEY_PREPROCESSING_GAUSSIAN_SIGMA_X;
                Imgproc.GaussianBlur(grayFace, grayFace, new Size(kernelSize, kernelSize), sigmaX);
                
                // Apply Fixed CLAHE
                double clipLimit = AppConfig.KEY_PREPROCESSING_CLAHE_CLIP_LIMIT; 
                double gridSize = AppConfig.KEY_PREPROCESSING_CLAHE_GRID_SIZE;
                Imgproc.createCLAHE(clipLimit, new Size(gridSize, gridSize)).apply(grayFace, grayFace);

                // 3. Resize to the fixed crop size and Save
                Mat resizedFace = new Mat();
                double cropSize = AppConfig.KEY_RECOGNITION_CROP_SIZE_PX;
                Imgproc.resize(grayFace, resizedFace, new Size(cropSize, cropSize));
                
                String fileName = finalPath + "/face_" + System.currentTimeMillis() + ".jpg";
                boolean saved = Imgcodecs.imwrite(fileName, resizedFace);

                if (saved) {
                    AppLogger.info("✓ Image captured and saved to: " + fileName);
                    JOptionPane.showMessageDialog(this, "Face captured successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    AppLogger.error("✗ Captured Image failed to save!");
                    JOptionPane.showMessageDialog(this, "Image save failed!", "Error", JOptionPane.ERROR_MESSAGE);
                }

                // 4. Cleanup temporary Mats
                bgrFace.release();
                grayFace.release();
                resizedFace.release();
                
            } catch (Exception e) {
                AppLogger.error("Error during face capture: " + e.getMessage());
                JOptionPane.showMessageDialog(this, "An error occurred during capture.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    } 
    // --- IConfigChangeListener IMPLEMENTATION (Sidebar Communication) ---

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

    @Override
    public void onSaveSettingsRequested() {
        AppConfig.getInstance().save(); 
    }     
    
    public static void main(String[] args) {
        String saveFolder = AppConfig.getInstance().getDatabaseStoragePath();
        String cascadePath = AppConfig.getInstance().getCascadePath();
        new File(saveFolder).mkdirs();         // Create save folder if it doesn't exist
        AppConfig.getInstance(); // Ensure config is loaded

        SwingUtilities.invokeLater(() -> {
            Name_ID_GUI gui = new Name_ID_GUI();
            gui.setDataSubmittedListener(new Name_ID_GUI.DataSubmittedListener() {
                public void onDataSubmitted(int id, String name) {
                    // The data is now extracted and available here
                    
                    String finalPath = saveFolder + "/"+ id + "_" + name;
                    new File(finalPath).mkdirs(); 
                    AppLogger.info("Launching FaceCropDemo for Id:" + id + " Name:" + name );
                    
                    // *** IMPORTANT: LAUNCH THE CAMERA WINDOW HERE ***
                    SwingUtilities.invokeLater(() -> new NewFaceCropDemo(finalPath));
                }
            });
        }); 
    }
}