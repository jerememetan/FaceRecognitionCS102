package app.service;

import app.entity.Student;
import app.model.FaceData;
import app.model.FaceDetectionResult;
import app.model.FaceCaptureResult;
import app.util.ImageProcessor;
import ConfigurationAndLogging.AppLogger;
import org.opencv.core.*;
import java.util.List;

/**
 * FaceDetection - Main orchestration class for face detection and capture operations.
 * This class coordinates all face detection services and maintains backward compatibility.
 *
 * Architecture:
 * - FaceDetector: Core DNN detection logic
 * - CameraManager: Camera hardware operations
 * - FaceRegionProcessor: Face region extraction and processing
 * - FaceCaptureService: Capture workflow orchestration
 */
public class FaceDetection {
    private final FaceDetector faceDetector;
    private final CameraManager cameraManager;
    private final FaceRegionProcessor faceRegionProcessor;
    private final FaceCaptureService faceCaptureService;

    public FaceDetection() {
        // Initialize all service components
        this.faceDetector = new FaceDetector();
        this.cameraManager = new CameraManager();
        this.faceRegionProcessor = new FaceRegionProcessor();

        // Initialize supporting components
        FaceEmbeddingGenerator embeddingGenerator = new FaceEmbeddingGenerator();
        ImageProcessor imageProcessor = new ImageProcessor();

        // Initialize capture service with all dependencies
        this.faceCaptureService = new FaceCaptureService(
            faceDetector, cameraManager, faceRegionProcessor, embeddingGenerator, imageProcessor);
    }

    public FaceDetectionResult detectFaceForPreview(Mat frame) {
        app.model.FaceDetectionResult result = faceDetector.detectFaceForPreview(frame);
        return new FaceDetectionResult(result.getFaces());
    }

    public List<Rect> detectFaces(Mat frame) {
        return faceDetector.detectFaces(frame);
    }

    public Mat drawFaceOverlay(Mat frame, FaceDetectionResult result) {
        return faceDetector.drawFaceOverlay(frame, result);
    }

    public FaceCaptureResult captureAndStoreFaceImages(Student student, int numberOfImages,
            FaceCaptureService.FaceCaptureCallback callback) {
        app.model.FaceCaptureResult result = faceCaptureService.captureAndStoreFaceImages(student, numberOfImages, callback);
        return new FaceCaptureResult(result.isSuccess(), result.getMessage(),
            result.getCapturedImages(), result.getBatchProcessingResult());
    }

    public boolean captureAndStoreFaceImages(Student student, int numberOfImages) {
        return faceCaptureService.captureAndStoreFaceImages(student, numberOfImages);
    }

    public Mat getCurrentFrame() {
        return cameraManager.getCurrentFrame();
    }

    public boolean isCameraAvailable() {
        return cameraManager.isCameraAvailable();
    }

    public boolean deleteFaceData(FaceData faceData) {
        // This functionality should probably be moved to a data service
        // For now, implement it here to maintain compatibility
        String folderPath = faceData.getFolderPath();
        java.io.File folder = new java.io.File(folderPath);

        if (folder.exists()) {
            java.io.File[] files = folder.listFiles();
            if (files != null) {
                for (java.io.File file : files) {
                    file.delete();
                }
            }
            return folder.delete();
        }
        return true;
    }

    public void release() {
        cameraManager.release();
    }

    // Helper method for embedding generation
    public Rect detectFaceInImage(Mat image) {
        return faceDetector.detectFaceInImage(image);
    }

    // Inner classes moved to app.model package for better organization
    public static class FaceDetectionResult extends app.model.FaceDetectionResult {
        public FaceDetectionResult(List<app.model.FaceCandidate> faces) {
            super(faces);
        }
    }

    public static class FaceCandidate extends app.model.FaceCandidate {
        public FaceCandidate(Rect rect, double confidence) {
            super(rect, confidence);
        }
    }

    public static class FaceCaptureResult extends app.model.FaceCaptureResult {
        public FaceCaptureResult(boolean success, String message, List<String> capturedImages,
                FaceEmbeddingGenerator.BatchProcessingResult batchProcessingResult) {
            super(success, message, capturedImages, batchProcessingResult);
        }
    }

    public interface FaceCaptureCallback extends FaceCaptureService.FaceCaptureCallback {
    }
}