package app.service;

import app.entity.Student;
import app.model.FaceData;
import app.model.FaceImage;
import app.util.ImageProcessor;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.dnn.Net;
import org.opencv.dnn.Dnn;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FaceDetection {
    private Net dnnFaceDetector;
    private VideoCapture camera;
    private ImageProcessor imageProcessor;
    private FaceEmbeddingGenerator embeddingGenerator;
    private FacialLandmarkDetector landmarkDetector;
    private final Object cameraLock = new Object(); // Synchronize camera access

    private static final double MIN_CONFIDENCE_SCORE = 0.55; // Increased from 0.5 for better quality
    private static final double MIN_FACE_SIZE = 50.0;
    private static final double MAX_FACE_SIZE = 400.0;
    private static final int CAPTURE_INTERVAL_MS = 900;
    private static final int CAPTURE_ATTEMPT_MULTIPLIER = 12;
    private static final boolean DEBUG_LOGS = Boolean.parseBoolean(
            System.getProperty("app.faceDetectionDebug", "false"));

    public FaceDetection() {
        logDebug("Initializing FaceDetection...");
        initializeDetectors();
        camera = new VideoCapture(0);
        imageProcessor = new ImageProcessor();
        embeddingGenerator = new FaceEmbeddingGenerator();
        landmarkDetector = new FacialLandmarkDetector();

        if (camera.isOpened()) {
            logDebug("Camera initialized successfully");
        } else {
            System.err.println("Camera failed to initialize");
        }
    }

    private void initializeDetectors() {
        try {
            System.loadLibrary("opencv_java480");
            logDebug("OpenCV library loaded");

            String modelConfiguration = "data/resources/opencv_face_detector.pbtxt";
            String modelWeights = "data/resources/opencv_face_detector_uint8.pb";

            if (new File(modelConfiguration).exists() && new File(modelWeights).exists()) {
                try {
                    dnnFaceDetector = Dnn.readNetFromTensorflow(modelWeights, modelConfiguration);
                    logDebug("DNN face detector loaded successfully");
                } catch (Exception e) {
                    System.err.println("DNN loading failed: " + e.getMessage());
                    dnnFaceDetector = null;
                }
            } else {
                System.err.println("DNN model files not found; face detection unavailable");
                dnnFaceDetector = null;
            }

        } catch (Exception e) {
            System.err.println("Face detector initialization failed: " + e.getMessage());
            throw new RuntimeException("Failed to initialize face detectors: " + e.getMessage(), e);
        }
    }

    public FaceDetectionResult detectFaceForPreview(Mat frame) {
        if (frame.empty()) {
            return new FaceDetectionResult(new ArrayList<>());
        }

        return detectFaceWithConfidence(frame);
    }

    private FaceDetectionResult detectFaceWithConfidence(Mat frame) {
        if (dnnFaceDetector != null) {
            return detectFaceWithDNN(frame);
        }

        return new FaceDetectionResult(new ArrayList<>());
    }

    private FaceDetectionResult detectFaceWithDNN(Mat frame) {
        Mat blob = null;
        Mat detections = null;
        Mat detectionsFloat = null;

        try {
            Size size = new Size(300, 300);
            // Fixed: Correct BGR mean values (was 177 for green, should be 117)
            blob = Dnn.blobFromImage(frame, 1.0, size, new Scalar(104.0, 117.0, 123.0));

            dnnFaceDetector.setInput(blob);
            detections = dnnFaceDetector.forward();

            // Convert to float just in case
            detectionsFloat = new Mat();
            detections.convertTo(detectionsFloat, CvType.CV_32F);

            int numDetections = (int) detectionsFloat.size(2);
            int dims = (int) detectionsFloat.size(3);

            List<FaceCandidate> faces = new ArrayList<>();

            for (int i = 0; i < numDetections; i++) {
                float[] detection = new float[dims];
                detectionsFloat.get(new int[] { 0, 0, i, 0 }, detection);

                float confidence = detection[2];
                if (confidence > MIN_CONFIDENCE_SCORE) {
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

                    if (width >= MIN_FACE_SIZE && width <= MAX_FACE_SIZE &&
                            height >= MIN_FACE_SIZE && height <= MAX_FACE_SIZE) {

                        Rect face = new Rect(x1, y1, width, height);
                        logDebug(String.format("DNN candidate accepted: conf=%.2f rect=[%d,%d,%d,%d]",
                                confidence, face.x, face.y, face.width, face.height));
                        faces.add(new FaceCandidate(face, confidence));
                    } else {
                        logDebug(String.format(
                                "DNN candidate rejected (size out of bounds): conf=%.2f width=%d height=%d",
                                confidence, width, height));
                    }
                } else {
                    logDebug(String.format("DNN candidate rejected due to confidence %.2f", confidence));
                }
            }

            return new FaceDetectionResult(faces);

        } catch (Exception e) {
            System.err.println("DNN detection failed: " + e.getMessage());
            e.printStackTrace();
            return new FaceDetectionResult(new ArrayList<>());
        } finally {
            // CRITICAL: Release Mat objects to prevent memory leak
            if (blob != null)
                blob.release();
            if (detections != null)
                detections.release();
            if (detectionsFloat != null)
                detectionsFloat.release();
        }
    }

    public Mat drawFaceOverlay(Mat frame, FaceDetectionResult result) {
        Mat debugFrame = frame.clone();

        if (result != null && result.hasValidFace()) {
            // Draw ONLY the best face to avoid visual confusion
            Rect face = result.getBestFace();
            double confidence = result.getBestConfidence();

            Scalar color;
            if (confidence >= 0.8) {
                color = new Scalar(0, 255, 0, 255); // Green - excellent
            } else if (confidence >= 0.6) {
                color = new Scalar(0, 255, 255, 255); // Yellow - acceptable
            } else {
                color = new Scalar(0, 0, 255, 255); // Red - poor
            }

            // Draw rectangle around best face
            Imgproc.rectangle(debugFrame, face.tl(), face.br(), color, 3);

            // Show confidence score
            String confidenceText = String.format("%.1f%%", confidence * 100);
            Point textPoint = new Point(face.x, Math.max(face.y - 10, 20));

            Imgproc.putText(debugFrame, confidenceText, textPoint,
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.8, color, 2);

        } else {
            // Only show "NO FACE" text if explicitly requested (not for every preview
            // frame)
            // This prevents flashing "NO FACE DETECTED" during brief detection gaps
            // The status label will show this information instead
        }

        return debugFrame;
    }

    public FaceCaptureResult captureAndStoreFaceImages(Student student, int numberOfImages,
            FaceCaptureCallback callback) {
        logDebug("Starting face capture for: " + student.getName());

        if (!camera.isOpened()) {
            System.err.println("Camera not opened");
            return new FaceCaptureResult(false, "Cannot open camera", new ArrayList<>(), 0, 0, new ArrayList<>());
        }

        Path folderPath = Paths.get(student.getFaceData().getFolderPath());
        try {
            Files.createDirectories(folderPath);
        } catch (Exception ex) {
            System.err.println("Failed to create face data directory: " + folderPath + " - " + ex.getMessage());
            return new FaceCaptureResult(false, "Cannot create storage directory", new ArrayList<>(), 0, 0,
                    new ArrayList<>());
        }

        File folder = folderPath.toFile();
        if (folder.exists() && folder.isDirectory()) {
            File[] oldFiles = folder.listFiles((dir, name) -> {
                String lower = name.toLowerCase();
                return lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".emb");
            });
            if (oldFiles != null && oldFiles.length > 0) {
                int deletedCount = 0;
                for (File oldFile : oldFiles) {
                    if (oldFile.delete()) {
                        deletedCount++;
                    }
                }
                logDebug("Cleared " + deletedCount + " old files before starting new capture");
                callback.onWarning("Cleared " + deletedCount + " old files - starting fresh capture");
            }
        }

        student.getFaceData().clearImages();

        Mat frame = new Mat();
        List<String> capturedImages = new ArrayList<>();
        List<String> rejectedReasons = new ArrayList<>();

        List<RawFaceData> rawFacesForProcessing = new ArrayList<>();
        int capturedCount = 0;
        int rejectedCount = 0;
        int attemptCount = 0;
        int maxAttempts = Math.max(numberOfImages * CAPTURE_ATTEMPT_MULTIPLIER, numberOfImages + 30);

        callback.onCaptureStarted();

        logDebug("Starting Phase 1: Fast capture of raw face ROIs");

        while (capturedCount < numberOfImages && attemptCount < maxAttempts) {
            synchronized (cameraLock) {
                if (!camera.read(frame)) {
                    System.err.println("Failed to read camera frame");
                    callback.onError("Failed to capture frame from camera");
                    break;
                }
            }

            attemptCount++;
            logDebug(String.format("Capture attempt %d/%d", attemptCount, maxAttempts));

            boolean capturedThisFrame = false;
            FaceDetectionResult detectionResult = detectFaceWithConfidence(frame);

            if (detectionResult.hasValidFace()) {
                Rect bestFace = detectionResult.getBestFace();
                Mat faceROI = extractFaceROI(frame, bestFace);

                if (faceROI != null) {

                    rawFacesForProcessing.add(new RawFaceData(
                            faceROI.clone(),
                            bestFace,
                            detectionResult.getConfidence(),
                            capturedCount));
                    faceROI.release();

                    capturedCount++;
                    capturedThisFrame = true;

                    callback.onImageCaptured(capturedCount, numberOfImages,
                            detectionResult.getConfidence());

                    try {
                        Thread.sleep(CAPTURE_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    logDebug("Face ROI extraction failed");
                    String reason = "Face too close to edge, please center your face";
                    callback.onWarning(reason);
                    rejectedReasons.add(reason);
                    rejectedCount++;
                }
            } else {
                String warning = getDetectionFeedback(detectionResult);
                callback.onWarning(warning);
                rejectedReasons.add(warning);
                rejectedCount++;
            }

            if (!capturedThisFrame) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        callback.onCaptureCompleted();
        logDebug("Phase 1 completed: Captured " + rawFacesForProcessing.size() + " raw face ROIs");

        // ============================================================
        // PHASE 2: BATCH PROCESSING - Process all captured ROIs
        // ============================================================
        if (!rawFacesForProcessing.isEmpty()) {
            callback.onProcessingStarted("Processing captured images (quality check, landmarks, embeddings)...");
            logDebug("Starting Phase 2: Batch processing of " + rawFacesForProcessing.size() + " captured faces");

            int processedSuccessfully = 0;
            int processingRejected = 0;

            for (int i = 0; i < rawFacesForProcessing.size(); i++) {
                RawFaceData rawData = rawFacesForProcessing.get(i);

                try {
                    // Step 1: Preprocess the face
                    Mat processedFace = preprocessForRecognition(rawData.rawFaceROI);

                    // Step 2: Validate image quality
                    ImageProcessor.ImageQualityResult qualityResult = imageProcessor
                            .validateImageQualityDetailed(processedFace);
                    String rawFeedback = qualityResult.getFeedback();
                    String feedbackMessage = rawFeedback != null ? rawFeedback.trim() : "Unknown quality issue";

                    boolean qualityAcceptable = qualityResult.isGoodQuality()
                            || qualityResult.getQualityScore() >= 70.0;

                    // Step 3: Validate landmark quality (if quality is acceptable)
                    boolean landmarkQualityAcceptable = true;
                    String landmarkFeedback = "";

                    if (qualityAcceptable && landmarkDetector != null && landmarkDetector.isInitialized()) {
                        FacialLandmarkDetector.LandmarkResult landmarkResult = landmarkDetector.detectLandmarks(
                                processedFace,
                                new Rect(0, 0, processedFace.width(), processedFace.height()));

                        if (landmarkResult.isSuccess()) {
                            FacialLandmarkDetector.LandmarkQuality landmarkQuality = landmarkResult.getLandmarks()
                                    .getQuality();

                            landmarkQualityAcceptable = landmarkQuality.isGoodQuality() ||
                                    landmarkQuality.getOverallScore() >= 65.0;
                            landmarkFeedback = landmarkQuality.getFeedback();

                            logDebug(String.format("Landmark Quality: %s", landmarkQuality.toString()));
                        } else {
                            logDebug("Landmark detection failed: " + landmarkResult.getMessage());
                        }
                    }

                    // Step 4: Save image and generate embedding if quality is good
                    if (qualityAcceptable && landmarkQualityAcceptable) {
                        String fileName = student.getStudentId() + "_" +
                                String.format("%03d", processedSuccessfully + 1) + ".jpg";
                        Path imageFile = folderPath.resolve(fileName);

                        if (Imgcodecs.imwrite(imageFile.toString(), processedFace)) {
                            // Generate embedding
                            byte[] embedding = embeddingGenerator.generateEmbedding(processedFace);

                            // Save embedding
                            String embeddingFileName = student.getStudentId() + "_" +
                                    String.format("%03d", processedSuccessfully + 1) + ".emb";
                            Path embeddingFile = folderPath.resolve(embeddingFileName);
                            try {
                                Files.write(embeddingFile, embedding);
                                logDebug("Saved embedding: " + embeddingFileName);
                            } catch (Exception e) {
                                System.err.println(
                                        "Failed to save embedding file: " + embeddingFile + " - " + e.getMessage());
                                callback.onWarning("Warning: Embedding file save failed for " + embeddingFileName);
                            }

                            // Create FaceImage with quality score
                            FaceImage faceImage = new FaceImage(imageFile.toString(), embedding);

                            double imageQualityScore = qualityResult.getQualityScore() / 100.0;
                            double landmarkQualityScore = landmarkQualityAcceptable ? 1.0 : 0.7;
                            double combinedQuality = (imageQualityScore * 0.6) + (landmarkQualityScore * 0.4);
                            double normalizedQuality = Math.min(1.0, Math.max(0.0, combinedQuality));

                            faceImage.setQualityScore(normalizedQuality);

                            student.getFaceData().addImage(faceImage);
                            capturedImages.add(imageFile.toString());
                            processedSuccessfully++;

                            logDebug(String.format("Processed image %d/%d: quality=%.1f%%, saved as %s",
                                    i + 1, rawFacesForProcessing.size(),
                                    qualityResult.getQualityScore(), fileName));
                        } else {
                            System.err.println("Failed to save image: " + imageFile);
                            processingRejected++;
                        }
                    } else {
                        // Rejected due to quality or landmarks
                        String reason = !qualityAcceptable ? feedbackMessage : landmarkFeedback;
                        logDebug("Image rejected during processing: " + reason);
                        processingRejected++;
                    }

                    processedFace.release();

                } catch (Exception e) {
                    System.err.println("Error processing face " + i + ": " + e.getMessage());
                    e.printStackTrace();
                    processingRejected++;
                } finally {
                    // Release raw ROI
                    if (rawData.rawFaceROI != null && !rawData.rawFaceROI.empty()) {
                        rawData.rawFaceROI.release();
                    }
                }

                // Update progress
                callback.onProcessingProgress(i + 1, rawFacesForProcessing.size());
            }

            callback.onProcessingCompleted();
            logDebug(String.format("Phase 2 completed: %d processed successfully, %d rejected",
                    processedSuccessfully, processingRejected));

            // Update counts
            capturedCount = processedSuccessfully;
            rejectedCount += processingRejected;
        }

        if (capturedCount >= 5) {
            callback.onWarning("Analyzing captured images for quality...");
            OutlierDetectionResult outlierResult = detectAndRemoveOutliers(student.getFaceData(), callback);

            if (outlierResult.outliersRemoved > 0) {
                capturedCount -= outlierResult.outliersRemoved;
                callback.onWarning(String.format(
                        "Removed %d outlier image(s) with low similarity (%.1f%% avg). Tightness improved: %.3f → %.3f",
                        outlierResult.outliersRemoved,
                        outlierResult.outlierAvgSimilarity * 100,
                        outlierResult.tightnessBeforeRemoval,
                        outlierResult.tightnessAfterRemoval));
            } else {
                callback.onWarning(String.format(
                        "✓ All images have good consistency (tightness: %.3f)",
                        outlierResult.tightnessBeforeRemoval));
            }
        }

        boolean success = (capturedCount >= Math.min(10, numberOfImages));
        String message = success ? String.format("Successfully captured %d high-quality face images", capturedCount)
                : String.format("Only captured %d images, need at least %d", capturedCount,
                        Math.min(10, numberOfImages));

        return new FaceCaptureResult(success, message, capturedImages, capturedCount, rejectedCount, rejectedReasons);
    }

    private Mat extractFaceROI(Mat frame, Rect face) {
        try {
            Rect captureRegion = buildSquareRegionWithPadding(frame.size(), face, 0.12);
            return new Mat(frame, captureRegion).clone();

        } catch (Exception e) {
            System.err.println(" Failed to extract face ROI: " + e.getMessage());
            return null;
        }
    }

    private Mat preprocessForRecognition(Mat faceROI) {
        if (faceROI == null || faceROI.empty()) {
            return new Mat();
        }
        Mat aligned = imageProcessor.correctFaceOrientation(faceROI);
        Mat denoised = imageProcessor.reduceNoise(aligned);
        Mat processed = imageProcessor.preprocessFaceImage(denoised);

        aligned.release();
        denoised.release();

        return processed;
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
        paddedSize = Math.max(paddedSize, (int) Math.round(MIN_FACE_SIZE));
        paddedSize = Math.min(paddedSize, Math.min(frameWidth, frameHeight));

        int x = centerX - paddedSize / 2;
        int y = centerY - paddedSize / 2;

        if (x < 0) {
            x = 0;
        }
        if (y < 0) {
            y = 0;
        }
        if (x + paddedSize > frameWidth) {
            x = frameWidth - paddedSize;
        }
        if (y + paddedSize > frameHeight) {
            y = frameHeight - paddedSize;
        }

        x = Math.max(0, x);
        y = Math.max(0, y);

        return new Rect(x, y, paddedSize, paddedSize);
    }

    private String getDetectionFeedback(FaceDetectionResult result) {
        if (result.getFaces().isEmpty()) {
            return "No face detected - please position your face in the camera view";
        } else if (result.getBestConfidence() < MIN_CONFIDENCE_SCORE) {
            if (result.getBestConfidence() < 0.3) {
                return "Face detection confidence too low - please improve lighting";
            } else {
                return String.format("Face quality insufficient (%.1f%%) - please look directly at camera",
                        result.getBestConfidence() * 100);
            }
        } else {
            return "Processing...";
        }
    }

    public boolean captureAndStoreFaceImages(Student student, int numberOfImages) {
        FaceCaptureCallback dummyCallback = new FaceCaptureCallback() {
            public void onCaptureStarted() {
            }

            public void onFrameUpdate(Mat frame) {
            }

            public void onImageCaptured(int current, int total, double confidence) {
            }

            public void onWarning(String message) {
                System.out.println(" Warning: " + message);
            }

            public void onError(String message) {
                System.err.println(" Error: " + message);
            }

            public void onCaptureCompleted() {
            }

            public void onProcessingStarted(String message) {
                System.out.println(" Processing: " + message);
            }

            public void onProcessingProgress(int current, int total) {
                System.out.println(" Progress: " + current + "/" + total);
            }

            public void onProcessingCompleted() {
                System.out.println(" Processing completed");
            }
        };

        FaceCaptureResult result = captureAndStoreFaceImages(student, numberOfImages, dummyCallback);
        return result.isSuccess();
    }

    public Mat getCurrentFrame() {
        Mat frame = new Mat();
        if (camera != null && camera.isOpened()) {
            synchronized (cameraLock) {
                boolean success = camera.read(frame);
                logDebug("getCurrentFrame: success=" + success + ", empty=" + frame.empty());
            }
        } else {
            System.err.println("Camera not available for getCurrentFrame");
        }
        return frame;
    }

    public boolean isCameraAvailable() {
        boolean available = camera != null && camera.isOpened();
        logDebug("Camera available: " + available);
        return available;
    }

    public boolean showCameraSettingsDialog() {
        if (camera != null && camera.isOpened()) {
            try {
                boolean result = camera.set(org.opencv.videoio.Videoio.CAP_PROP_SETTINGS, 1);
                logDebug("Camera settings dialog opened: " + result);
                return result;
            } catch (Exception e) {
                System.err.println("Failed to open camera settings dialog: " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    public boolean setFps(double fps) {
        if (camera != null && camera.isOpened()) {
            try {
                double currentFps = camera.get(org.opencv.videoio.Videoio.CAP_PROP_FPS);
                boolean result = camera.set(org.opencv.videoio.Videoio.CAP_PROP_FPS, fps);
                double actualFps = camera.get(org.opencv.videoio.Videoio.CAP_PROP_FPS);

                if (result && Math.abs(actualFps - fps) < 1.0) {
                    logDebug("FPS successfully set to: " + fps + " (actual: " + actualFps + ")");
                    return true;
                } else {
                    logDebug("FPS change not supported by camera. Current FPS: " + actualFps);
                    return false;
                }
            } catch (Exception e) {
                logDebug("FPS setting not supported: " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    public double getCurrentFps() {
        if (camera != null && camera.isOpened()) {
            try {
                return camera.get(org.opencv.videoio.Videoio.CAP_PROP_FPS);
            } catch (Exception e) {
                logDebug("Failed to get FPS: " + e.getMessage());
                return 0;
            }
        }
        return 0;
    }

    public boolean deleteFaceData(FaceData faceData) {
        String folderPath = faceData.getFolderPath();
        File folder = new File(folderPath);

        if (folder.exists()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            return folder.delete();
        }
        return true;
    }

    public void release() {
        if (camera != null && camera.isOpened()) {
            camera.release();
            logDebug("Camera released");
        }
    }

    public static class FaceDetectionResult {
        private List<FaceCandidate> faces;

        public FaceDetectionResult(List<FaceCandidate> faces) {
            this.faces = faces != null ? faces : new ArrayList<>();
        }

        public boolean hasValidFace() {
            return !faces.isEmpty();
        }

        public Rect getBestFace() {
            return faces.isEmpty() ? null
                    : faces.stream().max((a, b) -> Double.compare(a.confidence, b.confidence))
                            .get().rect;
        }

        public double getBestConfidence() {
            return faces.isEmpty() ? 0.0 : faces.stream().mapToDouble(f -> f.confidence).max().orElse(0.0);
        }

        public double getConfidence() {
            return getBestConfidence();
        }

        public List<FaceCandidate> getFaces() {
            return faces;
        }
    }

    public static class FaceCandidate {
        public final Rect rect;
        public final double confidence;

        public FaceCandidate(Rect rect, double confidence) {
            this.rect = rect;
            this.confidence = confidence;
        }
    }

    public static class FaceCaptureResult {
        private boolean success;
        private String message;
        private List<String> capturedImages;
        private int acceptedCount;
        private int rejectedCount;
        private List<String> rejectedReasons;

        public FaceCaptureResult(boolean success, String message, List<String> capturedImages) {
            this.success = success;
            this.message = message;
            this.capturedImages = capturedImages;
            this.acceptedCount = 0;
            this.rejectedCount = 0;
            this.rejectedReasons = new ArrayList<>();
        }

        public FaceCaptureResult(boolean success, String message, List<String> capturedImages,
                int acceptedCount, int rejectedCount, List<String> rejectedReasons) {
            this.success = success;
            this.message = message;
            this.capturedImages = capturedImages;
            this.acceptedCount = acceptedCount;
            this.rejectedCount = rejectedCount;
            this.rejectedReasons = rejectedReasons != null ? rejectedReasons : new ArrayList<>();
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public List<String> getCapturedImages() {
            return capturedImages;
        }

        public int getAcceptedCount() {
            return acceptedCount;
        }

        public int getRejectedCount() {
            return rejectedCount;
        }

        public List<String> getRejectedReasons() {
            return rejectedReasons;
        }
    }

    public static class OutlierDetectionResult {
        public int outliersRemoved;
        public double outlierAvgSimilarity;
        public double tightnessBeforeRemoval;
        public double tightnessAfterRemoval;

        public OutlierDetectionResult(int outliersRemoved, double outlierAvgSimilarity,
                double tightnessBeforeRemoval, double tightnessAfterRemoval) {
            this.outliersRemoved = outliersRemoved;
            this.outlierAvgSimilarity = outlierAvgSimilarity;
            this.tightnessBeforeRemoval = tightnessBeforeRemoval;
            this.tightnessAfterRemoval = tightnessAfterRemoval;
        }
    }

    // Helper class to store raw face ROI data before processing
    private static class RawFaceData {
        Mat rawFaceROI;
        Rect faceRect;
        double detectionConfidence;
        int captureIndex;

        RawFaceData(Mat roi, Rect face, double confidence, int index) {
            this.rawFaceROI = roi;
            this.faceRect = face;
            this.detectionConfidence = confidence;
            this.captureIndex = index;
        }
    }

    // Helper class to store processed face data before embedding generation
    private static class ProcessedFaceData {
        Mat processedFace;
        String imagePath;
        String embeddingPath;
        double qualityScore;

        ProcessedFaceData(Mat face, String imgPath, String embPath, double quality) {
            this.processedFace = face;
            this.imagePath = imgPath;
            this.embeddingPath = embPath;
            this.qualityScore = quality;
        }
    }

    private OutlierDetectionResult detectAndRemoveOutliers(app.model.FaceData faceData, FaceCaptureCallback callback) {
        List<app.model.FaceImage> images = faceData.getImages();

        if (images == null || images.size() < 5) {

            return new OutlierDetectionResult(0, 0.0, 1.0, 1.0);
        }

        List<byte[]> embeddings = new ArrayList<>();
        for (app.model.FaceImage img : images) {
            if (img.getEmbedding() != null) {
                embeddings.add(img.getEmbedding());
            }
        }

        if (embeddings.size() < 5) {
            return new OutlierDetectionResult(0, 0.0, 1.0, 1.0);
        }

        double[] avgSimilarities = new double[embeddings.size()];

        for (int i = 0; i < embeddings.size(); i++) {
            double sum = 0;
            int count = 0;
            for (int j = 0; j < embeddings.size(); j++) {
                if (i != j) {
                    sum += embeddingGenerator.calculateSimilarity(embeddings.get(i), embeddings.get(j));
                    count++;
                }
            }
            avgSimilarities[i] = sum / count;
        }

        double overallAvg = 0;
        for (double s : avgSimilarities) {
            overallAvg += s;
        }
        overallAvg /= avgSimilarities.length;
        double tightnessBeforeRemoval = overallAvg;

        List<Integer> outlierIndices = new ArrayList<>();
        double outlierSumSim = 0;

        for (int i = 0; i < avgSimilarities.length; i++) {
            if (avgSimilarities[i] < overallAvg - 0.10) {
                outlierIndices.add(i);
                outlierSumSim += avgSimilarities[i];
            }
        }

        if (outlierIndices.isEmpty()) {
            return new OutlierDetectionResult(0, 0.0, tightnessBeforeRemoval, tightnessBeforeRemoval);
        }

        double outlierAvgSimilarity = outlierSumSim / outlierIndices.size();

        outlierIndices.sort(Collections.reverseOrder());
        for (int idx : outlierIndices) {
            app.model.FaceImage removedImage = images.remove(idx);

            try {
                File imageFile = new File(removedImage.getImagePath());
                if (imageFile.exists()) {
                    imageFile.delete();
                    logDebug("Deleted outlier image: " + imageFile.getName());
                }

                String embPath = removedImage.getImagePath().replaceAll("\\.(jpg|png|jpeg)$", ".emb");
                File embFile = new File(embPath);
                if (embFile.exists()) {
                    embFile.delete();
                    logDebug("Deleted outlier embedding: " + embFile.getName());
                }
            } catch (Exception e) {
                System.err.println("Failed to delete outlier files: " + e.getMessage());
            }
        }

        List<byte[]> remainingEmbeddings = new ArrayList<>();
        for (app.model.FaceImage img : images) {
            if (img.getEmbedding() != null) {
                remainingEmbeddings.add(img.getEmbedding());
            }
        }

        double tightnessAfterRemoval = calculateTightness(remainingEmbeddings);

        return new OutlierDetectionResult(
                outlierIndices.size(),
                outlierAvgSimilarity,
                tightnessBeforeRemoval,
                tightnessAfterRemoval);
    }

    private double calculateTightness(List<byte[]> embeddings) {
        if (embeddings == null || embeddings.size() < 2) {
            return 1.0;
        }

        double sum = 0;
        int count = 0;

        for (int i = 0; i < embeddings.size(); i++) {
            for (int j = i + 1; j < embeddings.size(); j++) {
                sum += embeddingGenerator.calculateSimilarity(embeddings.get(i), embeddings.get(j));
                count++;
            }
        }

        return count > 0 ? sum / count : 1.0;
    }

    public interface FaceCaptureCallback {
        void onCaptureStarted();

        void onFrameUpdate(Mat frame);

        void onImageCaptured(int current, int total, double confidence);

        void onWarning(String message);

        void onError(String message);

        void onCaptureCompleted();

        void onProcessingStarted(String message);

        void onProcessingProgress(int current, int total);

        void onProcessingCompleted();
    }

    private void logDebug(String message) {
        if (DEBUG_LOGS) {
            System.out.println(message);
        }
    }
}