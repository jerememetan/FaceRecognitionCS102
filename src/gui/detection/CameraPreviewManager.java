package gui.detection;

import config.AppLogger;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import javax.swing.*;
import org.opencv.core.Mat;
import service.detection.FaceDetection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import util.ColourTheme;
public class CameraPreviewManager {
    private FaceDetection faceDetection;
    private final ScheduledExecutorService previewExecutor;
    private ScheduledFuture<?> previewTask;
    private final AtomicBoolean previewActive = new AtomicBoolean(false);
    private int frameCounter = 0;
    private static final int DETECTION_INTERVAL = 5;
    private static final int CAPTURE_DETECTION_INTERVAL = 8;
    private FaceDetection.FaceDetectionResult lastDetectionResult = null;
    private long lastDetectionTimestampMs = 0L;
    private JLabel videoLabel;
    private JLabel statusLabel;
    private JLabel qualityLabel;
    private boolean isCapturing = false;
    private static final Color SUCCESS_COLOR = ColourTheme.SUCCESS_COLOR;
    private static final Color WARNING_COLOR = ColourTheme.WARNING_COLOR;
    private static final Color ERROR_COLOR = ColourTheme.DANGER;

    public CameraPreviewManager(FaceDetection fd, JLabel vl, JLabel sl, JLabel ql) {
        this.faceDetection = fd;
        setLabels(vl, sl, ql);
        this.previewExecutor = Executors.newSingleThreadScheduledExecutor(
                createDaemonThreadFactory("camera-preview-manager"));
    }

    public void setLabels(JLabel vl, JLabel sl, JLabel ql) {
        this.videoLabel = vl;
        this.statusLabel = sl;
        this.qualityLabel = ql;
    }

    public void setCapturing(boolean capturing) {
        this.isCapturing = capturing;
    }

    public void startPreview() {
        AppLogger.info("Starting camera preview...");
        if (!faceDetection.isCameraAvailable()) {
            showError("Camera not available. Please check camera connection.");
            return;
        }
        runOnUiThread(() -> {
            statusLabel.setText("Camera ready");
            statusLabel.setForeground(SUCCESS_COLOR);
        });
        frameCounter = 0;
        lastDetectionResult = null;
        previewActive.set(true);
        previewTask = previewExecutor.scheduleAtFixedRate(
                this::processPreview,
                0,
                50,
                TimeUnit.MILLISECONDS);
        AppLogger.info("Preview scheduler started at 20fps");
    }

    public void stopPreview() {
        if (!previewActive.get()) {
            return;
        }

        previewActive.set(false);
        if (previewTask != null) {
            previewTask.cancel(true);
            previewTask = null;
        }

        SwingUtilities.invokeLater(() -> {
            if (videoLabel != null) {
                videoLabel.setIcon(null);
                videoLabel.setText("");
            }
        });

        lastDetectionResult = null;
        AppLogger.info("Preview scheduler stopped");
    }

    private void processPreview() {
        if (!previewActive.get()) {
            return;
        }

        Mat frame = null;
        Mat displayFrame = null;
        try {
            frame = faceDetection.getCurrentFrame();
            if (frame == null || frame.empty()) {
                if (!isCapturing) {
                    AppLogger.warn("Debug: Empty frame received");
                }
                return;
            }

            frameCounter++;
            int detectionInterval = isCapturing ? CAPTURE_DETECTION_INTERVAL : DETECTION_INTERVAL;
            boolean refreshDetection = lastDetectionResult == null || frameCounter >= detectionInterval;
            if (refreshDetection) {
                frameCounter = 0;
                lastDetectionResult = faceDetection.detectFaceForPreview(frame);
                lastDetectionTimestampMs = System.currentTimeMillis();
            }

            FaceDetection.FaceDetectionResult detectionForUi =
                    (lastDetectionResult != null && isDetectionFresh()) ? lastDetectionResult : null;

            displayFrame = (detectionForUi != null)
                    ? faceDetection.drawFaceOverlay(frame, detectionForUi)
                    : frame.clone();

            BufferedImage bufferedImage = matToBufferedImage(displayFrame);
            if (bufferedImage != null) {
                SwingUtilities.invokeLater(() -> renderPreview(bufferedImage, detectionForUi));
            }
        } catch (Exception e) {
            AppLogger.error("Preview update failed: " + e.getMessage(), e);
        } finally {
            if (displayFrame != null) {
                displayFrame.release();
            }
            if (frame != null) {
                frame.release();
            }
        }
    }

