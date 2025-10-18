package facecrop;

import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;
import org.opencv.dnn.Net;
import org.opencv.dnn.Dnn;
import org.opencv.videoio.VideoCapture;

import ConfigurationAndLogging.*;
import app.service.FaceEmbeddingGenerator;
import app.util.ImageProcessor;
import java.util.Arrays;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.swing.*;

import java.awt.*;
import java.io.File;

import java.util.ArrayList;
import java.util.List;

public class NewFaceRecognitionDemo extends JFrame implements IConfigChangeListener {

    // --- INSTANCE FIELDS ---
    private final CameraPanel cameraPanel = new CameraPanel();
    private VideoCapture capture;
    private Net dnnFaceDetector; // Changed from CascadeClassifier to DNN
    private ArrayList<List<byte[]>> personEmbeddings = new ArrayList<>(); // Store model data
    private ArrayList<double[]> personCentroids = new ArrayList<>(); // Centroid per person
    private ArrayList<String> folder_names = new ArrayList<>(); // Store training folders (absolute paths)
    private ArrayList<String> personLabels = new ArrayList<>(); // Store display labels

    // Read Recognition Threshold once at initialization
    private final int RECOGNITION_CROP_SIZE_PX = AppConfig.KEY_RECOGNITION_CROP_SIZE_PX;
    private final int PREPROCESSING_GAUSSIAN_KERNEL_SIZE = AppConfig.KEY_PREPROCESSING_GAUSSIAN_KERNEL_SIZE;
    private final int PREPROCESSING_GAUSSIAN_SIGMA_X = AppConfig.KEY_PREPROCESSING_GAUSSIAN_SIGMA_X;
    private final double PREPROCESSING_CLAHE_CLIP_LIMIT = AppConfig.KEY_PREPROCESSING_CLAHE_CLIP_LIMIT;
    private final double PREPROCESSING_CLAHE_GRID_SIZE = AppConfig.KEY_PREPROCESSING_CLAHE_GRID_SIZE;
    private final double RECOGNITION_THRESHOLD = AppConfig.getInstance().getRecognitionThreshold();
    private Mat webcamFrame = new Mat();
    private Mat gray = new Mat();
    private volatile boolean running = true;
    private Thread recognitionThread;

    // Recognition helpers
    private final ImageProcessor imageProcessor = new ImageProcessor();
    private final FaceEmbeddingGenerator embGen = new FaceEmbeddingGenerator();
    private final int TOP_K = 5; // capture more exemplars for robustness
    private final int CONSISTENCY_WINDOW = 8; // longer window for stability
    private final int CONSISTENCY_MIN_COUNT = 5; // require strong agreement
    private final Deque<Integer> recentPredictions = new ArrayDeque<>(CONSISTENCY_WINDOW);
    private final int Q_EMB_WINDOW = 5; // moderate smoothing
    private final Deque<byte[]> recentQueryEmbeddings = new ArrayDeque<>(Q_EMB_WINDOW);

    // Advanced differentiation parameters
    private final double PENALTY_WEIGHT = 0.20; // penalize similarity to wrong persons
    private final double MIN_RELATIVE_MARGIN_PCT = 0.10; // 10% relative margin minimum

    // Per-person adaptive thresholds and metadata
    private ArrayList<Double> personTightness = new ArrayList<>();
    private ArrayList<Double> personAbsoluteThresholds = new ArrayList<>();
    private ArrayList<Double> personRelativeMargins = new ArrayList<>();
    private ArrayList<Double> personStdDevs = new ArrayList<>(); // cluster spread measure

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
        FaceCropSettingsPanel settingsPanel = new FaceCropSettingsPanel(this, false);

        add(cameraPanel, BorderLayout.CENTER);
        add(settingsPanel, BorderLayout.EAST);

