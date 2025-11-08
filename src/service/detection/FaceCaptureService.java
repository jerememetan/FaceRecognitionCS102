package service.detection;

import config.AppLogger;
import entity.Student;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import model.FaceCaptureResult;
import model.FaceDetectionResult;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;
import service.camera.CameraManager;
import service.embedding.FaceEmbeddingGenerator;
import util.ImageProcessor;

public class FaceCaptureService {
    private static final int CAPTURE_INTERVAL_MS = 1200;
    private static final int CAPTURE_ATTEMPT_MULTIPLIER = 15;
    private static final boolean DEBUG_LOGS = Boolean.parseBoolean(
            System.getProperty("app.faceDetectionDebug", "false"));

    private final FaceDetector faceDetector;
    private final CameraManager cameraManager;
    private final FaceRegionProcessor faceRegionProcessor;
    private final FaceEmbeddingGenerator embeddingGenerator;
    private final ImageProcessor imageProcessor;

    public FaceCaptureService(FaceDetector faceDetector, CameraManager cameraManager,
            FaceRegionProcessor faceRegionProcessor, FaceEmbeddingGenerator embeddingGenerator,
            ImageProcessor imageProcessor) {
        this.faceDetector = faceDetector;
        this.cameraManager = cameraManager;
        this.faceRegionProcessor = faceRegionProcessor;
        this.embeddingGenerator = embeddingGenerator;
        this.imageProcessor = imageProcessor;
    }

    public FaceCaptureResult captureAndStoreFaceImages(Student student, int numberOfImages,
            FaceCaptureCallback callback) {
        AppLogger.info("Starting face capture for: " + student.getName());

        if (!cameraManager.isCameraAvailable()) {
            AppLogger.error("Camera not available");
            return new FaceCaptureResult(false, "Cannot open camera", new ArrayList<>(), null);
        }

        Path folderPath = Paths.get(student.getFaceData().getFolderPath());
        try {
            Files.createDirectories(folderPath);
        } catch (Exception ex) {
            AppLogger.error("Failed to create face data directory: " + folderPath + " - " + ex.getMessage());
            return new FaceCaptureResult(false, "Cannot create storage directory", new ArrayList<>(), null);
        }

        List<String> capturedImages = new ArrayList<>();
        int capturedCount = 0;
        int attemptCount = 0;
        int maxAttempts = Math.max(numberOfImages * CAPTURE_ATTEMPT_MULTIPLIER, numberOfImages + 30);

        callback.onCaptureStarted();

        while (capturedCount < numberOfImages && attemptCount < maxAttempts) {
            Mat frame = cameraManager.getCurrentFrame();
            Mat faceROI = null;
            try {
                if (frame.empty()) {
                    AppLogger.error("Failed to read camera frame");
                    callback.onError("Failed to capture frame from camera");
                    break;
                }

                attemptCount++;
                logDebug(String.format("Capture attempt %d/%d", attemptCount, maxAttempts));

                FaceDetectionResult detectionResult = faceDetector.detectFaceForPreview(frame);

                if (detectionResult.hasValidFace()) {
                    Rect bestFace = detectionResult.getBestFace();
                    faceROI = faceRegionProcessor.extractFaceROI(frame, bestFace);

                    if (faceROI != null && !faceROI.empty()) {
                        String fileName = student.getStudentId() + "_" +
                                String.format("%03d", capturedCount + 1) + ".png";
                        Path imageFile = folderPath.resolve(fileName);

                        if (Imgcodecs.imwrite(imageFile.toString(), faceROI)) {
                            capturedImages.add(imageFile.toString());
                            capturedCount++;

                            callback.onImageCaptured(capturedCount, numberOfImages, detectionResult.getConfidence());

                            try {
                                Thread.sleep(CAPTURE_INTERVAL_MS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        } else {
                            AppLogger.error("Failed to save image: " + imageFile);
                        }
                    } else {
                        logDebug("Face ROI extraction failed");
                    }

                } else {
                    logDebug("Detection feedback: " + getDetectionFeedback(detectionResult));
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } finally {
                frame.release();
                if (faceROI != null) {
                    faceROI.release();
                }
            }
        }

        callback.onCaptureCompleted();

        FaceEmbeddingGenerator.BatchProcessingResult batchResult = null;
        if (capturedCount > 0) {
            logDebug("Processing embeddings for " + capturedCount + " captured images...");

            // Clean up existing embeddings before generating new ones
            try {
                File folder = new File(folderPath.toString());
                if (folder.exists() && folder.isDirectory()) {
                    File[] existingEmbFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".emb"));
                    if (existingEmbFiles != null && existingEmbFiles.length > 0) {
                        int deletedCount = 0;
                        for (File embFile : existingEmbFiles) {
                            if (embFile.delete()) {
                                deletedCount++;
                            } else {
                                AppLogger.error("Failed to delete existing embedding: " + embFile.getName());
                            }
                        }
                        logDebug("Cleaned up " + deletedCount + " existing embedding files");
                    }
                }
            } catch (Exception e) {
                AppLogger.error("Error during embedding cleanup: " + e.getMessage());
            }

            FaceEmbeddingGenerator.ProgressCallback progressCallback = new FaceEmbeddingGenerator.ProgressCallback() {
                @Override
                public void onProgress(String message) {
                    callback.onWarning(message);
                }
            };

            batchResult = embeddingGenerator
                    .processCapturedImages(capturedImages, null, imageProcessor, progressCallback);

            logDebug("Embedding processing result: " + batchResult.getMessage());
        }

        boolean success = capturedCount >= Math.min(10, numberOfImages);
        String message = success ? String.format("Successfully captured %d high-quality face images", capturedCount)
                : String.format("Only captured %d images, need at least %d", capturedCount,
                        Math.min(10, numberOfImages));

        return new FaceCaptureResult(success, message, capturedImages, batchResult);
    }

    public boolean captureAndStoreFaceImages(Student student, int numberOfImages) {
        FaceCaptureCallback dummyCallback = new FaceCaptureCallback() {
            public void onCaptureStarted() {}
            public void onFrameUpdate(Mat frame) {}
            public void onImageCaptured(int current, int total, double confidence) {}
            public void onWarning(String message) {
                AppLogger.warn(" Warning: " + message);
            }
            public void onError(String message) {
                AppLogger.error(" Error: " + message);
            }
            public void onCaptureCompleted() {}
        };

        FaceCaptureResult result = captureAndStoreFaceImages(student, numberOfImages, dummyCallback);
        return result.isSuccess();
    }

    private String getDetectionFeedback(FaceDetectionResult result) {
        if (result.getFaces().isEmpty()) {
            return "No face detected - please position your face in the camera view";
        } else if (result.getBestConfidence() < 0.6) {
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

    private void logDebug(String message) {
        if (DEBUG_LOGS) {
            AppLogger.info(message);
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






