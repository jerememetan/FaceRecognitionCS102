package gui.recognition;

import config.AppConfig;
import config.AppLogger;
import config.IConfigChangeListener;
import gui.config.FaceCropSettingsPanel;
import java.awt.BorderLayout;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import service.recognition.LiveRecognitionService;
import service.recognition.LiveRecognitionService.RecognitionOutcome;

/**
 * Swing UI that renders the live recognition feed and delegates recognition
 * logic to {@link LiveRecognitionService}.
 */
public class LiveRecognitionViewer extends JFrame implements IConfigChangeListener {
    private static final Size DNN_INPUT_SIZE = new Size(300, 300);
    private static final Scalar DNN_MEAN_SUBTRACTION = new Scalar(104.0, 117.0, 123.0);
    private static final double MIN_ASPECT_RATIO = 0.7;
    private static final double TRACKING_DISTANCE_THRESHOLD = 80.0;
    private static final int TRACK_MISS_TOLERANCE = 10;

    private final CameraPanel cameraPanel = new CameraPanel();
    private final LiveRecognitionService recognitionService = new LiveRecognitionService();
    private volatile double dnnConfidenceThreshold = AppConfig.getInstance().getDnnConfidence();
    private volatile int minRecognitionWidthPx = AppConfig.getInstance().getRecognitionMinFaceWidthPx();
    private final Map<String, TrackedFace> activeTracks = new HashMap<>();
    private int nextTrackId = 0;

    private VideoCapture capture;
    private Net dnnFaceDetector;
    private final Mat webcamFrame = new Mat();
    private volatile boolean running = true;
    private Thread recognitionThread;
    private int frameCounter = 0;

    static {
        System.load(new File(AppConfig.getInstance().getOpenCvLibPath()).getAbsolutePath());
    }

    public LiveRecognitionViewer() {
        super("Real-Time Face Recognition");

        recognitionService.reloadDataset();
        initializeOpenCV();

        setLayout(new BorderLayout());

        FaceCropSettingsPanel settingsPanel = new FaceCropSettingsPanel(this, false, true);

        add(cameraPanel, BorderLayout.CENTER);
        add(settingsPanel, BorderLayout.EAST);

        setupFrameAndListener();
        startRecognitionLoop();
    }

    private List<Rect> detectFacesWithDNN(Mat frame) {
        List<Rect> faces = new java.util.ArrayList<>();

        if (dnnFaceDetector == null || frame.empty()) {
            return faces;
        }

        try {
            Mat blob = Dnn.blobFromImage(frame, 1.0, DNN_INPUT_SIZE, DNN_MEAN_SUBTRACTION);

            dnnFaceDetector.setInput(blob);
            Mat detections = dnnFaceDetector.forward();

            Mat detectionsFloat = new Mat();
            detections.convertTo(detectionsFloat, CvType.CV_32F);

            int numDetections = (int) detectionsFloat.size(2);
            int dims = (int) detectionsFloat.size(3);

            for (int i = 0; i < numDetections; i++) {
                float[] detection = new float[dims];
                detectionsFloat.get(new int[] { 0, 0, i, 0 }, detection);

                float confidence = detection[2];
                double threshold = Math.max(0.05, Math.min(0.99, dnnConfidenceThreshold));
                if (confidence < threshold) {
                    continue;
                }

                int x1 = (int) Math.round(detection[3] * frame.cols());
                int y1 = (int) Math.round(detection[4] * frame.rows());
                int x2 = (int) Math.round(detection[5] * frame.cols());
                int y2 = (int) Math.round(detection[6] * frame.rows());

                x1 = Math.max(0, Math.min(x1, frame.cols() - 1));
                y1 = Math.max(0, Math.min(y1, frame.rows() - 1));
                x2 = Math.max(0, Math.min(x2, frame.cols() - 1));
                y2 = Math.max(0, Math.min(y2, frame.rows() - 1));

                if (x2 <= x1 || y2 <= y1) {
                    continue;
                }

                int width = x2 - x1;
                int height = y2 - y1;

                if (width < minRecognitionWidthPx) {
                    continue;
                }

                double aspect = (double) width / Math.max(1, height);
                if (aspect < MIN_ASPECT_RATIO) {
                    continue;
                }

                faces.add(new Rect(x1, y1, width, height));
            }

            blob.release();
            detections.release();
            detectionsFloat.release();

        } catch (Exception e) {
            AppLogger.error("DNN face detection failed: " + e.getMessage(), e);
        }

        return faces;
    }

