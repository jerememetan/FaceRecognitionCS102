package facecrop;

import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
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
    private CascadeClassifier faceDetector;
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
    private final int TOP_K = 3; // average top-K similarities per person to reduce outliers
    private final double MARGIN_DEEP = 0.06; // slightly relaxed to reduce false negatives
    private final double MARGIN_FALLBACK = 0.08;
    private final int CONSISTENCY_WINDOW = 5; // frames to consider
    private final int CONSISTENCY_MIN_COUNT = 3; // required consistent predictions
    private final Deque<Integer> recentPredictions = new ArrayDeque<>(CONSISTENCY_WINDOW);
    private final int Q_EMB_WINDOW = 5; // smoothing window for query embeddings
    private final Deque<byte[]> recentQueryEmbeddings = new ArrayDeque<>(Q_EMB_WINDOW);

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
            personCentroids.add(computeCentroid(embList));
        }
    }

    // Compute normalized centroid for a person
    private double[] computeCentroid(List<byte[]> embList) {
        if (embList == null || embList.isEmpty()) return null;
        double[] sum = null;
        int count = 0;
        for (byte[] b : embList) {
            double[] v = decodeEmbeddingToDouble(b);
            if (v == null) continue;
            normalizeL2InPlace(v);
            if (sum == null) {
                sum = Arrays.copyOf(v, v.length);
            } else {
                for (int i = 0; i < v.length; i++) sum[i] += v[i];
            }
            count++;
        }
        if (sum == null || count == 0) return null;
        for (int i = 0; i < sum.length; i++) sum[i] /= count;
        normalizeL2InPlace(sum);
        return sum;
    }

    private double[] decodeEmbeddingToDouble(byte[] emb) {
        if (emb == null) return null;
        try {
            if (emb.length == 128 * 4) { // 128 floats
                float[] fv = new float[128];
                ByteBuffer bb = ByteBuffer.wrap(emb);
                for (int i = 0; i < 128; i++) fv[i] = bb.getFloat();
                double[] dv = new double[128];
                for (int i = 0; i < 128; i++) dv[i] = fv[i];
                return dv;
            } else if (emb.length == 128 * 8) { // 128 doubles
                double[] dv = new double[128];
                ByteBuffer bb = ByteBuffer.wrap(emb);
                for (int i = 0; i < 128; i++) dv[i] = bb.getDouble();
                return dv;
            } else {
                // Try generic float parse
                int n = emb.length / 4;
                float[] fv = new float[n];
                ByteBuffer bb = ByteBuffer.wrap(emb);
                for (int i = 0; i < n; i++) fv[i] = bb.getFloat();
                double[] dv = new double[n];
                for (int i = 0; i < n; i++) dv[i] = fv[i];
                return dv;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void normalizeL2InPlace(double[] v) {
        double norm = 0.0;
        for (double x : v) norm += x * x;
        norm = Math.sqrt(Math.max(norm, 1e-12));
        for (int i = 0; i < v.length; i++) v[i] /= norm;
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
                Imgproc.GaussianBlur(gray, gray,
                        new Size(PREPROCESSING_GAUSSIAN_KERNEL_SIZE, PREPROCESSING_GAUSSIAN_KERNEL_SIZE),
                        PREPROCESSING_GAUSSIAN_SIGMA_X);
                Imgproc.createCLAHE(PREPROCESSING_CLAHE_CLIP_LIMIT,
                        new Size(PREPROCESSING_CLAHE_GRID_SIZE, PREPROCESSING_CLAHE_GRID_SIZE)).apply(gray, gray);
                MatOfRect faces = new MatOfRect();
                // Detect faces using ADJUSTABLE detection settings
                faceDetector.detectMultiScale(gray, faces, DETECTION_SCALE_FACTOR, DETECTION_MIN_NEIGHBORS, 0,
                        new Size(DETECTION_MIN_SIZE_PX, DETECTION_MIN_SIZE_PX), new Size());

                for (Rect rect : faces.toArray()) {
                    // Draw rectangle
                    Imgproc.rectangle(webcamFrame, new Point(rect.x, rect.y),
                            new Point(rect.x + rect.width, rect.y + rect.height),
                            new Scalar(0, 255, 0), 2);

                    // Crop from color frame for richer preprocessing
                    Mat faceColor = webcamFrame.submat(rect);
                    // Quality gate and preprocessing
                    ImageProcessor.ImageQualityResult q = imageProcessor.validateImageQualityDetailed(faceColor);
                    if (!q.isGoodQuality()) {
                        // Skip low-quality detections to reduce false positives
                        Imgproc.putText(webcamFrame, "unknown", new Point(rect.x, rect.y - 10),
                                Imgproc.FONT_HERSHEY_SIMPLEX, 0.9, new Scalar(0, 0, 255), 2);
                        continue;
                    }

                    Mat preprocessed = imageProcessor.preprocessFaceImage(
                            imageProcessor.correctFaceOrientation(faceColor));
                    Imgproc.resize(preprocessed, preprocessed, new Size(RECOGNITION_CROP_SIZE_PX, RECOGNITION_CROP_SIZE_PX));
                    byte[] queryEmbedding = embGen.generateEmbedding(preprocessed);
                    // update temporal embedding buffer
                    if (recentQueryEmbeddings.size() == Q_EMB_WINDOW) {
                        recentQueryEmbeddings.pollFirst();
                    }
                    recentQueryEmbeddings.offerLast(queryEmbedding);
                    byte[] smoothedEmbedding = buildSmoothedEmbedding();

                    // Compare with stored embeddings per person by fusing centroid and exemplar similarities
                    ArrayList<Double> personScores = new ArrayList<>();
                    for (int p = 0; p < personEmbeddings.size(); p++) {
                        List<byte[]> person = personEmbeddings.get(p);
                        if (person == null || person.isEmpty()) {
                            personScores.add(0.0);
                            continue;
                        }
                        double[] centroid = (p < personCentroids.size()) ? personCentroids.get(p) : null;
                        double fusedRaw = computeFusedScore(queryEmbedding, person, centroid);
                        double fusedSmooth = smoothedEmbedding != null ? computeFusedScore(smoothedEmbedding, person, centroid) : 0.0;
                        personScores.add(Math.max(fusedRaw, fusedSmooth));
                    }

                    String displayText;
                    int bestIdx = -1;
                    if (personScores.isEmpty()) {
                        displayText = "unknown";
                    } else {
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
                            if (i == bestIdx) continue;
                            if (personScores.get(i) > second) second = personScores.get(i);
                        }
                        boolean deep = embGen.isDeepLearningAvailable();
                        double high = deep ? 0.90 : 0.85;
                        double soft = Math.max(RECOGNITION_THRESHOLD, deep ? 0.80 : 0.70);
                        double requiredMargin = deep ? MARGIN_DEEP : MARGIN_FALLBACK;

                        boolean accept = false;
                        if (best >= high) {
                            accept = true;
                        } else if (best >= soft && (best - second) >= requiredMargin) {
                            accept = true;
                        } else if (best >= (soft - 0.02) && isConsistent(bestIdx)) {
                            accept = true;
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
        if (recentQueryEmbeddings.size() < 2) return null;
        // average all in double space, normalize, then encode back
        double[] sum = null;
        for (byte[] b : recentQueryEmbeddings) {
            double[] v = decodeEmbeddingToDouble(b);
            if (v == null) continue;
            normalizeL2InPlace(v);
            if (sum == null) {
                sum = Arrays.copyOf(v, v.length);
            } else {
                for (int i = 0; i < v.length; i++) sum[i] += v[i];
            }
        }
        if (sum == null) return null;
        for (int i = 0; i < sum.length; i++) sum[i] /= recentQueryEmbeddings.size();
        normalizeL2InPlace(sum);
        return encodeEmbeddingFromDouble(sum);
    }

    private byte[] encodeEmbeddingFromDouble(double[] v) {
        try {
            if (embGen.isDeepLearningAvailable()) {
                // 128 floats
                ByteBuffer bb = ByteBuffer.allocate(128 * 4);
                for (int i = 0; i < 128 && i < v.length; i++) bb.putFloat((float) v[i]);
                return bb.array();
            } else {
                // 128 doubles
                ByteBuffer bb = ByteBuffer.allocate(128 * 8);
                for (int i = 0; i < 128 && i < v.length; i++) bb.putDouble(v[i]);
                return bb.array();
            }
        } catch (Exception e) {
            return null;
        }
    }

    // Compute fused score for a person given a query embedding
    private double computeFusedScore(byte[] queryEmb, List<byte[]> person, double[] centroid) {
        if (person == null || person.isEmpty()) return 0.0;
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
        double exemplarScore = 0.7 * avgTopK + 0.3 * medianTopK;

        double centroidScore = 0.0;
        if (centroid != null) {
            centroidScore = cosineSimilarity(queryEmb, centroid);
        }
        int n = sims.length;
        double alpha = Math.min(0.8, Math.max(0.4, 0.4 + 0.02 * Math.max(0, n - 5)));
        return alpha * centroidScore + (1.0 - alpha) * exemplarScore;
    }

    private void updateRecentPredictions(int idx) {
        if (recentPredictions.size() == CONSISTENCY_WINDOW) {
            recentPredictions.pollFirst();
        }
        recentPredictions.offerLast(idx);
    }

    private boolean isConsistent(int idx) {
        if (idx < 0) return false;
        int count = 0;
        for (Integer v : recentPredictions) {
            if (v != null && v == idx) count++;
        }
        return count >= CONSISTENCY_MIN_COUNT;
    }

    // Compute cosine similarity between bytes embedding and a centroid vector
    private double cosineSimilarity(byte[] queryEmb, double[] centroid) {
        double[] q = decodeEmbeddingToDouble(queryEmb);
        if (q == null || centroid == null || q.length != centroid.length) return 0.0;
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