    private void renderPreview(BufferedImage bufferedImage, FaceDetection.FaceDetectionResult detectionResult) {
        if (!previewActive.get()) {
            return;
        }

        if (videoLabel != null && bufferedImage != null) {
            int displayWidth = videoLabel.getWidth();
            int displayHeight = videoLabel.getHeight();
            Image imageToShow;
            if (displayWidth > 0 && displayHeight > 0) {
                imageToShow = bufferedImage.getScaledInstance(displayWidth, displayHeight, Image.SCALE_FAST);
            } else {
                imageToShow = bufferedImage;
            }
            videoLabel.setIcon(new ImageIcon(imageToShow));
            videoLabel.setText("");
        }

        if (!isCapturing && detectionResult != null) {
            updateQualityFeedback(detectionResult);
        } else if (!isCapturing && detectionResult == null && qualityLabel != null && !isDetectionFresh()) {
            qualityLabel.setText("Face quality: No face detected");
            qualityLabel.setForeground(ERROR_COLOR);
        }
    }

    private void updateQualityFeedback(FaceDetection.FaceDetectionResult result) {
        if (result.hasValidFace()) {
            double confidence = result.getConfidence();
            if (confidence >= 0.8) {
                qualityLabel.setText("Face quality: Excellent (" + String.format("%.1f%%", confidence * 100) + ")");
                qualityLabel.setForeground(SUCCESS_COLOR);
            } else if (confidence >= 0.6) {
                qualityLabel.setText("Face quality: Good (" + String.format("%.1f%%", confidence * 100) + ")");
                qualityLabel.setForeground(WARNING_COLOR);
            } else if (confidence >= 0.3) {
                qualityLabel.setText("Face quality: Poor (" + String.format("%.1f%%", confidence * 100) + ") - Improve lighting");
                qualityLabel.setForeground(ERROR_COLOR);
            } else {
                qualityLabel.setText("Face quality: Very poor (" + String.format("%.1f%%", confidence * 100) + ")");
                qualityLabel.setForeground(ERROR_COLOR);
            }
        } else {
            qualityLabel.setText("Face quality: No face detected");
            qualityLabel.setForeground(ERROR_COLOR);
        }
    }

    private BufferedImage matToBufferedImage(Mat mat) {
        try {
            if (mat.empty()) {
                System.err.println(" Cannot convert empty Mat to BufferedImage");
                return null;
            }
            int type;
            int channels = mat.channels();
            if (channels == 1) {
                type = BufferedImage.TYPE_BYTE_GRAY;
            } else if (channels == 3) {
                type = BufferedImage.TYPE_3BYTE_BGR;
            } else {
                System.err.println(" Unsupported number of channels: " + channels);
                return null;
            }
            byte[] buffer = new byte[(int) (mat.total() * mat.elemSize())];
            mat.get(0, 0, buffer);
            BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
            final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            System.arraycopy(buffer, 0, targetPixels, 0, buffer.length);
            return image;
        } catch (Exception e) {
            System.err.println(" Mat to BufferedImage conversion failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private boolean isDetectionFresh() {
        if (lastDetectionTimestampMs <= 0) {
            return false;
        }
        long age = System.currentTimeMillis() - lastDetectionTimestampMs;
        return age <= 1000;
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            statusLabel.setText("Error: " + message);
            statusLabel.setForeground(ERROR_COLOR);
            AppLogger.error("Debug: Error - " + message);
            JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
        });
    }

    public void dispose() {
        stopPreview();
        previewExecutor.shutdownNow();
    }

    private ThreadFactory createDaemonThreadFactory(String threadName) {
        return runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, e) ->
                    AppLogger.error("Preview thread error: " + e.getMessage(), e));
            return thread;
        };
    }

    private void runOnUiThread(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }
}






