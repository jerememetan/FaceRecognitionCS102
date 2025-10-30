package service.camera;

import config.AppLogger;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import util.ModuleLoader;

/**
 * Low-level camera hardware abstraction implementing CameraService interface
 */
public class CameraManager implements CameraService {
    private VideoCapture camera;
    private static final boolean DEBUG_LOGS = Boolean.parseBoolean(
            System.getProperty("app.faceDetectionDebug", "false"));

    public CameraManager() {
        ModuleLoader.ensureOpenCVLoaded();
        initializeCamera();
    }

    private void initializeCamera() {
        camera = new VideoCapture(0);
        if (camera.isOpened()) {
            AppLogger.info("Camera initialized successfully");
        } else {
            AppLogger.error("Camera failed to initialize");
        }
    }

    @Override
    public Mat getCurrentFrame() {
        Mat frame = new Mat();
        if (camera != null && camera.isOpened()) {
            boolean success = camera.read(frame);
            logDebug("getCurrentFrame: success=" + success + ", empty=" + frame.empty());
        } else {
            AppLogger.error("Camera not available for getCurrentFrame");
        }
        return frame;
    }

    @Override
    public boolean isCameraAvailable() {
        boolean available = camera != null && camera.isOpened();
        logDebug("Camera available: " + available);
        return available;
    }

    @Override
    public void release() {
        if (camera != null && camera.isOpened()) {
            camera.release();
            AppLogger.info("Camera released");
        }
    }

    private void logDebug(String message) {
        if (DEBUG_LOGS) {
            System.out.println(message);
        }
    }
}






