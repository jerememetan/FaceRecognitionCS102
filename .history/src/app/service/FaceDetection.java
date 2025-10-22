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
import java.util.List;
import ConfigurationAndLogging.*;

public class FaceDetection {
    private Net dnnFaceDetector;
    private VideoCapture camera;
    private ImageProcessor imageProcessor;
    private FaceEmbeddingGenerator embeddingGenerator;

    private static final double MIN_CONFIDENCE_SCORE = AppConfig.getInstance().getCaptureMinConfidenceScore();
    private static final double MIN_FACE_SIZE = AppConfig.getInstancce().getCaptureMinFaceSize();
    private static final double MAX_FACE_SIZE = 400.0;
    private static final int CAPTURE_INTERVAL_MS = AppConfig.getInstance().getCaptureIntervalMs();
    private static final int CAPTURE_ATTEMPT_MULTIPLIER = AppConfig.getInstance().getCaptureAttemptMultiplier();
    private static final boolean DEBUG_LOGS = Boolean.parseBoolean(
            System.getProperty("app.faceDetectionDebug", "false"));

    public FaceDetection() {
        logDebug("Initializing FaceDetection...");
        initializeDetectors();
        camera = new VideoCapture(0);
        imageProcessor = new ImageProcessor();
        embeddingGenerator = new FaceEmbeddingGenerator();

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

            String modelConfiguration = AppConfig.getInstance().getDnnConfigPath();
            String modelWeights = AppConfig.getInstance().getDnnModelPath();

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
        try {
            Size size = new Size(300, 300);
            Mat blob = Dnn.blobFromImage(frame, 1.0, size, new Scalar(104.0, 177.0, 123.0));

            dnnFaceDetector.setInput(blob);
            Mat detections = dnnFaceDetector.forward();

            // Convert to float just in case
            Mat detectionsFloat = new Mat();
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
        }
    }

    public Mat drawFaceOverlay(Mat frame, FaceDetectionResult result) {
        Mat debugFrame = frame.clone();

        if (result != null && result.hasValidFace()) {
            for (FaceCandidate candidate : result.getFaces()) {
                Rect face = candidate.rect;
                double confidence = candidate.confidence;

                Scalar color;
                if (confidence >= 0.8) {
                    color = new Scalar(0, 255, 0, 255);
                } else if (confidence >= 0.6) {
                    color = new Scalar(0, 255, 255, 255);
                } else {
                    color = new Scalar(0, 0, 255, 255);
                }

                Imgproc.rectangle(debugFrame, face.tl(), face.br(), color, 3);

                String confidenceText = String.format("%.1f%%", confidence * 100);
                Point textPoint = new Point(face.x, Math.max(face.y - 10, 20));

                Imgproc.putText(debugFrame, confidenceText, textPoint,
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.8, color, 2);

            }
        } else {

            Point textPoint = new Point(20, 30);
            Imgproc.putText(debugFrame, "NO FACE DETECTED", textPoint,
                    Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(0, 0, 255, 255), 2);
        }

        return debugFrame;
    }

