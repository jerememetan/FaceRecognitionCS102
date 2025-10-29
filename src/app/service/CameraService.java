package app.service;

/**
 * Interface for camera operations - separates hardware abstraction from business logic
 */
public interface CameraService {
    /**
     * Check if camera is available and ready for use
     */
    boolean isCameraAvailable();

    /**
     * Get the current frame from camera
     */
    org.opencv.core.Mat getCurrentFrame();

    /**
     * Release camera resources
     */
    void release();
}