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
import java.facecrop.LiveRecognitionPreprocessor;

import javax.swing.*;

import java.awt.*;
import java.io.File;

import java.util.ArrayList;
import java.util.List;

public class NewFaceRecognitionDemo extends JFrame implements IConfigChangeListener {

    private final CameraPanel cameraPanel = new CameraPanel();
    private VideoCapture capture;
    private Net dnnFaceDetector;
    private ArrayList<List<byte[]>> personEmbeddings = new ArrayList<>();
    private ArrayList<double[]> personCentroids = new ArrayList<>();
    private ArrayList<String> folder_names = new ArrayList<>();
    private ArrayList<String> personLabels = new ArrayList<>();

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

    private int frameCounter = 0;

    private final ImageProcessor imageProcessor = new ImageProcessor();
    private final FaceEmbeddingGenerator embGen = new FaceEmbeddingGenerator();
    private final LiveRecognitionPreprocessor livePreprocessor = new LiveRecognitionPreprocessor();

    private final int TOP_K = 5;

    private final int CONSISTENCY_WINDOW = 7;
    private final int CONSISTENCY_MIN_COUNT = 5;
    private final Deque<Integer> recentPredictions = new ArrayDeque<>(CONSISTENCY_WINDOW);
    private final int Q_EMB_WINDOW = 5;
    private final Deque<byte[]> recentQueryEmbeddings = new ArrayDeque<>(Q_EMB_WINDOW);

    private final double PENALTY_WEIGHT = 0.20;
    private final double MIN_RELATIVE_MARGIN_PCT = 0.10;
    private final double STRONG_MARGIN_THRESHOLD = 0.12;

    private final double CENTROID_PREFILTER_THRESHOLD = 0.0;

    private String lastDisplayText = "";
    private Scalar lastDisplayColor = new Scalar(0, 0, 255);

    private ArrayList<Double> personTightness = new ArrayList<>();
    private ArrayList<Double> personAbsoluteThresholds = new ArrayList<>();
    private ArrayList<Double> personRelativeMargins = new ArrayList<>();
    private ArrayList<Double> personStdDevs = new ArrayList<>();

    static {

        System.load(new File("lib/opencv_java480.dll").getAbsolutePath());
    }

    public NewFaceRecognitionDemo() {
        super("Real-Time Face Recognition - Configurable Detection");

        initializeModelData();
        initializeOpenCV();

        setLayout(new BorderLayout());

        FaceCropSettingsPanel settingsPanel = new FaceCropSettingsPanel(this, false);

        add(cameraPanel, BorderLayout.CENTER);
        add(settingsPanel, BorderLayout.EAST);

        setupFrameAndListener();
        startRecognitionLoop();
    }