    private void initializeOpenCV() {
        try {
            String modelConfiguration = "data/resources/opencv_face_detector.pbtxt";
            String modelWeights = "data/resources/opencv_face_detector_uint8.pb";

            if (new File(modelConfiguration).exists() && new File(modelWeights).exists()) {
                dnnFaceDetector = Dnn.readNetFromTensorflow(modelWeights, modelConfiguration);
                AppLogger.info("DNN face detector loaded successfully for recognition");
            } else {
                AppLogger.error("DNN model files not found; face detection unavailable");
                throw new RuntimeException("Cannot find DNN model files for face detection");
            }
        } catch (Exception e) {
            AppLogger.error("Failed to load DNN face detector: " + e.getMessage(), e);
            throw new RuntimeException("Failed to initialize DNN face detector", e);
        }

        capture = openCameraWithFallback();
        if (!capture.isOpened()) {
            AppLogger.error("Error opening webcam with all backends!");
            throw new RuntimeException("Camera Initialization failed.");
        }

        configureCamera(capture);
        AppLogger.info("Camera initialized successfully");
    }

    private VideoCapture openCameraWithFallback() {
        int[] cameraIndices = { 0, 1, 2, 3, 4 };
        int[] backends = {
                org.opencv.videoio.Videoio.CAP_DSHOW,
                org.opencv.videoio.Videoio.CAP_MSMF,
                org.opencv.videoio.Videoio.CAP_WINRT,
                org.opencv.videoio.Videoio.CAP_ANY
        };
        String[] backendNames = { "DSHOW", "MSMF", "WINRT", "ANY" };

        for (int cameraIndex : cameraIndices) {
            for (int i = 0; i < backends.length; i++) {
                int backend = backends[i];
                String backendName = backendNames[i];

                AppLogger.info("Attempting camera index " + cameraIndex + " with " + backendName + " backend...");

                VideoCapture cap = new VideoCapture(cameraIndex, backend);

                if (cap.isOpened()) {
                    Mat testFrame = new Mat();
                    boolean canRead = false;

                    try {
                        Thread.sleep(500);

                        for (int attempt = 0; attempt < 3; attempt++) {
                            if (cap.read(testFrame) && !testFrame.empty()) {
                                canRead = true;
                                break;
                            }
                            Thread.sleep(200);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        AppLogger.warn("Camera initialization interrupted");
                    } finally {
                        testFrame.release();
                    }

                    if (canRead) {
                        AppLogger.info("Camera opened successfully: index " + cameraIndex + " with " + backendName
                                + " backend");
                        return cap;
                    } else {
                        AppLogger.warn(
                                "Camera opened but cannot read frames: index " + cameraIndex + " with " + backendName);
                        cap.release();
                    }
                } else {
                    AppLogger.warn("Failed to open camera: index " + cameraIndex + " with " + backendName);
                    cap.release();
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        AppLogger.error("All camera initialization attempts failed!");
        return new VideoCapture();
    }

    private void configureCamera(VideoCapture cap) {
        if (cap == null || !cap.isOpened()) {
            AppLogger.warn("Cannot configure camera: capture is null or not opened");
            return;
        }

        try {
            boolean widthSet = cap.set(org.opencv.videoio.Videoio.CAP_PROP_FRAME_WIDTH, 640);
            boolean heightSet = cap.set(org.opencv.videoio.Videoio.CAP_PROP_FRAME_HEIGHT, 480);
            boolean fpsSet = cap.set(org.opencv.videoio.Videoio.CAP_PROP_FPS, 30);

            boolean codecSet = cap.set(org.opencv.videoio.Videoio.CAP_PROP_FOURCC,
                    VideoWriter_fourcc('M', 'J', 'P', 'G'));

            if (!codecSet) {
                AppLogger.info("MJPEG codec not supported, using default codec");
            }

            double actualWidth = cap.get(org.opencv.videoio.Videoio.CAP_PROP_FRAME_WIDTH);
            double actualHeight = cap.get(org.opencv.videoio.Videoio.CAP_PROP_FRAME_HEIGHT);
            double actualFps = cap.get(org.opencv.videoio.Videoio.CAP_PROP_FPS);

            AppLogger.info(String.format("Camera configured: %.0fx%.0f @ %.0ffps (MJPEG: %s)",
                    actualWidth, actualHeight, actualFps, codecSet ? "yes" : "no"));

            if (actualWidth <= 0 || actualHeight <= 0) {
                AppLogger.warn("Camera reported invalid resolution, configuration may have failed");
            }

        } catch (Exception e) {
            AppLogger.warn("Camera configuration failed (non-critical): " + e.getMessage());
        }
    }

    private static int VideoWriter_fourcc(char c1, char c2, char c3, char c4) {
        return ((int) c1) | (((int) c2) << 8) | (((int) c3) << 16) | (((int) c4) << 24);
    }

    private void setupFrameAndListener() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                stopRecognitionLoop();
            }
        });
        pack();
        setVisible(true);
    }

