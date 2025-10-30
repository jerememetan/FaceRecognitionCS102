package app.service;

import app.model.FaceDetectionResult;
import app.model.FaceCandidate;
import app.util.ModuleLoader;
import ConfigurationAndLogging.AppConfig;
import ConfigurationAndLogging.AppLogger;
import org.opencv.core.*;
import org.opencv.dnn.Net;
import org.opencv.dnn.Dnn;
import org.opencv.imgproc.Imgproc;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FaceDetector {
    private static final double MIN_CONFIDENCE_SCORE = 0.6;
    private static final double MIN_FACE_SIZE = 50.0;
    private static final double MAX_FACE_SIZE = 400.0;
    private static final boolean DEBUG_LOGS = Boolean.parseBoolean(
            System.getProperty("app.faceDetectionDebug", "false"));

    private Net dnnFaceDetector;

    public FaceDetector() {
        ModuleLoader.ensureOpenCVLoaded();
        initializeDetector();
    }

    private void initializeDetector() {
        try {
            String modelConfiguration = AppConfig.getInstance().getDetectionModelConfigurationPath();
            String modelWeights = AppConfig.getInstance().getDetectionModelWeightsPath();

            if (new File(modelConfiguration).exists() && new File(modelWeights).exists()) {
                dnnFaceDetector = Dnn.readNetFromTensorflow(modelWeights, modelConfiguration);
                AppLogger.info("DNN face detector loaded successfully");
            } else {
                AppLogger.error("DNN model files not found; face detection unavailable");
                dnnFaceDetector = null;
            }
        } catch (Exception e) {
            AppLogger.error("Face detector initialization failed: " + e.getMessage());
            throw new RuntimeException("Failed to initialize face detector: " + e.getMessage(), e);
        }
    }

    public FaceDetectionResult detectFaceForPreview(Mat frame) {
        if (frame.empty()) {
            return new FaceDetectionResult(new ArrayList<>());
        }
        return detectFaceWithConfidence(frame);
    }

    public List<Rect> detectFaces(Mat frame) {
        FaceDetectionResult result = detectFaceForPreview(frame);
        List<Rect> faces = new ArrayList<>();

        for (FaceCandidate candidate : result.getFaces()) {
            faces.add(candidate.rect);
        }

        logDebug("=== FACE DETECTION DEBUG ===");
        logDebug("Frame size: " + frame.size());
        logDebug("Detected " + faces.size() + " faces");

        for (int i = 0; i < faces.size(); i++) {
            Rect rect = faces.get(i);
            logDebug(String.format("Face %d: (%d, %d, %dx%d) size=%d%%",
                    i, rect.x, rect.y, rect.width, rect.height,
                    (100 * rect.width * rect.height) / (frame.width() * frame.height())));

            if (rect.width < 10 || rect.height < 10) {
                AppLogger.error("FACE TOO SMALL!");
            }
            if (rect.width > frame.width() * 0.9) {
                AppLogger.error("FACE TOO LARGE (whole frame)!");
            }
            if (rect.x + rect.width > frame.width()) {
                AppLogger.error("RECT OUT OF BOUNDS!");
            }
        }

        return faces;
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

            Mat detectionsFloat = new Mat();
            detections.convertTo(detectionsFloat, CvType.CV_32F);

            int numDetections = (int) detectionsFloat.size(2);
            int dims = (int) detectionsFloat.size(3);

            double configuredMinConfidence = Math.max(0.05,
                    Math.min(0.99, AppConfig.getInstance().getDnnConfidence()));
            double configuredMinSize = Math.max(20.0, AppConfig.getInstance().getDetectionMinSize());

            List<FaceCandidate> faces = selectCandidatesWithFallback(frame, detectionsFloat, numDetections, dims,
                    configuredMinConfidence, configuredMinSize);

            blob.release();
            detections.release();
            detectionsFloat.release();

            return new FaceDetectionResult(faces);

        } catch (Exception e) {
            AppLogger.error("DNN detection failed: " + e.getMessage());
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

    public Rect detectFaceInImage(Mat image) {
        if (dnnFaceDetector == null || image.empty()) {
            return null;
        }

        try {
            Size size = new Size(300, 300);
            Mat blob = Dnn.blobFromImage(image, 1.0, size, new Scalar(104.0, 117.0, 123.0));

            dnnFaceDetector.setInput(blob);
            Mat detections = dnnFaceDetector.forward();

            Mat detectionsFloat = new Mat();
            detections.convertTo(detectionsFloat, CvType.CV_32F);

            int numDetections = (int) detectionsFloat.size(2);
            int dims = (int) detectionsFloat.size(3);

            double configuredMinConfidence = Math.max(0.05,
                    Math.min(0.99, AppConfig.getInstance().getDnnConfidence()));
            double configuredMinSize = Math.max(20.0, AppConfig.getInstance().getDetectionMinSize());

            List<FaceCandidate> faces = selectCandidatesWithFallback(image, detectionsFloat, numDetections, dims,
                    configuredMinConfidence, configuredMinSize);

            Rect bestFace = null;
            double bestConfidence = 0.0;
            for (FaceCandidate candidate : faces) {
                if (candidate.confidence > bestConfidence) {
                    bestConfidence = candidate.confidence;
                    bestFace = candidate.rect;
                }
            }

            blob.release();
            detections.release();
            detectionsFloat.release();

            return bestFace;

        } catch (Exception e) {
            AppLogger.error("Face detection in image failed: " + e.getMessage());
            return null;
        }
    }

    private List<FaceCandidate> selectCandidatesWithFallback(Mat frame, Mat detectionsFloat, int numDetections,
            int dims, double configuredMinConfidence, double configuredMinSize) {
        double maxFaceSize = Math.max(MAX_FACE_SIZE, configuredMinSize * 6.0);
        List<FaceCandidate> faces = filterDetections(frame, detectionsFloat, numDetections, dims,
                configuredMinConfidence, configuredMinSize, maxFaceSize);

        if (!faces.isEmpty()) {
            return faces;
        }

        double fallbackConfidence = Math.max(0.20, configuredMinConfidence - 0.15);
        double fallbackMinSize = Math.max(24.0, configuredMinSize * 0.75);

        boolean canRelaxConfidence = fallbackConfidence < configuredMinConfidence - 1e-6;
        boolean canRelaxSize = fallbackMinSize < configuredMinSize - 1e-6;

        if (!canRelaxConfidence && !canRelaxSize) {
            return faces;
        }

        double fallbackMaxFaceSize = Math.max(MAX_FACE_SIZE, fallbackMinSize * 6.0);
        List<FaceCandidate> relaxedFaces = filterDetections(frame, detectionsFloat, numDetections, dims,
                canRelaxConfidence ? fallbackConfidence : configuredMinConfidence,
                canRelaxSize ? fallbackMinSize : configuredMinSize, fallbackMaxFaceSize);

        if (!relaxedFaces.isEmpty()) {
            AppLogger.warn(String.format(
                    "DNN detection fallback applied (confidence %.2f -> %.2f, minSize %.1f -> %.1f)",
                    configuredMinConfidence, canRelaxConfidence ? fallbackConfidence : configuredMinConfidence,
                    configuredMinSize, canRelaxSize ? fallbackMinSize : configuredMinSize));
            return relaxedFaces;
        }

        return faces;
    }

    private List<FaceCandidate> filterDetections(Mat frame, Mat detectionsFloat, int numDetections, int dims,
            double minConfidence, double minSize, double maxFaceSize) {
        List<FaceCandidate> faces = new ArrayList<>();

        for (int i = 0; i < numDetections; i++) {
            float[] detection = new float[dims];
            detectionsFloat.get(new int[] { 0, 0, i, 0 }, detection);

            float confidence = detection[2];
            if (confidence < minConfidence) {
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

            if (width < minSize || height < minSize || width > maxFaceSize || height > maxFaceSize) {
                logDebug(String.format(
                        "DNN candidate rejected (size out of bounds): conf=%.2f width=%d height=%d", confidence,
                        width, height));
                continue;
            }

            Rect face = new Rect(x1, y1, width, height);
            logDebug(String.format("DNN candidate accepted: conf=%.2f rect=[%d,%d,%d,%d]", confidence, face.x,
                    face.y, face.width, face.height));
            faces.add(new FaceCandidate(face, confidence));
        }

        return faces;
    }

    private void logDebug(String message) {
        if (DEBUG_LOGS) {
            System.out.println(message);
        }
    }
}