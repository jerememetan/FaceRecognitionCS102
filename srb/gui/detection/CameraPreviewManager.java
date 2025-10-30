package gui.detection;

import config.AppLogger;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import javax.swing.*;
import model.FaceDetectionResult;
import org.opencv.core.Mat;
import service.detection.FaceDetection;

public class CameraPreviewManager {
    private FaceDetection faceDetection;
    private Timer previewTimer;
    private int frameCounter = 0;
    private static final int DETECTION_INTERVAL = 5;
    private FaceDetection.FaceDetectionResult lastDetectionResult = null;
    private volatile Mat sharedFrame = null;
    private final Object frameLock = new Object();
    private JLabel videoLabel;
    private JLabel statusLabel;
    private JLabel qualityLabel;
    private boolean isCapturing = false;
    private static final Color SUCCESS_COLOR = new Color(46, 125, 50);
    private static final Color WARNING_COLOR = new Color(255, 152, 0);
    private static final Color ERROR_COLOR = new Color(211, 47, 47);

    public CameraPreviewManager(FaceDetection fd, JLabel vl, JLabel sl, JLabel ql) {
        this.faceDetection = fd;
        setLabels(vl, sl, ql);
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
        statusLabel.setText("Camera ready");
        statusLabel.setForeground(SUCCESS_COLOR);
        previewTimer = new Timer(50, e -> updatePreview());
        previewTimer.start();
        AppLogger.info("Preview timer started at 20fps");
    }

    public void stopPreview() {
        if (previewTimer != null) {
            previewTimer.stop();
            AppLogger.info("Preview timer stopped");
        }
    }

    private void updatePreview() {
        try {
            Mat frame = faceDetection.getCurrentFrame();
            if (frame != null && !frame.empty()) {
                frameCounter++;
                if (frameCounter >= DETECTION_INTERVAL) {
                    frameCounter = 0;
                    lastDetectionResult = faceDetection.detectFaceForPreview(frame);
                }
                Mat displayFrame = (lastDetectionResult != null) ? faceDetection.drawFaceOverlay(frame, lastDetectionResult) : frame.clone();
                BufferedImage bufferedImage = matToBufferedImage(displayFrame);
                if (bufferedImage != null) {
                    int displayWidth = videoLabel.getWidth();
                    int displayHeight = videoLabel.getHeight();
                    if (displayWidth > 0 && displayHeight > 0) {
                        Image scaledImage = bufferedImage.getScaledInstance(displayWidth, displayHeight, Image.SCALE_FAST);
                        ImageIcon icon = new ImageIcon(scaledImage);
                        videoLabel.setIcon(icon);
                        videoLabel.setText("");
                    }
                }
                if (!isCapturing && lastDetectionResult != null) {
                    updateQualityFeedback(lastDetectionResult);
                }
                displayFrame.release();
                frame.release();
            } else {
                if (!isCapturing) {
                    AppLogger.warn("Debug: Empty frame received");
                }
            }
        } catch (Exception e) {
            AppLogger.error("Preview update failed: " + e.getMessage(), e);
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

    private void showError(String message) {
        statusLabel.setText("Error: " + message);
        statusLabel.setForeground(ERROR_COLOR);
        AppLogger.error("Debug: Error - " + message);
        JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
    }
}