    private void startRecognitionLoop() {
        recognitionThread = new Thread(() -> {
            while (running && capture.read(webcamFrame)) {
                frameCounter++;

                int frameSkip = recognitionService.getAdaptiveFrameSkip();
                boolean shouldProcess = (frameCounter % frameSkip == 0);

                List<Rect> detectedFaces = detectFacesWithDNN(webcamFrame);

                incrementTrackMissCounters();
                Set<String> matchedTrackIds = new HashSet<>();

                for (Rect rect : detectedFaces) {
                    TrackedFace track = findOrCreateTrack(rect, matchedTrackIds);
                    matchedTrackIds.add(track.id);

                    if (shouldProcess) {
                        RecognitionOutcome outcome = recognitionService.analyzeFace(webcamFrame, rect, track.id);
                        track.lastOutcome = outcome;
                    }

                    if (track.lastOutcome == null) {
                        track.lastOutcome = LiveRecognitionService.RecognitionOutcome.rejected();
                    }

                    Scalar color = track.lastOutcome.displayColor();
                    Imgproc.rectangle(webcamFrame, new Point(rect.x, rect.y),
                            new Point(rect.x + rect.width, rect.y + rect.height),
                            color, 2);

                    Imgproc.putText(webcamFrame, track.lastOutcome.displayText(),
                            new Point(rect.x, Math.max(20, rect.y - 10)),
                            Imgproc.FONT_HERSHEY_SIMPLEX, 0.9, color, 2);
                }

                cleanupStaleTracks();
                cameraPanel.displayMat(webcamFrame);
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            stopRecognitionLoop();
        });
        recognitionThread.start();
    }

    private void stopRecognitionLoop() {
        running = false;
        AppLogger.info("Exiting Face Recognition Viewer...");

        if (recognitionThread != null) {
            if (Thread.currentThread() != recognitionThread) {
                recognitionThread.interrupt();
                try {
                    recognitionThread.join(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (capture != null && capture.isOpened()) {
            capture.release();
        }

        webcamFrame.release();
        recognitionService.release();
        activeTracks.clear();

        recognitionThread = null;
        capture = null;
    }

    @Override
    public void onDnnConfidenceChanged(double newConfidence) {
        double clamped = Math.max(0.05, Math.min(0.99, newConfidence));
        dnnConfidenceThreshold = clamped;
        AppConfig.getInstance().setDnnConfidence(clamped);
    }

    @Override
    public void onMinSizeChanged(int newMinSize) {
        int clamped = Math.max(20, newMinSize);
        AppConfig.getInstance().setDetectionMinSize(clamped);
    }

    @Override
    public void onRecognitionMinFaceWidthChanged(int newMinWidth) {
        int clamped = Math.max(32, newMinWidth);
        minRecognitionWidthPx = clamped;
        AppConfig.getInstance().setRecognitionMinFaceWidthPx(clamped);
    }

    @Override
    public void onConsistencyWindowChanged(int newWindowSize) {
        AppConfig.getInstance().setConsistencyWindow(newWindowSize);
    }

    @Override
    public void onConsistencyMinCountChanged(int newMinCount) {
        AppConfig.getInstance().setConsistencyMinCount(newMinCount);
    }

    @Override
    public void onCaptureFaceRequested() {
        AppLogger.warn("Face capture requested, but not supported in LiveRecognitionViewer.");
    }

    @Override
    public void onSaveSettingsRequested() {
        AppConfig.getInstance().save();
    }

    public static void main(String[] args) {
        AppLogger.info("LiveRecognitionViewer starting...");

        AppConfig.getInstance();

        SwingUtilities.invokeLater(() -> {
            try {
                new LiveRecognitionViewer();
            } catch (Exception e) {
                AppLogger.error("Failed to launch LiveRecognitionViewer: " + e.getMessage());
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "The recognition viewer failed to start. See logs for details.",
                        "Startup Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void incrementTrackMissCounters() {
        for (TrackedFace track : activeTracks.values()) {
            track.framesSinceSeen++;
        }
    }

    private TrackedFace findOrCreateTrack(Rect rect, Set<String> matchedTrackIds) {
        TrackedFace bestMatch = null;
        double bestDistance = Double.MAX_VALUE;

        double rectCenterX = rect.x + rect.width / 2.0;
        double rectCenterY = rect.y + rect.height / 2.0;

        for (TrackedFace candidate : activeTracks.values()) {
            if (matchedTrackIds.contains(candidate.id)) {
                continue;
            }

            double distance = candidate.distanceTo(rectCenterX, rectCenterY);
            double dynamicThreshold = Math.max(TRACKING_DISTANCE_THRESHOLD, candidate.averageSize() * 0.6);

            if (distance < dynamicThreshold && distance < bestDistance) {
                bestDistance = distance;
                bestMatch = candidate;
            }
        }

        if (bestMatch == null) {
            bestMatch = new TrackedFace(generateTrackId(), rect);
            activeTracks.put(bestMatch.id, bestMatch);
        } else {
            bestMatch.updateRect(rect);
        }

        bestMatch.framesSinceSeen = 0;
        return bestMatch;
    }

    private void cleanupStaleTracks() {
        Set<String> toRemove = new HashSet<>();
        for (Map.Entry<String, TrackedFace> entry : activeTracks.entrySet()) {
            if (entry.getValue().framesSinceSeen > TRACK_MISS_TOLERANCE) {
                toRemove.add(entry.getKey());
            }
        }

        for (String trackId : toRemove) {
            activeTracks.remove(trackId);
            recognitionService.discardSession(trackId);
        }
    }

    private String generateTrackId() {
        nextTrackId++;
        return "track-" + nextTrackId;
    }

    private static final class TrackedFace {
        private final String id;
        private Rect lastRect;
        private int framesSinceSeen = 0;
        private RecognitionOutcome lastOutcome = LiveRecognitionService.RecognitionOutcome.rejected();

        private TrackedFace(String id, Rect rect) {
            this.id = id;
            this.lastRect = new Rect(rect.x, rect.y, rect.width, rect.height);
        }

        private void updateRect(Rect rect) {
            this.lastRect = new Rect(rect.x, rect.y, rect.width, rect.height);
        }

        private double centerX() {
            return lastRect.x + lastRect.width / 2.0;
        }

        private double centerY() {
            return lastRect.y + lastRect.height / 2.0;
        }

        private double distanceTo(double x, double y) {
            double dx = centerX() - x;
            double dy = centerY() - y;
            return Math.hypot(dx, dy);
        }

        private double averageSize() {
            return (lastRect.width + lastRect.height) / 2.0;
        }
    }
}