        setupFrameAndListener();
        startRecognitionLoop();
    }

    // --- INITIALIZATION METHODS ---
    private void initializeModelData() {
        // Load per-person embeddings (.emb files) for each folder

        // Example:
        folder_names.clear();
        personLabels.clear();

        String image_folder_path = AppConfig.getInstance().getDatabaseStoragePath(); //
        File folder_directories = new File(image_folder_path); //

        File[] list_files = folder_directories.listFiles(); // this
        if (list_files != null) {
            for (File file : list_files) { // for each file in list files
                // if its a folder
                if (file.isDirectory()) {
                    folder_names.add(file.getAbsolutePath());
                    personLabels.add(buildDisplayLabel(file.getName()));
                }

            }
        }

        System.out.println("=== Folder debug ===");
        for (int i = 0; i < folder_names.size(); i++) {
            String s = folder_names.get(i);
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
            if (i < personLabels.size()) {
                System.out.println("  Display label: " + personLabels.get(i));
            }
        }
        System.out.println("====================");

        // checks if ./project Folder is empty
        if (list_files == null) {
            AppLogger.error("No Image Files found at " + image_folder_path + "!");
            personEmbeddings.clear();
            return;
        }
        personEmbeddings = new ArrayList<>();
        personCentroids = new ArrayList<>();
        personTightness = new ArrayList<>();
        personAbsoluteThresholds = new ArrayList<>();
        personRelativeMargins = new ArrayList<>();
        personStdDevs = new ArrayList<>();
        for (String folder : folder_names) {
            List<byte[]> embList = new ArrayList<>();
            File dir = new File(folder);
            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".emb"));
            if (files != null) {
                for (File f : files) {
                    try {
                        byte[] emb = java.nio.file.Files.readAllBytes(f.toPath());
                        if (emb != null && emb.length > 0) {
                            embList.add(emb);
                        }
                    } catch (Exception readEx) {
                        AppLogger.warn("Failed to read embedding file: " + f.getAbsolutePath());
                    }
                }
            }
            personEmbeddings.add(embList);
            double[] centroid = computeCentroid(embList);
            personCentroids.add(centroid);

            double tightness = computeTightness(embList);
            personTightness.add(tightness);

            double stdDev = computeStdDev(embList, centroid);
            personStdDevs.add(stdDev);

            boolean deep = embGen.isDeepLearningAvailable();

            double baseAbsolute = deep ? 0.62 : 0.58;
            double baseMargin = deep ? 0.08 : 0.12;

            boolean likelyHasGlasses = stdDev > 0.12;

            if (likelyHasGlasses) {
                // Adaptive relaxation based on stdDev severity (5-12% relaxation)
                double relaxationFactor = Math.max(0.88, 0.95 - (stdDev * 0.5));
                baseAbsolute *= relaxationFactor;
                baseMargin *= 0.85;
                System.out.println("  [Glasses Mode] Detected high variation (stdDev=" + String.format("%.3f", stdDev)
                        + ") - relaxed thresholds by " + String.format("%.1f", (1.0 - relaxationFactor) * 100) + "%");
            }

            double absoluteThreshold = baseAbsolute + ((1.0 - tightness) * 0.10); // Increased from 0.08
            double relativeMargin = baseMargin + ((1.0 - tightness) * 0.10); // Increased from 0.08

            personAbsoluteThresholds.add(absoluteThreshold);
            personRelativeMargins.add(relativeMargin);

            System.out.printf("Person %s: tightness=%.3f, stdDev=%.3f, absThresh=%.3f, margin=%.3f%n",
                    buildDisplayLabel(new File(folder).getName()), tightness, stdDev, absoluteThreshold,
                    relativeMargin);
        }
    }

    // Compute tightness: average pairwise similarity within person's embeddings
    private double computeTightness(List<byte[]> embList) {
        if (embList == null || embList.size() < 2)
            return 1.0; // single embedding = tight
        double sum = 0.0;
        int count = 0;
        for (int i = 0; i < embList.size(); i++) {
            for (int j = i + 1; j < embList.size(); j++) {
                sum += embGen.calculateSimilarity(embList.get(i), embList.get(j));
                count++;
            }
        }
        return count > 0 ? sum / count : 1.0;
    }

    // Compute standard deviation of similarity to centroid
    private double computeStdDev(List<byte[]> embList, double[] centroid) {
        if (embList == null || embList.size() < 2 || centroid == null)
            return 0.0;
        double[] similarities = new double[embList.size()];
        double mean = 0.0;
        for (int i = 0; i < embList.size(); i++) {
            similarities[i] = cosineSimilarity(embList.get(i), centroid);
            mean += similarities[i];
        }
        mean /= embList.size();
        double variance = 0.0;
        for (double sim : similarities) {
            double diff = sim - mean;
            variance += diff * diff;
        }
        return Math.sqrt(variance / embList.size());
    }

    // Compute normalized centroid for a person
    private double[] computeCentroid(List<byte[]> embList) {
        if (embList == null || embList.isEmpty())
            return null;
        double[] sum = null;
        int count = 0;
        for (byte[] b : embList) {
            double[] v = decodeEmbeddingToDouble(b);
            if (v == null)
                continue;
            normalizeL2InPlace(v);
            if (sum == null) {
                sum = Arrays.copyOf(v, v.length);
            } else {
                for (int i = 0; i < v.length; i++)
                    sum[i] += v[i];
            }
            count++;
        }
        if (sum == null || count == 0)
            return null;
        for (int i = 0; i < sum.length; i++)
            sum[i] /= count;
        normalizeL2InPlace(sum);
        return sum;
    }

    private double[] decodeEmbeddingToDouble(byte[] emb) {
        if (emb == null)
            return null;
        try {
            if (emb.length == 128 * 4) { // 128 floats
                float[] fv = new float[128];
                ByteBuffer bb = ByteBuffer.wrap(emb);
                for (int i = 0; i < 128; i++)
                    fv[i] = bb.getFloat();
                double[] dv = new double[128];
                for (int i = 0; i < 128; i++)
                    dv[i] = fv[i];
                return dv;
            } else if (emb.length == 128 * 8) { // 128 doubles
                double[] dv = new double[128];
                ByteBuffer bb = ByteBuffer.wrap(emb);
                for (int i = 0; i < 128; i++)
                    dv[i] = bb.getDouble();
                return dv;
            } else {
                // Try generic float parse
                int n = emb.length / 4;
                float[] fv = new float[n];
                ByteBuffer bb = ByteBuffer.wrap(emb);
                for (int i = 0; i < n; i++)
                    fv[i] = bb.getFloat();
                double[] dv = new double[n];
                for (int i = 0; i < n; i++)
                    dv[i] = fv[i];
                return dv;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void normalizeL2InPlace(double[] v) {
        double norm = 0.0;
        for (double x : v)
            norm += x * x;
        norm = Math.sqrt(Math.max(norm, 1e-12));
        for (int i = 0; i < v.length; i++)
            v[i] /= norm;
    }

    /**
     * Detect faces using DNN (same approach as enrollment for consistency).
     * Returns list of face rectangles with confidence >= 0.5.
     */
    private List<Rect> detectFacesWithDNN(Mat frame) {
        List<Rect> faces = new ArrayList<>();

        if (dnnFaceDetector == null || frame.empty()) {
            return faces;
        }

        try {
            // Create blob from image
            Size size = new Size(300, 300);
            // Fixed: Correct BGR mean values (must match FaceDetection.java for
            // consistency)
            Mat blob = Dnn.blobFromImage(frame, 1.0, size, new Scalar(104.0, 117.0, 123.0));

            dnnFaceDetector.setInput(blob);
            Mat detections = dnnFaceDetector.forward();

            // Convert to float
            Mat detectionsFloat = new Mat();
            detections.convertTo(detectionsFloat, CvType.CV_32F);

            int numDetections = (int) detectionsFloat.size(2);
            int dims = (int) detectionsFloat.size(3);

            for (int i = 0; i < numDetections; i++) {
                float[] detection = new float[dims];
                detectionsFloat.get(new int[] { 0, 0, i, 0 }, detection);

                float confidence = detection[2];
                if (confidence > 0.5) { // Same threshold as enrollment
                    int x1 = (int) Math.round(detection[3] * frame.cols());
                    int y1 = (int) Math.round(detection[4] * frame.rows());
                    int x2 = (int) Math.round(detection[5] * frame.cols());
                    int y2 = (int) Math.round(detection[6] * frame.rows());

                    // Clamp to frame boundaries
                    x1 = Math.max(0, Math.min(x1, frame.cols() - 1));
                    y1 = Math.max(0, Math.min(y1, frame.rows() - 1));
                    x2 = Math.max(0, Math.min(x2, frame.cols() - 1));
                    y2 = Math.max(0, Math.min(y2, frame.rows() - 1));

                    if (x2 > x1 && y2 > y1) {
                        int width = x2 - x1;
                        int height = y2 - y1;

                        // Size validation (same as enrollment)
                        if (width >= 50 && width <= 400 && height >= 50 && height <= 400) {
                            Rect face = new Rect(x1, y1, width, height);
                            faces.add(face);
                        }
                    }
                }
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
        // Initialize DNN face detector (same as enrollment for consistency)
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

        // Initialize camera with fallback strategy
        capture = openCameraWithFallback();
        if (!capture.isOpened()) {
            AppLogger.error("Error opening webcam with all backends!");
            throw new RuntimeException("Camera Initialization failed.");
        }

        // Configure camera settings
        configureCamera(capture);
        AppLogger.info("Camera initialized successfully");
    }

    /**
     * Opens camera with multiple backend fallbacks to handle DSHOW errors.
     * Tries DSHOW -> MSMF -> Any available backend.
     */
    private VideoCapture openCameraWithFallback() {
        int cameraIndex = AppConfig.getInstance().getCameraIndex();

        // Try DirectShow first (preferred on Windows, avoids MSMF grabFrame errors)
        AppLogger.info("Attempting to open camera with DSHOW backend...");
        VideoCapture cap = new VideoCapture(cameraIndex, org.opencv.videoio.Videoio.CAP_DSHOW);
        if (cap.isOpened()) {
            AppLogger.info("Camera opened successfully with DSHOW backend");
            return cap;
        }
        AppLogger.warn("DSHOW backend failed, trying MSMF...");

        // Fallback to MSMF (Microsoft Media Foundation)
        cap.release();
        cap = new VideoCapture(cameraIndex, org.opencv.videoio.Videoio.CAP_MSMF);
        if (cap.isOpened()) {
            AppLogger.info("Camera opened successfully with MSMF backend");
            return cap;
        }
        AppLogger.warn("MSMF backend failed, trying any available backend...");

        // Last resort: let OpenCV choose any available backend
        cap.release();
        cap = new VideoCapture(cameraIndex);
        if (cap.isOpened()) {
            AppLogger.info("Camera opened successfully with default backend");
        } else {
            AppLogger.error("All camera backends failed!");
        }
        return cap;
    }

    /**
     * Configures camera resolution and codec settings for optimal performance.
     */
    private void configureCamera(VideoCapture cap) {
        try {
            cap.set(org.opencv.videoio.Videoio.CAP_PROP_FRAME_WIDTH, 640);
            cap.set(org.opencv.videoio.Videoio.CAP_PROP_FRAME_HEIGHT, 480);
            cap.set(org.opencv.videoio.Videoio.CAP_PROP_FPS, 30);

            // Use MJPEG codec for better performance
            cap.set(org.opencv.videoio.Videoio.CAP_PROP_FOURCC,
                    VideoWriter_fourcc('M', 'J', 'P', 'G'));

            AppLogger.info("Camera configured: 640x480 @ 30fps with MJPEG codec");
        } catch (Exception e) {
            AppLogger.warn("Camera configuration failed (non-critical): " + e.getMessage());
        }
    }

    /**
     * Helper to create FOURCC code for video codec.
     */
    private static int VideoWriter_fourcc(char c1, char c2, char c3, char c4) {
        return ((int) c1) | (((int) c2) << 8) | (((int) c3) << 16) | (((int) c4) << 24);
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
                // Detect faces using DNN (consistent with enrollment)
                List<Rect> detectedFaces = detectFacesWithDNN(webcamFrame);

                for (Rect rect : detectedFaces) {
                    // Draw rectangle
                    Imgproc.rectangle(webcamFrame, new Point(rect.x, rect.y),
                            new Point(rect.x + rect.width, rect.y + rect.height),
                            new Scalar(0, 255, 0), 2);

                    // Crop from color frame - keep as COLOR for embedding generation
                    Mat faceColor = webcamFrame.submat(rect);

                    try {
                        // GLASSES-AWARE: Reduce glare and specular highlights from glasses
                        Mat glareReduced = imageProcessor.reduceGlare(faceColor);

                        // Quality gate - relaxed to allow more faces through
                        ImageProcessor.ImageQualityResult q = imageProcessor.validateImageQualityDetailed(glareReduced);
                        // Only reject severely poor quality (score < 30)
                        boolean severelyPoor = q.getQualityScore() < 30.0;

                        // Generate embedding directly from COLOR image
                        // The FaceEmbeddingGenerator expects color images and handles preprocessing
                        // internally
                        Mat aligned = imageProcessor.correctFaceOrientation(glareReduced);
                        byte[] queryEmbedding = embGen.generateEmbedding(aligned);
                        aligned.release();
                        glareReduced.release();

                        // Skip only if severely poor quality AND embedding generation failed
                        if (severelyPoor && queryEmbedding == null) {
                            Imgproc.putText(webcamFrame, "unknown", new Point(rect.x, rect.y - 10),
                                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.9, new Scalar(0, 0, 255), 2);
                            continue;
                        }

                        // update temporal embedding buffer
                        if (recentQueryEmbeddings.size() == Q_EMB_WINDOW) {
                            recentQueryEmbeddings.pollFirst();
                        }
                        recentQueryEmbeddings.offerLast(queryEmbedding);
                        byte[] smoothedEmbedding = buildSmoothedEmbedding();

                        // Compare with stored embeddings per person by fusing centroid and exemplar
                        // similarities
                        ArrayList<Double> personScores = new ArrayList<>();
                        for (int p = 0; p < personEmbeddings.size(); p++) {
                            List<byte[]> person = personEmbeddings.get(p);
                            if (person == null || person.isEmpty()) {
                                personScores.add(0.0);
                                continue;
                            }
                            double[] centroid = (p < personCentroids.size()) ? personCentroids.get(p) : null;
                            double fusedRaw = computeFusedScore(queryEmbedding, person, centroid);
                            double fusedSmooth = smoothedEmbedding != null
                                    ? computeFusedScore(smoothedEmbedding, person, centroid)
                                    : 0.0;
                            // Use max of raw and smoothed without quality boost (bias removal)
                            personScores.add(Math.max(fusedRaw, fusedSmooth));
                        }

                        String displayText;
                        int bestIdx = -1;
                        if (personScores.isEmpty()) {
                            displayText = "unknown";
                        } else {
                            // Find top-2 scores for discriminative decision
                            int maxIdx = 0;
                            for (int i = 1; i < personScores.size(); i++) {
                                if (personScores.get(i) > personScores.get(maxIdx)) {
                                    maxIdx = i;
                                }
                            }
                            bestIdx = maxIdx;

                            double best = personScores.get(bestIdx);
                            double second = 0.0;
                            for (int i = 0; i < personScores.size(); i++) {
                                if (i == bestIdx)
                                    continue;
                                if (personScores.get(i) > second)
                                    second = personScores.get(i);
                            }

                            // Get person-specific discriminative thresholds
                            double absThresh = bestIdx < personAbsoluteThresholds.size()
                                    ? personAbsoluteThresholds.get(bestIdx)
                                    : 0.75;
                            double margin = bestIdx < personRelativeMargins.size()
                                    ? personRelativeMargins.get(bestIdx)
                                    : 0.10;

                            // Calculate negative evidence: how much does query match OTHER persons?
                            double negativeEvidence = 0.0;
                            int negCount = 0;
                            for (int i = 0; i < personScores.size(); i++) {
                                if (i != bestIdx) {
                                    negativeEvidence += personScores.get(i);
                                    negCount++;
                                }
                            }
                            double avgNegative = negCount > 0 ? negativeEvidence / negCount : 0.0;

                            // Discriminative score with penalty for matching wrong persons
                            double discriminativeScore = best - (PENALTY_WEIGHT * avgNegative);

                            // Relative margin as percentage of best score (more robust than absolute)
                            double relativeMarginPct = best > 0 ? (best - second) / best : 0.0;
                            double requiredMarginPct = Math.max(MIN_RELATIVE_MARGIN_PCT, margin / best);

                            // Debug logging: show all person scores for transparency
                            StringBuilder allScores = new StringBuilder("[Recognition] Scores: ");
                            for (int i = 0; i < personScores.size(); i++) {
                                String personName = i < personLabels.size() ? personLabels.get(i) : "P" + i;
                                allScores.append(String.format("%s=%.3f ", personName, personScores.get(i)));
                            }
                            double tightness = bestIdx < personTightness.size() ? personTightness.get(bestIdx) : 0.0;
                            double stdDev = bestIdx < personStdDevs.size() ? personStdDevs.get(bestIdx) : 0.0;
                            String bestName = bestIdx < personLabels.size() ? personLabels.get(bestIdx) : "unknown";
                            System.out.println(allScores.toString());
                            System.out.printf("[Decision] Best=%s(%.3f), 2nd=%.3f, Discriminative=%.3f, AvgNeg=%.3f%n",
                                    bestName, best, second, discriminativeScore, avgNegative);
                            System.out.printf(
                                    "[Thresholds] Abs=%.3f, Margin=%.3f(%.1f%%), RelMargin=%.1f%%, Tightness=%.3f, StdDev=%.3f%n",
                                    absThresh, margin, requiredMarginPct * 100, relativeMarginPct * 100, tightness,
                                    stdDev);

                            // Multi-criteria discriminative decision
                            boolean absolutePass = best >= absThresh;
                            boolean absoluteMarginPass = (best - second) >= margin;
                            boolean relativeMarginPass = relativeMarginPct >= requiredMarginPct;
                            boolean discriminativePass = discriminativeScore >= absThresh;
                            boolean consistent = isConsistent(bestIdx);

                            boolean accept = false;
                            // Tier 1: Strong discrimination - both absolute quality AND margin separation
                            if (absolutePass && (absoluteMarginPass || relativeMarginPass)) {
                                accept = true;
                                System.out.println("[Decision] ACCEPT: Tier 1 - Absolute + Margin");
                            }
                            // Tier 2: Discriminative scoring - penalized score still good + consistency
                            else if (discriminativePass && relativeMarginPass && consistent) {
                                accept = true;
                                System.out.println(
                                        "[Decision] ACCEPT: Tier 2 - Discriminative + RelMargin + Consistency");
                            }
                            // Tier 3: Very strong consistency with good discriminative score
                            else if (discriminativePass && consistent
                                    && recentPredictions.size() >= CONSISTENCY_WINDOW - 1) {
                                int strongCount = 0;
                                for (Integer v : recentPredictions) {
                                    if (v != null && v == bestIdx)
                                        strongCount++;
                                }
                                // Require very strong consistency (6 out of last 7-8 frames)
                                if (strongCount >= CONSISTENCY_MIN_COUNT + 1) {
                                    accept = true;
                                    System.out.println(
                                            "[Decision] ACCEPT: Tier 3 - Strong Consistency (" + strongCount
                                                    + " frames)");
                                } else {
                                    System.out.printf("[Decision] REJECT: Weak consistency (%d frames)%n", strongCount);
                                }
                            } else {
                                System.out.printf(
                                        "[Decision] REJECT: Abs=%s, AbsMargin=%s, RelMargin=%s, Discrim=%s, Consist=%s%n",
                                        absolutePass, absoluteMarginPass, relativeMarginPass, discriminativePass,
                                        consistent);
                            }

                            if (accept) {
                                String label = bestIdx < personLabels.size()
                                        ? personLabels.get(bestIdx)
                                        : new File(folder_names.get(bestIdx)).getName();
                                displayText = label;

                            } else {
                                displayText = "unknown";
                            }
                        }

                        updateRecentPredictions("unknown".equals(displayText) ? -1 : bestIdx);
                        Imgproc.putText(webcamFrame, displayText, new Point(rect.x, rect.y - 10),
                                Imgproc.FONT_HERSHEY_SIMPLEX, 0.9,
                                "unknown".equals(displayText) ? new Scalar(0, 0, 255) : new Scalar(15, 255, 15), 2);

                    } finally {
                        // CRITICAL: Always release the submat to prevent memory leak
                        faceColor.release();
                    }
                }
                cameraPanel.displayMat(webcamFrame);
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

            }
            // ... stopRecognitionLoop cleanup ...
            stopRecognitionLoop();
        });
        recognitionThread.start();
    }

    // Build smoothed embedding from recent query embeddings (EMA-like average)
    private byte[] buildSmoothedEmbedding() {
        if (recentQueryEmbeddings.size() < 2)
            return null;
        // average all in double space, normalize, then encode back
        double[] sum = null;
        for (byte[] b : recentQueryEmbeddings) {
            double[] v = decodeEmbeddingToDouble(b);
            if (v == null)
                continue;
            normalizeL2InPlace(v);
            if (sum == null) {
                sum = Arrays.copyOf(v, v.length);
            } else {
                for (int i = 0; i < v.length; i++)
                    sum[i] += v[i];
            }
        }
        if (sum == null)
            return null;
        for (int i = 0; i < sum.length; i++)
            sum[i] /= recentQueryEmbeddings.size();
        normalizeL2InPlace(sum);
        return encodeEmbeddingFromDouble(sum);
    }

    private byte[] encodeEmbeddingFromDouble(double[] v) {
        try {
            if (embGen.isDeepLearningAvailable()) {
                // 128 floats
                ByteBuffer bb = ByteBuffer.allocate(128 * 4);
                for (int i = 0; i < 128 && i < v.length; i++)
                    bb.putFloat((float) v[i]);
                return bb.array();
            } else {
                // 128 doubles
                ByteBuffer bb = ByteBuffer.allocate(128 * 8);
                for (int i = 0; i < 128 && i < v.length; i++)
                    bb.putDouble(v[i]);
                return bb.array();
            }
        } catch (Exception e) {
            return null;
        }
    }

    // Compute fused score for a person given a query embedding
    private double computeFusedScore(byte[] queryEmb, List<byte[]> person, double[] centroid) {
        if (person == null || person.isEmpty())
            return 0.0;
        double[] sims = new double[person.size()];
        for (int i = 0; i < person.size(); i++) {
            sims[i] = embGen.calculateSimilarity(queryEmb, person.get(i));
        }
        Arrays.sort(sims); // ascending
        int k = Math.min(TOP_K, sims.length);
        double sumTopK = 0.0;
        double[] topK = new double[k];
        for (int i = 0; i < k; i++) {
            double val = sims[sims.length - 1 - i];
            topK[i] = val;
            sumTopK += val;
        }
        double avgTopK = sumTopK / k;
        double medianTopK = (k % 2 == 1) ? topK[k / 2] : (topK[k / 2 - 1] + topK[k / 2]) / 2.0;
        double maxTopK = topK[0]; // best exemplar
        // Weight: max gets more influence to reduce false negatives
        double exemplarScore = 0.50 * maxTopK + 0.35 * avgTopK + 0.15 * medianTopK;

        double centroidScore = 0.0;
        if (centroid != null) {
            centroidScore = cosineSimilarity(queryEmb, centroid);
        }
        int n = sims.length;
        // Increase centroid weight for larger clusters (more stable)
        double alpha = Math.min(0.70, Math.max(0.35, 0.35 + 0.03 * Math.max(0, n - 5)));
        return alpha * centroidScore + (1.0 - alpha) * exemplarScore;
    }

    private void updateRecentPredictions(int idx) {
        if (recentPredictions.size() == CONSISTENCY_WINDOW) {
            recentPredictions.pollFirst();
        }
        recentPredictions.offerLast(idx);
    }

    private boolean isConsistent(int idx) {
        if (idx < 0)
            return false;
        int count = 0;
        for (Integer v : recentPredictions) {
            if (v != null && v == idx)
                count++;
        }
        return count >= CONSISTENCY_MIN_COUNT;
    }

    // Compute cosine similarity between bytes embedding and a centroid vector
    private double cosineSimilarity(byte[] queryEmb, double[] centroid) {
        double[] q = decodeEmbeddingToDouble(queryEmb);
        if (q == null || centroid == null || q.length != centroid.length)
            return 0.0;
        normalizeL2InPlace(q);
        double dot = 0.0, n1 = 0.0, n2 = 0.0;
        for (int i = 0; i < q.length; i++) {
            dot += q[i] * centroid[i];
            n1 += q[i] * q[i];
            n2 += centroid[i] * centroid[i];
        }
        n1 = Math.sqrt(Math.max(n1, 1e-12));
        n2 = Math.sqrt(Math.max(n2, 1e-12));
        return dot / (n1 * n2);
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
                // Instantiate the JFrame, which triggers all initialization (loading model,
                // starting camera).
                new NewFaceRecognitionDemo();
            } catch (Exception e) {
                AppLogger.error("Failed to launch FaceRecognitionDemo: " + e.getMessage());
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "The Recognition Demo failed to start. See logs for details.",
                        "Startup Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static String buildDisplayLabel(String folderName) {
        if (folderName == null || folderName.isEmpty()) {
            return "unknown";
        }

        String[] parts = folderName.split("_", 2);
        if (parts.length == 2) {
            String studentId = parts[0].trim();
            String studentName = parts[1].trim();
            if (!studentId.isEmpty() && !studentName.isEmpty()) {
                return studentId + " - " + studentName;
            }
        }

        return folderName;
    }

}