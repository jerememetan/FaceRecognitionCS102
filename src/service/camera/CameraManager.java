package service.camera;

import config.AppLogger;
import org.opencv.core.CvException;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import util.ModuleLoader;

/**
 * Low-level camera hardware abstraction implementing CameraService interface
 */
public class CameraManager implements CameraService {
    private VideoCapture camera;
    private final Object cameraLock = new Object();
    private static final boolean DEBUG_LOGS = Boolean.parseBoolean(
            System.getProperty("app.faceDetectionDebug", "false"));

    public CameraManager() {
        ModuleLoader.ensureOpenCVLoaded();
        initializeCamera();
    }

    private void initializeCamera() {
        synchronized (cameraLock) {
            camera = new VideoCapture(0);
            if (camera.isOpened()) {
                AppLogger.info("Camera initialized successfully");
            } else {
                AppLogger.error("Camera failed to initialize");
            }
        }
    }

    @Override
    public Mat getCurrentFrame() {
        synchronized (cameraLock) {
            if (camera == null || !camera.isOpened()) {
                AppLogger.error("Camera not available for getCurrentFrame");
                return new Mat();
            }

            Mat frame = new Mat();
            try {
                boolean success = camera.read(frame);
                logDebug("getCurrentFrame: success=" + success + ", empty=" + frame.empty());
                if (!success) {
                    AppLogger.error("Camera read failed");
                    frame.release();
                    return new Mat();
                }
                return frame;
            } catch (CvException ex) {
                AppLogger.error("Camera read encountered an error: " + ex.getMessage(), ex);
                frame.release();
                return new Mat();
            } catch (RuntimeException ex) {
                AppLogger.error("Camera read encountered a runtime error: " + ex.getMessage(), ex);
                frame.release();
                return new Mat();
            }
        }
    }

    @Override
    public boolean isCameraAvailable() {
        synchronized (cameraLock) {
            boolean available = camera != null && camera.isOpened();
            logDebug("Camera available: " + available);
            return available;
        }
    }

    @Override
    public void release() {
        synchronized (cameraLock) {
            if (camera != null) {
                try {
                    if (camera.isOpened()) {
                        camera.release();
                        AppLogger.info("Camera released");
                    }
                } catch (CvException ex) {
                    AppLogger.error("Camera release encountered an error: " + ex.getMessage(), ex);
                } catch (RuntimeException ex) {
                    AppLogger.error("Camera release encountered a runtime error: " + ex.getMessage(), ex);
                }
            }
        }
    }

    private void logDebug(String message) {
        if (DEBUG_LOGS) {
            System.out.println(message);
        }
    }
}






