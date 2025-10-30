package service.detection;

import entity.Student;
import model.FaceCaptureResult;
import model.FaceDetectionResult;
import service.embedding.FaceEmbeddingGenerator;

/**
 * Interface for face detection operations
 */
public interface FaceDetectionService {
    /**
     * Detect faces in a frame for preview purposes
     */
    FaceDetectionResult detectFaceForPreview(org.opencv.core.Mat frame);

    /**
     * Draw face overlay on frame
     */
    org.opencv.core.Mat drawFaceOverlay(org.opencv.core.Mat frame, FaceDetectionResult result);

    /**
     * Capture and store face images with callback
     */
    FaceCaptureResult captureAndStoreFaceImages(
        entity.Student student,
        int targetImages,
        FaceCaptureCallback callback
    );

    /**
     * Check if camera is available through this service
     */
    boolean isCameraAvailable();

    // Inner interfaces for callbacks and results
    interface FaceCaptureCallback {
        void onCaptureStarted();
        void onFrameUpdate(org.opencv.core.Mat frame);
        void onImageCaptured(int current, int total, double confidence);
        void onWarning(String message);
        void onError(String message);
        void onCaptureCompleted();
    }

    interface FaceDetectionResult {
        boolean hasValidFace();
        double getConfidence();
        // Other result methods...
    }

    interface FaceCaptureResult {
        boolean isSuccess();
        String getMessage();
        java.util.List<String> getCapturedImages();
        FaceEmbeddingGenerator.BatchProcessingResult getBatchProcessingResult();
    }
}






