package app.service;

import app.entity.Student;
import app.model.FaceData;
import app.model.FaceImage;
import app.util.ImageProcessor;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;
import org.opencv.dnn.Net;
import org.opencv.dnn.Dnn;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FaceDetection {
    private CascadeClassifier faceDetector;
    private Net dnnFaceDetector;
    private VideoCapture camera;
    private ImageProcessor imageProcessor;
    private FaceEmbeddingGenerator embeddingGenerator;

    private static final double MIN_CONFIDENCE_SCORE = 0.5;
    private static final double MIN_FACE_SIZE = 50.0;
    private static final double MAX_FACE_SIZE = 400.0;
    private static final int CAPTURE_INTERVAL_MS = 500;

    public FaceDetection() {
        System.out.println(" Initializing FaceDetection...");
        initializeDetectors();
        camera = new VideoCapture(0);
        imageProcessor = new ImageProcessor();
        embeddingGenerator = new FaceEmbeddingGenerator();

        if (camera.isOpened()) {
            System.out.println(" ✓ Camera initialized successfully");
        } else {
            System.err.println(" ✗ Camera failed to initialize");
        }
    }

    private void initializeDetectors() {
        try {
            System.loadLibrary("opencv_java480");
            System.out.println(" ✓ OpenCV library loaded");

            String cascadeFile = "data/resources/haarcascade_frontalface_default.xml";
            faceDetector = new CascadeClassifier(cascadeFile);

            if (faceDetector.empty()) {
                System.err.println(" ✗ Haar cascade file not found: " + cascadeFile);
                throw new RuntimeException("Failed to load Haar cascade classifier");
            } else {
                System.out.println(" ✓ Haar cascade loaded successfully");
            }

            String modelConfiguration = "data/resources/opencv_face_detector.pbtxt";
            String modelWeights = "data/resources/opencv_face_detector_uint8.pb";

            if (new File(modelConfiguration).exists() && new File(modelWeights).exists()) {
                try {
                    dnnFaceDetector = Dnn.readNetFromTensorflow(modelWeights, modelConfiguration);
                    System.out.println(" ✓ DNN face detector loaded successfully");
                } catch (Exception e) {
                    System.err.println(" ⚠ DNN loading failed: " + e.getMessage());
                    dnnFaceDetector = null;
                }
            } else {
                System.out.println(" ⚠ DNN model files not found, using Haar cascade only");
                dnnFaceDetector = null;
            }

        } catch (Exception e) {
            System.err.println(" ✗ Face detector initialization failed: " + e.getMessage());
            throw new RuntimeException("Failed to initialize face detectors: " + e.getMessage(), e);
        }
    }

    public FaceDetectionResult detectFaceForPreview(Mat frame) {
        if (frame.empty()) {
            System.out.println(" Empty frame received");
            return new FaceDetectionResult(new ArrayList<>());
        }

        System.out.println(" Processing frame: " + frame.width() + "x" + frame.height());
        return detectFaceWithConfidence(frame);
    }

    private FaceDetectionResult detectFaceWithConfidence(Mat frame) {
        System.out.println(" detectFaceWithConfidence called");

        if (dnnFaceDetector != null) {
            System.out.println(" Using DNN detection");
            return detectFaceWithDNN(frame);
        } else {
            System.out.println(" Using Haar cascade detection");
            return detectFaceWithHaar(frame);
        }
    }

    private FaceDetectionResult detectFaceWithDNN(Mat frame) {
        try {
            System.out.println(" DNN detection starting...");

            Size size = new Size(300, 300);
            Mat blob = Dnn.blobFromImage(frame, 1.0, size, new Scalar(104.0, 177.0, 123.0));

            dnnFaceDetector.setInput(blob);
            Mat detections = dnnFaceDetector.forward();

            // Convert to float just in case
            Mat detectionsFloat = new Mat();
            detections.convertTo(detectionsFloat, CvType.CV_32F);

            int numDetections = (int) detectionsFloat.size(2);
            int dims = (int) detectionsFloat.size(3);

            System.out.println(" DNN output shape: " +
                    detectionsFloat.size(0) + "x" + detectionsFloat.size(1) + "x" +
                    detectionsFloat.size(2) + "x" + detectionsFloat.size(3));
            System.out.println(" Found " + numDetections + " potential faces");

            List<FaceCandidate> faces = new ArrayList<>();

            for (int i = 0; i < numDetections; i++) {
                float[] detection = new float[dims];
                detectionsFloat.get(new int[] { 0, 0, i, 0 }, detection);

                float confidence = detection[2];
                if (confidence > MIN_CONFIDENCE_SCORE) {
                    int x = (int) (detection[3] * frame.cols());
                    int y = (int) (detection[4] * frame.rows());
                    int width = (int) ((detection[5] * frame.cols()) - x);
                    int height = (int) ((detection[6] * frame.rows()) - y);

                    if (width >= MIN_FACE_SIZE && width <= MAX_FACE_SIZE &&
                            height >= MIN_FACE_SIZE && height <= MAX_FACE_SIZE) {

                        Rect face = new Rect(x, y, width, height);
                        faces.add(new FaceCandidate(face, confidence));
                        System.out.printf(" ✓ Face %.2f%% at [%d,%d,%d,%d]%n",
                                confidence * 100, x, y, width, height);
                    } else {
                        System.out.println(" ✗ Face rejected - size out of range");
                    }
                }
            }

            System.out.println(" DNN detection completed, " + faces.size() + " valid faces");
            return new FaceDetectionResult(faces);

        } catch (Exception e) {
            System.err.println(" DNN detection failed: " + e.getMessage());
            e.printStackTrace();
            return detectFaceWithHaar(frame);
        }
    }

    private FaceDetectionResult detectFaceWithHaar(Mat frame) {
        try {
            System.out.println(" Haar detection starting...");

            MatOfRect faceDetections = new MatOfRect();

            faceDetector.detectMultiScale(frame, faceDetections,
                    1.1, 3,
                    0,
                    new Size(MIN_FACE_SIZE, MIN_FACE_SIZE),
                    new Size(MAX_FACE_SIZE, MAX_FACE_SIZE));

            Rect[] faces = faceDetections.toArray();
            System.out.println(" Haar found " + faces.length + " faces");

            List<FaceCandidate> candidates = new ArrayList<>();

            for (int i = 0; i < faces.length; i++) {
                Rect face = faces[i];
                double confidence = calculateHaarConfidence(face, frame);

                System.out.println(String.format(" Haar face %d: bounds=(%d,%d,%d,%d), confidence=%.2f",
                        i, face.x, face.y, face.width, face.height, confidence));

                if (confidence >= 0.3) {
                    candidates.add(new FaceCandidate(face, confidence));
                    System.out.println(" ✓ Haar face accepted");
                } else {
                    System.out.println(" ✗ Haar face rejected - low confidence");
                }
            }

            System.out.println(" Haar detection completed, " + candidates.size() + " valid faces");
            return new FaceDetectionResult(candidates);

        } catch (Exception e) {
            System.err.println(" Haar detection failed: " + e.getMessage());
            e.printStackTrace();
            return new FaceDetectionResult(new ArrayList<>());
        }
    }

    private double calculateHaarConfidence(Rect face, Mat frame) {

        double sizeScore = Math.min(1.0, face.width / 100.0);
        double centerScore = 1.0 - Math.abs((face.x + face.width / 2.0) - frame.width() / 2.0) / (frame.width() / 2.0);
        double aspectScore = 1.0 - Math.abs((face.height / (double) face.width) - 1.0) / 1.0;

        double confidence = (sizeScore * 0.4 + centerScore * 0.3 + aspectScore * 0.3) * 0.8 + 0.2;
        return Math.min(0.99, confidence);
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

                System.out.println(String.format(" Drew face rectangle: (%.0f,%.0f) %.0fx%.0f, confidence=%.2f",
                        (double) face.x, (double) face.y, (double) face.width, (double) face.height, confidence));

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
        System.out.println(" Starting face capture for: " + student.getName());

        if (!camera.isOpened()) {
            System.err.println(" Camera not opened");
            return new FaceCaptureResult(false, "Cannot open camera", new ArrayList<>());
        }

        String folderPath = student.getFaceData().getFolderPath();
        File folder = new File(folderPath);
        if (!folder.exists()) {
            folder.mkdirs();
            System.out.println(" Created folder: " + folderPath);
        }

        Mat frame = new Mat();
        List<String> capturedImages = new ArrayList<>();
        int capturedCount = 0;
        int attemptCount = 0;
        int maxAttempts = numberOfImages * 5;

        callback.onCaptureStarted();

        while (capturedCount < numberOfImages && attemptCount < maxAttempts) {
            if (!camera.read(frame)) {
                System.err.println(" Failed to read camera frame");
                callback.onError("Failed to capture frame from camera");
                break;
            }

            attemptCount++;
            System.out.println(String.format(" Capture attempt %d/%d", attemptCount, maxAttempts));

            callback.onFrameUpdate(frame.clone());
            FaceDetectionResult detectionResult = detectFaceWithConfidence(frame);

            if (detectionResult.hasValidFace()) {
                System.out.println(String.format(" Valid face detected with confidence %.2f",
                        detectionResult.getConfidence()));

                Rect bestFace = detectionResult.getBestFace();
                Mat faceROI = extractFaceROI(frame, bestFace);

                if (faceROI != null) {
                    Mat processedFace = imageProcessor.preprocessFaceImage(faceROI);

                    if (imageProcessor.validateImageQuality(processedFace)) {
                        String imagePath = folderPath + student.getStudentId() + "_" +
                                String.format("%03d", capturedCount + 1) + ".jpg";

                        if (Imgcodecs.imwrite(imagePath, processedFace)) {
                            byte[] embedding = embeddingGenerator.generateEmbedding(processedFace);
                            FaceImage faceImage = new FaceImage(imagePath, embedding);
                            faceImage.setQualityScore(detectionResult.getConfidence());

                            student.getFaceData().addImage(faceImage);
                            capturedImages.add(imagePath);
                            capturedCount++;

                            callback.onImageCaptured(capturedCount, numberOfImages, detectionResult.getConfidence());
                            System.out.printf(" ✓ Captured image %d/%d (confidence: %.2f)\n",
                                    capturedCount, numberOfImages, detectionResult.getConfidence());

                            try {
                                Thread.sleep(CAPTURE_INTERVAL_MS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        } else {
                            System.err.println(" Failed to save image: " + imagePath);
                            callback.onWarning("Failed to save image, please try again");
                        }
                    } else {
                        System.out.println(" Image quality validation failed");
                        callback.onWarning("Image quality too low, please improve lighting");
                    }
                } else {
                    System.out.println(" Face ROI extraction failed");
                    callback.onWarning("Face too close to edge, please center your face");
                }
            } else {
                String warning = getDetectionFeedback(detectionResult);
                System.out.println(" " + warning);
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

        System.out.println(" Face capture completed. " + message);
        return new FaceCaptureResult(success, message, capturedImages);
    }

    private Mat extractFaceROI(Mat frame, Rect face) {
        try {
            int padding = (int) (Math.min(face.width, face.height) * 0.1);

            int x = Math.max(0, face.x - padding);
            int y = Math.max(0, face.y - padding);
            int width = Math.min(frame.cols() - x, face.width + 2 * padding);
            int height = Math.min(frame.rows() - y, face.height + 2 * padding);

            Rect paddedRect = new Rect(x, y, width, height);
            return new Mat(frame, paddedRect);

        } catch (Exception e) {
            System.err.println(" Failed to extract face ROI: " + e.getMessage());
            return null;
        }
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
            System.out.println(" getCurrentFrame: success=" + success + ", empty=" + frame.empty());
        } else {
            System.err.println(" Camera not available for getCurrentFrame");
        }
        return frame;
    }

    public boolean isCameraAvailable() {
        boolean available = camera != null && camera.isOpened();
        System.out.println(" Camera available: " + available);
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
            System.out.println(" Camera released");
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
}