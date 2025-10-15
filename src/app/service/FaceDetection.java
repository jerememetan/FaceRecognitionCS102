package app.service;

import app.entity.Student;
import app.model.FaceData;
import app.model.FaceImage;
import app.util.ImageProcessor;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.opencv.dnn.Net;
import org.opencv.dnn.Dnn;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FaceDetection {
    private static final String OPENCV_NATIVE_LIBRARY = "opencv_java480";
    private static final String OPENCV_DLL_RELATIVE_PATH = "lib/opencv_java480.dll";
    private static final boolean OPENCV_LOADED;

    static {
        boolean loaded = false;
        try {
            System.loadLibrary(OPENCV_NATIVE_LIBRARY);
            loaded = true;
        } catch (UnsatisfiedLinkError primaryError) {
            try {
                String absolutePath = new File(OPENCV_DLL_RELATIVE_PATH).getAbsolutePath();
                System.load(absolutePath);
                loaded = true;
            } catch (UnsatisfiedLinkError fallbackError) {
                System.err.println("Failed to load OpenCV from library path: " + primaryError.getMessage());
                System.err.println("Also failed to load OpenCV from absolute path: " + fallbackError.getMessage());
            }
        }

        OPENCV_LOADED = loaded;
        if (!loaded) {
            System.err.println("OpenCV native library could not be loaded. Face detection features will be unavailable.");
        }
    }

    private Net dnnFaceDetector;
    private VideoCapture camera;
    private ImageProcessor imageProcessor;
    private FaceEmbeddingGenerator embeddingGenerator;
    private final Object frameLock = new Object();
    private Mat latestFrame;
    private volatile boolean cameraLoopRunning;
    private Thread cameraLoopThread;
    private volatile long latestFrameTimestamp;
    private List<FaceCandidate> latestFaceCandidates = new ArrayList<>();

    private static final double MIN_CONFIDENCE_SCORE = 0.5;
    private static final double MIN_FACE_SIZE = 50.0;
    private static final double MAX_FACE_SIZE = 400.0;
    private static final int CAPTURE_INTERVAL_MS = 900;
    private static final int CAPTURE_ATTEMPT_MULTIPLIER = 12;
    private static final int FRAME_WAIT_TIMEOUT_MS = 500;
    private static final long FACE_PERSISTENCE_NS = TimeUnit.MILLISECONDS.toNanos(350);
    private volatile long latestFaceCandidatesUpdatedAt;
    private static final boolean DEBUG_LOGS = Boolean.parseBoolean(
            System.getProperty("app.faceDetectionDebug", "false"));

    public FaceDetection() {
        logDebug("Initializing FaceDetection...");
        if (!OPENCV_LOADED) {
            throw new IllegalStateException("OpenCV native library not loaded. Check lib folder configuration.");
        }
        initializeDetectors();
        camera = openCameraWithFallback();
        imageProcessor = new ImageProcessor();
        embeddingGenerator = new FaceEmbeddingGenerator();
        latestFrame = new Mat();
        latestFrameTimestamp = 0L;
    latestFaceCandidatesUpdatedAt = 0L;

        if (camera.isOpened()) {
            logDebug("Camera initialized successfully");
            startCameraLoop();
        } else {
            System.err.println("Camera failed to initialize");
        }
    }

    private void initializeDetectors() {
        try {
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

            faces.sort((a, b) -> Double.compare(b.confidence, a.confidence));
            if (faces.size() > 8) {
                faces = new ArrayList<>(faces.subList(0, 8));
            }

            List<FaceCandidate> smoothed = applyTemporalSmoothing(frame.size(), faces);
            List<FaceCandidate> filtered = suppressOverlappingFaces(smoothed, 0.45);
            filtered.sort((a, b) -> Double.compare(b.confidence, a.confidence));

            return new FaceDetectionResult(filtered);

        } catch (Exception e) {
            System.err.println("DNN detection failed: " + e.getMessage());
            e.printStackTrace();
            return new FaceDetectionResult(new ArrayList<>());
        }
    }

    private List<FaceCandidate> applyTemporalSmoothing(Size frameSize, List<FaceCandidate> currentFaces) {
        long now = System.nanoTime();

        if (currentFaces.isEmpty()) {
            synchronized (frameLock) {
                if (!latestFaceCandidates.isEmpty()
                        && now - latestFaceCandidatesUpdatedAt <= FACE_PERSISTENCE_NS) {
                    List<FaceCandidate> persisted = new ArrayList<>(latestFaceCandidates.size());
                    for (FaceCandidate candidate : latestFaceCandidates) {
                        persisted.add(scaleCandidateConfidence(candidate, 0.85));
                    }
                    return persisted;
                }

                latestFaceCandidates.clear();
                latestFaceCandidatesUpdatedAt = now;
            }
            return currentFaces;
        }

        List<FaceCandidate> smoothed;
        synchronized (frameLock) {
            if (latestFaceCandidates.isEmpty()) {
                latestFaceCandidates = new ArrayList<>(currentFaces);
                latestFaceCandidatesUpdatedAt = now;
                return currentFaces;
            }

            smoothed = new ArrayList<>();
            for (FaceCandidate candidate : currentFaces) {
                FaceCandidate previous = findClosestFace(candidate, latestFaceCandidates);
                if (previous != null) {
                    Rect mergedRect = interpolateRect(previous.rect, candidate.rect, 0.6);
                    double mergedConfidence = Math.min(1.0,
                            (previous.confidence * 0.6) + (candidate.confidence * 0.4));
                    smoothed.add(new FaceCandidate(clipRectToFrame(frameSize, mergedRect), mergedConfidence));
                } else {
                    smoothed.add(candidate);
                }
            }

            latestFaceCandidates = new ArrayList<>(smoothed);
            latestFaceCandidatesUpdatedAt = now;
        }

        return smoothed;
    }

    public Mat drawFaceOverlay(Mat frame, FaceDetectionResult result) {
        Mat debugFrame = frame.clone();

        if (result != null && result.hasValidFace()) {
            FaceCandidate candidate = result.getFaces().get(0);
            Rect face = candidate.rect;
            double confidence = candidate.confidence;

            Scalar color = confidence >= 0.7 ? new Scalar(0, 220, 0, 255) : new Scalar(0, 200, 255, 255);
            int thickness = 4;

            Imgproc.rectangle(debugFrame, face.tl(), face.br(), color, thickness);

            String confidenceText = String.format("%.1f%%", confidence * 100);
            Point textPoint = new Point(face.x, Math.max(face.y - 10, 20));

            Imgproc.putText(debugFrame, confidenceText, textPoint,
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.75, color, 2);
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

        List<String> capturedImages = new ArrayList<>();
        int capturedCount = 0;
        int attemptCount = 0;
        int maxAttempts = Math.max(numberOfImages * CAPTURE_ATTEMPT_MULTIPLIER, numberOfImages + 30);

        callback.onCaptureStarted();

        while (capturedCount < numberOfImages && attemptCount < maxAttempts) {
            Mat frame = waitForFrame(FRAME_WAIT_TIMEOUT_MS);
            if (frame.empty()) {
                callback.onWarning("Waiting for camera frame...");
                frame.release();
                try {
                    Thread.sleep(60);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            try {
                attemptCount++;
                logDebug(String.format("Capture attempt %d/%d", attemptCount, maxAttempts));

                Mat frameClone = frame.clone();
                try {
                    callback.onFrameUpdate(frameClone);
                } finally {
                    frameClone.release();
                }

                FaceDetectionResult detectionResult = detectFaceWithConfidence(frame);

                if (detectionResult.hasValidFace()) {
                    Rect bestFace = detectionResult.getBestFace();
                    Mat faceROI = extractFaceROI(frame, bestFace);

                    if (faceROI != null) {
                        try {
                            ImageProcessor.ImageQualityResult qualityResult = imageProcessor
                                    .validateImageQualityDetailed(faceROI);
                            String rawFeedback = qualityResult.getFeedback();
                            String feedbackMessage = rawFeedback != null ? rawFeedback.trim() : "Unknown quality issue";

                            boolean qualityAcceptable = qualityResult.isGoodQuality()
                                    || qualityResult.getQualityScore() >= 70.0;

                            if (qualityAcceptable) {
                                Mat processedFace = preprocessForRecognition(faceROI);
                                try {
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

                                        callback.onImageCaptured(capturedCount, numberOfImages,
                                                detectionResult.getConfidence());
                                        if (!qualityResult.isGoodQuality()) {
                                            callback.onWarning(
                                                    "Captured, but consider improving quality: " + feedbackMessage);
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
                                } finally {
                                    processedFace.release();
                                }
                            } else {
                                logDebug("Image quality validation failed: " + feedbackMessage);
                                callback.onWarning("Image rejected: " + feedbackMessage);
                            }
                        } finally {
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
            } finally {
                frame.release();
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
        Mat processed = imageProcessor.preprocessFaceImage(aligned);

        if (aligned != faceROI) {
            aligned.release();
        }

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
                synchronized (frameLock) {
                        return latestFrame.empty() ? new Mat() : latestFrame.clone();
                }
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
        stopCameraLoop();
        if (camera != null && camera.isOpened()) {
            camera.release();
            logDebug("Camera released");
        }
    }

    private void startCameraLoop() {
        if (cameraLoopRunning) {
            return;
        }

        cameraLoopRunning = true;
        cameraLoopThread = new Thread(() -> {
            Mat frameBuffer = new Mat();
            int consecutiveFailures = 0;
            final int maxFailuresBeforeRetry = 12;

            while (cameraLoopRunning) {
                boolean success = camera != null && camera.isOpened() && camera.read(frameBuffer);
                if (success) {
                    synchronized (frameLock) {
                        frameBuffer.copyTo(latestFrame);
                        latestFrameTimestamp = System.nanoTime();
                        consecutiveFailures = 0;
                    }
                } else {
                    consecutiveFailures++;
                    logDebug("Camera loop: failed to grab frame (" + consecutiveFailures + ")");
                    if (consecutiveFailures >= maxFailuresBeforeRetry) {
                        logDebug("Camera loop: attempting to reopen camera (backend fallback)");
                        reopenCameraSafely();
                        consecutiveFailures = 0;
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    } else {
                        try {
                            Thread.sleep(80);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }
                }

                try {
                    Thread.sleep(15);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            frameBuffer.release();
        }, "FaceDetection-CameraLoop");
        cameraLoopThread.setDaemon(true);
        cameraLoopThread.start();
    }

    private void stopCameraLoop() {
        cameraLoopRunning = false;
        if (cameraLoopThread != null) {
            try {
                cameraLoopThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            cameraLoopThread = null;
        }

        synchronized (frameLock) {
            if (!latestFrame.empty()) {
                latestFrame.release();
                latestFrame = new Mat();
            }
        }
    }

    private Mat waitForFrame(long timeoutMs) {
        long deadline = timeoutMs > 0 ? System.currentTimeMillis() + timeoutMs : Long.MAX_VALUE;

        while (true) {
            Mat frame = getCurrentFrame();
            if (!frame.empty()) {
                return frame;
            }

            frame.release();

            if (timeoutMs > 0 && System.currentTimeMillis() >= deadline) {
                return new Mat();
            }

            try {
                Thread.sleep(15);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Mat();
            }
        }
    }

    private VideoCapture openCameraWithFallback() {
        // Prefer DirectShow on Windows to avoid MSMF grabFrame errors
        VideoCapture cap = new VideoCapture(0, Videoio.CAP_DSHOW);
        if (cap.isOpened()) {
            configureCamera(cap);
            return cap;
        }

        // Fallback to MSMF
        cap.release();
        cap = new VideoCapture(0, Videoio.CAP_MSMF);
        if (cap.isOpened()) {
            configureCamera(cap);
            return cap;
        }

        // Last resort: any backend
        cap.release();
        cap = new VideoCapture(0);
        if (cap.isOpened()) {
            configureCamera(cap);
        }
        return cap;
    }

    private void configureCamera(VideoCapture cap) {
        try {
            // Reduce latency and stabilize output
            cap.set(Videoio.CAP_PROP_FRAME_WIDTH, 640);
            cap.set(Videoio.CAP_PROP_FRAME_HEIGHT, 480);
            cap.set(Videoio.CAP_PROP_FPS, 30);
            // Buffer size (may be ignored by some backends)
            cap.set(Videoio.CAP_PROP_BUFFERSIZE, 1);
            // FourCC MJPG often helps with USB cams
            cap.set(Videoio.CAP_PROP_FOURCC, VideoWriter_fcc('M','J','P','G'));

            // Do not adjust exposure/white-balance/auto-focus here; use device defaults for raw feed
        } catch (Exception ignored) {
        }
    }

    private static int VideoWriter_fcc(char c1, char c2, char c3, char c4) {
        return ((int) c1) | (((int) c2) << 8) | (((int) c3) << 16) | (((int) c4) << 24);
    }

    private void reopenCameraSafely() {
        synchronized (frameLock) {
            try {
                if (camera != null && camera.isOpened()) {
                    camera.release();
                }
            } catch (Exception ignored) {
            }

            camera = openCameraWithFallback();
        }
    }

    // Expose driver settings dialog (Windows DirectShow typically), no image filtering is applied.
    public boolean showCameraSettingsDialog() {
        try {
            if (camera != null && camera.isOpened()) {
                return camera.set(Videoio.CAP_PROP_SETTINGS, 1);
            }
        } catch (Exception ignored) { }
        return false;
    }

    // Allow user to change FPS which often affects max exposure time in low light.
    public boolean setFps(double fps) {
        try {
            if (camera != null && camera.isOpened()) {
                return camera.set(Videoio.CAP_PROP_FPS, fps);
            }
        } catch (Exception ignored) { }
        return false;
    }

    private FaceCandidate findClosestFace(FaceCandidate target, List<FaceCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        double targetCenterX = target.rect.x + target.rect.width / 2.0;
        double targetCenterY = target.rect.y + target.rect.height / 2.0;

        FaceCandidate closest = null;
        double minDistance = Double.MAX_VALUE;

        for (FaceCandidate candidate : candidates) {
            double candidateCenterX = candidate.rect.x + candidate.rect.width / 2.0;
            double candidateCenterY = candidate.rect.y + candidate.rect.height / 2.0;
            double distance = Math.hypot(targetCenterX - candidateCenterX, targetCenterY - candidateCenterY);

            if (distance < minDistance) {
                minDistance = distance;
                closest = candidate;
            }
        }

        return closest;
    }

    private Rect interpolateRect(Rect from, Rect to, double alpha) {
        if (from == null) {
            return to;
        }

        double beta = 1.0 - alpha;
        int x = (int) Math.round(from.x * alpha + to.x * beta);
        int y = (int) Math.round(from.y * alpha + to.y * beta);
        int width = (int) Math.round(from.width * alpha + to.width * beta);
        int height = (int) Math.round(from.height * alpha + to.height * beta);

        return new Rect(x, y, Math.max(1, width), Math.max(1, height));
    }

    private List<FaceCandidate> suppressOverlappingFaces(List<FaceCandidate> faces, double iouThreshold) {
        if (faces == null || faces.isEmpty()) {
            return faces;
        }

        List<FaceCandidate> result = new ArrayList<>();

        for (FaceCandidate candidate : faces) {
            boolean suppressed = false;
            for (int i = 0; i < result.size(); i++) {
                FaceCandidate kept = result.get(i);
                if (computeIoU(candidate.rect, kept.rect) > iouThreshold) {
                    if (candidate.confidence > kept.confidence) {
                        result.set(i, candidate);
                    }
                    suppressed = true;
                    break;
                }
            }

            if (!suppressed) {
                result.add(candidate);
            }
        }

        return result;
    }

    private double computeIoU(Rect a, Rect b) {
        int x1 = Math.max(a.x, b.x);
        int y1 = Math.max(a.y, b.y);
        int x2 = Math.min(a.x + a.width, b.x + b.width);
        int y2 = Math.min(a.y + a.height, b.y + b.height);

        int intersectionWidth = Math.max(0, x2 - x1);
        int intersectionHeight = Math.max(0, y2 - y1);
        int intersectionArea = intersectionWidth * intersectionHeight;

        int areaA = a.width * a.height;
        int areaB = b.width * b.height;

        int unionArea = areaA + areaB - intersectionArea;
        if (unionArea <= 0) {
            return 0.0;
        }

        return (double) intersectionArea / unionArea;
    }

    private FaceCandidate scaleCandidateConfidence(FaceCandidate candidate, double scale) {
        double scaled = Math.max(0.0, Math.min(1.0, candidate.confidence * scale));
        return new FaceCandidate(candidate.rect, scaled);
    }

    private Rect clipRectToFrame(Size frameSize, Rect rect) {
        int frameWidth = (int) frameSize.width;
        int frameHeight = (int) frameSize.height;

        int x = Math.max(0, Math.min(rect.x, frameWidth - 1));
        int y = Math.max(0, Math.min(rect.y, frameHeight - 1));
        int width = Math.min(rect.width, frameWidth - x);
        int height = Math.min(rect.height, frameHeight - y);

        return new Rect(x, y, Math.max(1, width), Math.max(1, height));
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