    private void initializeModelData() {

        folder_names.clear();
        personLabels.clear();
        String image_folder_path = AppConfig.getInstance().getDatabaseStoragePath();
        File folder_directories = new File(image_folder_path);

        File[] list_files = folder_directories.listFiles();
        if (list_files != null) {
            for (File file : list_files) {

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
                        // FIXED: Validate embeddings before loading
                        if (emb != null && emb.length > 0) {
                            if (!embGen.isEmbeddingValid(emb)) {
                                AppLogger.warn("Skipping invalid embedding file: " + f.getName());
                                continue;
                            }
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

            double baseAbsolute = deep ? 0.60 : 0.55;
            double baseMargin = deep ? 0.10 : 0.12;

            boolean likelyHasGlasses = stdDev > 0.12;

            if (likelyHasGlasses) {

                double relaxationFactor = Math.max(0.88, 0.95 - (stdDev * 0.5));
                baseAbsolute *= relaxationFactor;
                baseMargin *= 0.85;
                System.out.println("  [Glasses Mode] Detected high variation (stdDev=" + String.format("%.3f", stdDev)
                        + ") - relaxed thresholds by " + String.format("%.1f", (1.0 - relaxationFactor) * 100) + "%");
            }

            double absoluteThreshold = baseAbsolute + ((1.0 - tightness) * 0.10);
            double relativeMargin = baseMargin + ((1.0 - tightness) * 0.10);

            personAbsoluteThresholds.add(absoluteThreshold);
            personRelativeMargins.add(relativeMargin);

            AppLogger.info(String.format("Person %s: tightness=%.3f, stdDev=%.3f, absThresh=%.3f, margin=%.3f",
                    buildDisplayLabel(new File(folder).getName()), tightness, stdDev, absoluteThreshold,
                    relativeMargin));
        }
    }

    private double computeTightness(List<byte[]> embList) {
        if (embList == null || embList.size() < 2)
            return 1.0;
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

    private double[] computeCentroid(List<byte[]> embList) {
        if (embList == null || embList.isEmpty())
            return null;
        double[] sum = null;
        int count = 0;
        for (byte[] b : embList) {
            double[] v = decodeEmbeddingToDouble(b);
            if (v == null)
                continue;

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
            if (emb.length == 128 * 4) {
                float[] fv = new float[128];
                ByteBuffer bb = ByteBuffer.wrap(emb);
                for (int i = 0; i < 128; i++)
                    fv[i] = bb.getFloat();
                double[] dv = new double[128];
                for (int i = 0; i < 128; i++)
                    dv[i] = fv[i];
                return dv;
            } else if (emb.length == 128 * 8) {
                double[] dv = new double[128];
                ByteBuffer bb = ByteBuffer.wrap(emb);
                for (int i = 0; i < 128; i++)
                    dv[i] = bb.getDouble();
                return dv;
            } else {

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

    private List<Rect> detectFacesWithDNN(Mat frame) {
        List<Rect> faces = new ArrayList<>();

        if (dnnFaceDetector == null || frame.empty()) {
            return faces;
        }

        try {

            Size size = new Size(300, 300);

            Mat blob = Dnn.blobFromImage(frame, 1.0, size, new Scalar(104.0, 117.0, 123.0));

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
                if (confidence > 0.5) {
                    int x1 = (int) Math.round(detection[3] * frame.cols());
                    int y1 = (int) Math.round(detection[4] * frame.rows());
                    int x2 = (int) Math.round(detection[5] * frame.cols());
                    int y2 = (int) Math.round(detection[6] * frame.rows());

                    x1 = Math.max(0, Math.min(x1, frame.cols() - 1));
                    y1 = Math.max(0, Math.min(y1, frame.rows() - 1));
                    x2 = Math.max(0, Math.min(x2, frame.cols() - 1));
                    y2 = Math.max(0, Math.min(y2, frame.rows() - 1));

                    if (x2 > x1 && y2 > y1) {
                        int width = x2 - x1;
                        int height = y2 - y1;

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
        int cameraIndex = AppConfig.getInstance().getCameraIndex();

        AppLogger.info("Attempting to open camera with DSHOW backend...");
        VideoCapture cap = new VideoCapture(cameraIndex, org.opencv.videoio.Videoio.CAP_DSHOW);
        if (cap.isOpened()) {
            AppLogger.info("Camera opened successfully with DSHOW backend");
            return cap;
        }
        AppLogger.warn("DSHOW backend failed, trying MSMF...");

        cap.release();
        cap = new VideoCapture(cameraIndex, org.opencv.videoio.Videoio.CAP_MSMF);
        if (cap.isOpened()) {
            AppLogger.info("Camera opened successfully with MSMF backend");
            return cap;
        }
        AppLogger.warn("MSMF backend failed, trying any available backend...");

        cap.release();
        cap = new VideoCapture(cameraIndex);
        if (cap.isOpened()) {
            AppLogger.info("Camera opened successfully with default backend");
        } else {
            AppLogger.error("All camera backends failed!");
        }
        return cap;
    }

    private void configureCamera(VideoCapture cap) {
        try {
            cap.set(org.opencv.videoio.Videoio.CAP_PROP_FRAME_WIDTH, 640);
            cap.set(org.opencv.videoio.Videoio.CAP_PROP_FRAME_HEIGHT, 480);
            cap.set(org.opencv.videoio.Videoio.CAP_PROP_FPS, 30);

            cap.set(org.opencv.videoio.Videoio.CAP_PROP_FOURCC,
                    VideoWriter_fourcc('M', 'J', 'P', 'G'));

            AppLogger.info("Camera configured: 640x480 @ 30fps with MJPEG codec");
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

                int frameSkip = getAdaptiveFrameSkip();
                boolean shouldProcess = (frameCounter % frameSkip == 0);

                List<Rect> detectedFaces = detectFacesWithDNN(webcamFrame);

                for (Rect rect : detectedFaces) {

                    Imgproc.rectangle(webcamFrame, new Point(rect.x, rect.y),
                            new Point(rect.x + rect.width, rect.y + rect.height),
                            new Scalar(0, 255, 0), 2);

                    Imgproc.putText(webcamFrame, lastDisplayText, new Point(rect.x, rect.y - 10),
                            Imgproc.FONT_HERSHEY_SIMPLEX, 0.9, lastDisplayColor, 2);

                    if (!shouldProcess) {
                        continue;
                    }

                    Rect paddedRect = buildSquareRegionWithPadding(webcamFrame.size(), rect, 0.12);
                    Mat faceColor = new Mat(webcamFrame, paddedRect).clone();

                    try {
       
                        app.util.ImageProcessor.ImageQualityResult qualityResult = imageProcessor
                                .validateImageQualityDetailed(faceColor);
                        if (!qualityResult.isGoodQuality()) {
                            lastDisplayText = "unknown";
                            lastDisplayColor = new Scalar(0, 0, 255);
                            AppLogger
                                    .info("[Reject] Face rejected: Poor image quality. " + qualityResult.getFeedback());
                            faceColor.release();
                            continue;
                        }

                        // --- RECOGNITION ---
                        Mat preprocessed = embGen.preprocessFaceForLiveRecognition(faceColor);
                        faceColor.release();

                        // Generate embedding from preprocessed face
                        byte[] queryEmbedding = embGen.generateEmbedding(preprocessed);
                        preprocessed.release();

                        if (queryEmbedding == null) {
                            lastDisplayText = "unknown";
                            lastDisplayColor = new Scalar(0, 0, 255);
                            AppLogger.info("[Reject] Face rejected: Embedding generation failed.");
                            continue;
                        }

                        if (recentQueryEmbeddings.size() == Q_EMB_WINDOW) {
                            recentQueryEmbeddings.pollFirst();
                        }
                        recentQueryEmbeddings.offerLast(queryEmbedding);
                        byte[] smoothedEmbedding = buildSmoothedEmbedding();

                        ArrayList<Double> personScores = new ArrayList<>();
                        int prefilterSkipped = 0;

                        for (int p = 0; p < personEmbeddings.size(); p++) {
                            List<byte[]> person = personEmbeddings.get(p);
                            if (person == null || person.isEmpty()) {
                                personScores.add(0.0);
                                continue;
                            }

                            double[] centroid = (p < personCentroids.size()) ? personCentroids.get(p) : null;

                            if (centroid != null) {
                                double centroidScore = cosineSimilarity(queryEmbedding, centroid);
                                if (centroidScore < CENTROID_PREFILTER_THRESHOLD) {
                                    personScores.add(0.0);
                                    prefilterSkipped++;
                                    continue;
                                }
                            }

                            double fusedRaw = computeFusedScore(queryEmbedding, person, centroid);
                            double fusedSmooth = smoothedEmbedding != null
                                    ? computeFusedScore(smoothedEmbedding, person, centroid)
                                    : 0.0;

                            personScores.add(Math.max(fusedRaw, fusedSmooth));
                        }

                        String displayText;
                        int bestIdx = -1;
                        if (personScores.isEmpty()) {
                            displayText = "unknown";
                            AppLogger.info("[Reject] Face rejected: No person scores available.");
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
                                if (i == bestIdx)
                                    continue;
                                if (personScores.get(i) > second)
                                    second = personScores.get(i);
                            }

                            double absThresh = bestIdx < personAbsoluteThresholds.size()
                                    ? personAbsoluteThresholds.get(bestIdx)
                                    : 0.75;
                            double margin = bestIdx < personRelativeMargins.size()
                                    ? personRelativeMargins.get(bestIdx)
                                    : 0.10;

                            double negativeEvidence = 0.0;
                            int negCount = 0;
                            for (int i = 0; i < personScores.size(); i++) {
                                if (i != bestIdx) {
                                    negativeEvidence += personScores.get(i);
                                    negCount++;
                                }
                            }
                            double avgNegative = negCount > 0 ? negativeEvidence / negCount : 0.0;

                            double discriminativeScore = best - (PENALTY_WEIGHT * avgNegative);
                            double relativeMarginPct = best > 0 ? (best - second) / best : 0.0;
                            double requiredMarginPct = Math.max(MIN_RELATIVE_MARGIN_PCT, margin / best);

                            StringBuilder allScores = new StringBuilder("[Recognition] Scores: ");
                            for (int i = 0; i < personScores.size(); i++) {
                                String personName = i < personLabels.size() ? personLabels.get(i) : "P" + i;
                                allScores.append(String.format("%s=%.3f ", personName, personScores.get(i)));
                            }
                            double tightness = bestIdx < personTightness.size() ? personTightness.get(bestIdx) : 0.0;
                            double stdDev = bestIdx < personStdDevs.size() ? personStdDevs.get(bestIdx) : 0.0;
                            String bestName = bestIdx < personLabels.size() ? personLabels.get(bestIdx) : "unknown";
                            AppLogger.info(allScores.toString());
                            AppLogger.info(String.format(
                                    "[Decision] Best=%s(%.3f), 2nd=%.3f, Discriminative=%.3f, AvgNeg=%.3f",
                                    bestName, best, second, discriminativeScore, avgNegative));
                            AppLogger.info(String.format(
                                    "[Thresholds] Abs=%.3f, Margin=%.3f(%.1f%%), RelMargin=%.1f%%, Tightness=%.3f, StdDev=%.3f",
                                    absThresh, margin, requiredMarginPct * 100, relativeMarginPct * 100, tightness,
                                    stdDev));

                            if (prefilterSkipped > 0) {
                                AppLogger.info(
                                        String.format("[Performance] Pre-filter skipped %d/%d persons (%.1f%% speedup)",
                                                prefilterSkipped, personScores.size(),
                                                (prefilterSkipped * 100.0) / personScores.size()));
                            }

                            boolean absolutePass = best >= absThresh;
                            boolean absoluteMarginPass = (best - second) >= margin;
                            boolean relativeMarginPass = relativeMarginPct >= requiredMarginPct;
                            boolean strongMarginPass = relativeMarginPct >= STRONG_MARGIN_THRESHOLD;
                            boolean discriminativePass = discriminativeScore >= absThresh;
                            boolean consistent = isConsistent(bestIdx);

                            int strongCount = 0;
                            for (Integer v : recentPredictions) {
                                if (v != null && v == bestIdx)
                                    strongCount++;
                            }

                            boolean primaryAccept = absolutePass && absoluteMarginPass && relativeMarginPass;
                            boolean strongMarginAccept = absolutePass && strongMarginPass;
                            boolean consistencyAccept = absolutePass && discriminativePass && consistent
                                    && strongCount >= CONSISTENCY_MIN_COUNT;

                            boolean accept = primaryAccept || strongMarginAccept || consistencyAccept;

                            // --- FINAL MINIMUM CONFIDENCE THRESHOLD ---
                            if (accept && best < 0.5) {
                                accept = false;
                                System.out.println("[Decision] REJECT: Below minimum confidence threshold (0.5)");
                            }

                            if (accept) {
                                if (primaryAccept) {
                                    System.out.println(
                                            "[Decision] ACCEPT: Primary thresholds met (abs + margin + relative)");
                                } else if (strongMarginAccept) {
                                    System.out.println("[Decision] ACCEPT: Strong margin hold (" +
                                            String.format("%.1f%%", relativeMarginPct * 100) + ")");
                                } else {
                                    System.out.println("[Decision] ACCEPT: Consistency override (" + strongCount + "/"
                                            + CONSISTENCY_WINDOW + " frames)");
                                }
                            } else {
                                System.out.printf(
                                        "[Decision] REJECT: Abs=%s, AbsMargin=%s, RelMargin=%s(%.1f%%), StrongMargin=%s(%.1f%%), Discrim=%s, Consist=%s(%d/%d)%n",
                                        absolutePass, absoluteMarginPass, relativeMarginPass, relativeMarginPct * 100,
                                        strongMarginPass, STRONG_MARGIN_THRESHOLD * 100, discriminativePass,
                                        consistent, strongCount, CONSISTENCY_WINDOW);
                            }

                            if (accept) {
                                String label = bestIdx < personLabels.size()
                                        ? personLabels.get(bestIdx)
                                        : new File(folder_names.get(bestIdx)).getName();

                                lastDisplayText = String.format("%s (%.2f)", label, best);
                                lastDisplayColor = new Scalar(15, 255, 15);

                            } else {
                                lastDisplayText = "unknown";
                                lastDisplayColor = new Scalar(0, 0, 255);
                            }
                        }

                        updateRecentPredictions("unknown".equals(lastDisplayText) ? -1 : bestIdx);

                    } finally {
                        // ...existing code...
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

            stopRecognitionLoop();
        });
        recognitionThread.start();
    }

    private byte[] buildSmoothedEmbedding() {
        if (recentQueryEmbeddings.size() < 2)
            return null;

        double[] sum = null;
        double totalWeight = 0.0;

        int size = recentQueryEmbeddings.size();
        for (int idx = 0; idx < size; idx++) {
            byte[] b = recentQueryEmbeddings.toArray(new byte[size][])[idx];
            double[] v = decodeEmbeddingToDouble(b);
            if (v == null)
                continue;

            double weight = (double) (idx + 1) / size;

            if (sum == null) {
                sum = new double[v.length];
            }

            for (int i = 0; i < v.length; i++) {
                sum[i] += v[i] * weight;
            }
            totalWeight += weight;
        }

        if (sum == null || totalWeight == 0)
            return null;

        for (int i = 0; i < sum.length; i++) {
            sum[i] /= totalWeight;
        }

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

    private double computeFusedScore(byte[] queryEmb, List<byte[]> person, double[] centroid) {
        if (person == null || person.isEmpty())
            return 0.0;

        double centroidScore = 0.0;
        if (centroid != null) {
            centroidScore = cosineSimilarity(queryEmb, centroid);
        }

        double[] sims = new double[person.size()];
        for (int i = 0; i < person.size(); i++) {
            sims[i] = embGen.calculateSimilarity(queryEmb, person.get(i));
        }
        Arrays.sort(sims);

        int k = Math.min(Math.max(5, sims.length / 3), sims.length);
        double sumTopK = 0.0;
        for (int i = 0; i < k; i++) {
            sumTopK += sims[sims.length - 1 - i];
        }
        double maxTopK = sims[sims.length - 1];
        double avgTopK = sumTopK / k;
        double exemplarScore = 0.75 * maxTopK + 0.25 * avgTopK;
        double fused = (centroid != null)
                ? (0.25 * centroidScore + 0.75 * exemplarScore)
                : exemplarScore;

        return Math.min(1.0, Math.max(fused, exemplarScore));
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

    private double cosineSimilarity(byte[] queryEmb, double[] centroid) {
        double[] q = decodeEmbeddingToDouble(queryEmb);
        if (q == null || centroid == null || q.length != centroid.length)
            return 0.0;

        normalizeL2InPlace(q);

        double dot = 0.0;
        for (int i = 0; i < q.length; i++) {
            dot += q[i] * centroid[i];
        }

        return Math.max(-1.0, Math.min(1.0, dot));
    }

    private void stopRecognitionLoop() {

        running = false;
        AppLogger.info("Exiting Face Recognition Demo...");

        if (recognitionThread != null) {
            recognitionThread.interrupt();

            try {

                recognitionThread.join(100);
            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();
            }
        }

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

    @Override
    public void onCaptureFaceRequested() {

        AppLogger.warn("Face capture requested, but not supported in FaceRecognitionDemo.");
    }

    @Override
    public void onSaveSettingsRequested() {

        AppConfig.getInstance().save();
    }

    public static void main(String[] args) {
        AppLogger.info("FaceRecognitionDemo Starting...");

        AppConfig.getInstance();

        SwingUtilities.invokeLater(() -> {
            try {

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

    private int getAdaptiveFrameSkip() {
        int numPeople = personEmbeddings.size();

        if (numPeople <= 5) {
            return 2;
        } else if (numPeople <= 20) {
            return 3;
        } else {
            return 4;
        }
    }

    private Rect buildSquareRegionWithPadding(Size frameSize, Rect face, double paddingRatio) {
        int frameWidth = (int) frameSize.width;
        int frameHeight = (int) frameSize.height;

        if (frameWidth <= 0 || frameHeight <= 0) {
            return face;
        }

        int centerX = face.x + face.width / 2;
        int centerY = face.y + face.height / 2;

        double safePadding = Math.max(0.0, paddingRatio);
        int paddedSize = (int) Math.round(Math.max(face.width, face.height) * (1.0 + safePadding));

        // Use same MIN_FACE_SIZE as training (96px)
        final int MIN_FACE_SIZE = 96;
        paddedSize = Math.max(paddedSize, MIN_FACE_SIZE);
        paddedSize = Math.min(paddedSize, Math.min(frameWidth, frameHeight));

        int halfSize = paddedSize / 2;

        int x = centerX - halfSize;
        int y = centerY - halfSize;

        x = Math.max(0, Math.min(x, frameWidth - paddedSize));
        y = Math.max(0, Math.min(y, frameHeight - paddedSize));

        return new Rect(x, y, paddedSize, paddedSize);
    }

}