    public FaceCaptureResult captureAndStoreFaceImages(Student student, int numberOfImages,
            FaceCaptureCallback callback) {
        logDebug("Starting face capture for: " + student.getName());

        if (!camera.isOpened()) {
            System.err.println("Camera not opened");
            return new FaceCaptureResult(false, "Cannot open camera", new ArrayList<>());
        }

        Path folderPath = Paths.get(student.getFaceData().getFolderPath());
        try {
            Files.createDirectories(folderPath);
        } catch (Exception ex) {
            System.err.println("Failed to create face data directory: " + folderPath + " - " + ex.getMessage());
            return new FaceCaptureResult(false, "Cannot create storage directory", new ArrayList<>());
        }

        Mat frame = new Mat();
        List<String> capturedImages = new ArrayList<>();
        int capturedCount = 0;
        int attemptCount = 0;
        int maxAttempts = Math.max(numberOfImages * CAPTURE_ATTEMPT_MULTIPLIER, numberOfImages + 30);

        callback.onCaptureStarted();

        while (capturedCount < numberOfImages && attemptCount < maxAttempts) {
            if (!camera.read(frame)) {
                System.err.println("Failed to read camera frame");
                callback.onError("Failed to capture frame from camera");
                break;
            }

            attemptCount++;
            logDebug(String.format("Capture attempt %d/%d", attemptCount, maxAttempts));

            callback.onFrameUpdate(frame.clone());
            FaceDetectionResult detectionResult = detectFaceWithConfidence(frame);

            if (detectionResult.hasValidFace()) {
                Rect bestFace = detectionResult.getBestFace();
                Mat faceROI = extractFaceROI(frame, bestFace);

                if (faceROI != null) {
                    ImageProcessor.ImageQualityResult qualityResult = imageProcessor
                            .validateImageQualityDetailed(faceROI);
                    String rawFeedback = qualityResult.getFeedback();
                    String feedbackMessage = rawFeedback != null ? rawFeedback.trim() : "Unknown quality issue";

                    // Accept frames that pass all checks or miss only one metric narrowly (score >=
                    // 70)
                    boolean qualityAcceptable = qualityResult.isGoodQuality()
                            || qualityResult.getQualityScore() >= 70.0;

                    if (qualityAcceptable) {
                        Mat processedFace = preprocessForRecognition(faceROI);
                        faceROI.release();
                        String fileName = student.getStudentId() + "_" +
                                String.format("%03d", capturedCount + 1) + ".jpg";
                        Path imageFile = folderPath.resolve(fileName);

                        if (Imgcodecs.imwrite(imageFile.toString(), processedFace)) {
                            byte[] embedding = embeddingGenerator.generateEmbedding(processedFace);
                            FaceImage faceImage = new FaceImage(imageFile.toString(), embedding);
                            double normalizedQuality = Math.min(1.0, Math.max(0.0,
                                    qualityResult.getQualityScore() / 100.0));
                            faceImage.setQualityScore(normalizedQuality);

                            student.getFaceData().addImage(faceImage);
                            capturedImages.add(imageFile.toString());
                            capturedCount++;

                            callback.onImageCaptured(capturedCount, numberOfImages, detectionResult.getConfidence());
                            if (!qualityResult.isGoodQuality()) {
                                callback.onWarning("Captured, but consider improving quality: " + feedbackMessage);
                            }

                            try {
                                Thread.sleep(CAPTURE_INTERVAL_MS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        } else {
                            System.err.println("Failed to save image: " + imageFile);
                            callback.onWarning("Failed to save image, please try again");
                        }
                        processedFace.release();
                    } else {
                        logDebug("Image quality validation failed: " + feedbackMessage);
                        callback.onWarning("Image rejected: " + feedbackMessage);
                        faceROI.release();
                    }
                } else {
                    logDebug("Face ROI extraction failed");
                    callback.onWarning("Face too close to edge, please center your face");
                }
            } else {
                String warning = getDetectionFeedback(detectionResult);
                callback.onWarning(warning);
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        callback.onCaptureCompleted();

        boolean success = (capturedCount >= Math.min(10, numberOfImages));
        String message = success ? String.format("Successfully captured %d high-quality face images", capturedCount)
                : String.format("Only captured %d images, need at least %d", capturedCount,
                        Math.min(10, numberOfImages));

        return new FaceCaptureResult(success, message, capturedImages);
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
        };

        FaceCaptureResult result = captureAndStoreFaceImages(student, numberOfImages, dummyCallback);
        return result.isSuccess();
    }

    public Mat getCurrentFrame() {
        Mat frame = new Mat();
        if (camera != null && camera.isOpened()) {
            boolean success = camera.read(frame);
            logDebug("getCurrentFrame: success=" + success + ", empty=" + frame.empty());
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

        public FaceCaptureResult(boolean success, String message, List<String> capturedImages) {
            this.success = success;
            this.message = message;
            this.capturedImages = capturedImages;
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
    }

    public interface FaceCaptureCallback {
        void onCaptureStarted();

        void onFrameUpdate(Mat frame);

        void onImageCaptured(int current, int total, double confidence);

        void onWarning(String message);

        void onError(String message);

        void onCaptureCompleted();
    }

    private void logDebug(String message) {
        if (DEBUG_LOGS) {
            System.out.println(message);
        }
    }
}