package gui.detection;

import config.AppLogger;
import entity.Student;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import model.FaceCaptureResult;
import model.FaceDetectionResult;
import org.opencv.core.Mat;
import service.camera.CameraService;
import service.detection.FaceDetectionService;
import service.student.StudentManager;

/**
 * Unified camera operations manager that combines preview and capture functionality
 * Uses dependency injection for better testability and separation of concerns
 */
public class CameraOperationsManager {
    // Core dependencies
    private final CameraService cameraService;
    private final FaceDetectionService faceDetectionService;
    private final StudentManager studentManager;

    // UI components
    private JLabel videoLabel;
    private JLabel statusLabel;
    private JLabel qualityLabel;
    private JProgressBar progressBar;
    private JLabel progressTextLabel;
    private JLabel instructionLabel;

    // State management
    private Timer previewTimer;
    private boolean isCapturing = false;
    private boolean isPreviewing = false;
    private int frameCounter = 0;
    private static final int DETECTION_INTERVAL = 5;
    private FaceDetectionService.FaceDetectionResult lastDetectionResult = null;

    // Capture state
    private Student currentStudent;
    private int targetImages;
    private AtomicInteger capturedCount;
    private FaceDetectionService.FaceCaptureCallback captureCallback;

    // Colors for UI feedback
    private static final Color SUCCESS_COLOR = new Color(46, 125, 50);
    private static final Color WARNING_COLOR = new Color(255, 152, 0);
    private static final Color ERROR_COLOR = new Color(211, 47, 47);

    /**
     * Constructor with dependency injection
     */
    public CameraOperationsManager(
            CameraService cameraService,
            FaceDetectionService faceDetectionService,
            StudentManager studentManager) {
        this.cameraService = cameraService;
        this.faceDetectionService = faceDetectionService;
        this.studentManager = studentManager;
        this.capturedCount = new AtomicInteger(0);
    }

    /**
     * Set UI components for preview display
     */
    public void setPreviewComponents(JLabel videoLabel, JLabel statusLabel, JLabel qualityLabel) {
        this.videoLabel = videoLabel;
        this.statusLabel = statusLabel;
        this.qualityLabel = qualityLabel;
    }

    /**
     * Set UI components for capture progress
     */
    public void setCaptureComponents(JProgressBar progressBar, JLabel progressTextLabel, JLabel instructionLabel) {
        this.progressBar = progressBar;
        this.progressTextLabel = progressTextLabel;
        this.instructionLabel = instructionLabel;
    }

    // ===== PREVIEW OPERATIONS =====

    /**
     * Start camera preview with face detection overlay
     */
    public void startPreview() {
        if (isPreviewing) return;

        AppLogger.info("Starting camera preview...");
        if (!cameraService.isCameraAvailable()) {
            showError("Camera not available. Please check camera connection.");
            return;
        }

        updateStatus("Camera ready", SUCCESS_COLOR);
        previewTimer = new Timer(50, e -> updatePreviewFrame());
        previewTimer.start();
        isPreviewing = true;
        AppLogger.info("Preview timer started at 20fps");
    }

    /**
     * Stop camera preview
     */
    public void stopPreview() {
        if (previewTimer != null) {
            previewTimer.stop();
            isPreviewing = false;
            AppLogger.info("Preview timer stopped");
        }
    }

    /**
     * Update preview frame with face detection
     */
    private void updatePreviewFrame() {
        try {
            Mat frame = cameraService.getCurrentFrame();
            if (frame != null && !frame.empty()) {
                frameCounter++;
                if (frameCounter >= DETECTION_INTERVAL) {
                    frameCounter = 0;
                    lastDetectionResult = faceDetectionService.detectFaceForPreview(frame);
                }

                Mat displayFrame = (lastDetectionResult != null) ?
                    faceDetectionService.drawFaceOverlay(frame, lastDetectionResult) : frame.clone();

                BufferedImage bufferedImage = matToBufferedImage(displayFrame);
                if (bufferedImage != null && videoLabel != null) {
                    int displayWidth = videoLabel.getWidth();
                    int displayHeight = videoLabel.getHeight();
                    if (displayWidth > 0 && displayHeight > 0) {
                        Image scaledImage = bufferedImage.getScaledInstance(displayWidth, displayHeight, Image.SCALE_FAST);
                        ImageIcon icon = new ImageIcon(scaledImage);
                        videoLabel.setIcon(icon);
                        videoLabel.setText("");
                    }
                }

                if (!isCapturing && lastDetectionResult != null && qualityLabel != null) {
                    updateQualityFeedback(lastDetectionResult);
                }

                displayFrame.release();
                frame.release();
            } else if (!isCapturing) {
                AppLogger.warn("Empty frame received during preview");
            }
        } catch (Exception e) {
            AppLogger.error("Preview update failed: " + e.getMessage(), e);
        }
    }

    /**
     * Update quality feedback based on face detection results
     */
    private void updateQualityFeedback(FaceDetectionService.FaceDetectionResult result) {
        if (qualityLabel == null) return;

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

    // ===== CAPTURE OPERATIONS =====

    /**
     * Start face capture process for a student
     */
    public void startCapture(Student student, int targetImages, FaceDetectionService.FaceCaptureCallback callback) {
        if (isCapturing) return;

        this.currentStudent = student;
        this.targetImages = targetImages;
        this.captureCallback = callback;
        this.capturedCount.set(0);
        this.isCapturing = true;

        updateStatus("Capturing...", WARNING_COLOR);
        updateProgress(0, "Starting capture...");
        updateInstructions("<html><center><b>CAPTURING IN PROGRESS</b><br/>" +
                "Look at the camera and keep still!<br/>" +
                "Green rectangles = Good quality faces</center></html>");

        SwingWorker<FaceDetectionService.FaceCaptureResult, Void> captureWorker =
            new SwingWorker<FaceDetectionService.FaceCaptureResult, Void>() {
                @Override
                protected FaceDetectionService.FaceCaptureResult doInBackground() throws Exception {
                    return faceDetectionService.captureAndStoreFaceImages(student, targetImages, callback);
                }

                @Override
                protected void done() {
                    try {
                        FaceDetectionService.FaceCaptureResult result = get();
                        onCaptureCompleted(result);
                    } catch (Exception e) {
                        AppLogger.error("Capture worker failed: " + e.getMessage(), e);
                        showError("Capture failed: " + e.getMessage());
                        onCaptureCompleted(null);
                    }
                }
            };
        captureWorker.execute();
    }

    /**
     * Stop ongoing capture process
     */
    public void stopCapture() {
        AppLogger.info("Stopping capture...");
        isCapturing = false;
        updateStatus("Capture stopped", WARNING_COLOR);
        updateInstructions("<html><center>Capture stopped by user.<br/>" +
                "You can start again when ready.</center></html>");
        AppLogger.info("Capture manually stopped");
    }

    /**
     * Handle capture completion
     */
    private void onCaptureCompleted(FaceDetectionService.FaceCaptureResult result) {
        isCapturing = false;

        if (result != null && result.isSuccess()) {
            updateStatus("Capture successful!", SUCCESS_COLOR);
            updateInstructions("<html><center><b>SUCCESS!</b><br/>" +
                    "Face images captured successfully.</center></html>");
            updateProgress(100, "100% Complete");

            // Show summary
            showCaptureSummary(result);
        } else {
            updateStatus("Capture failed", ERROR_COLOR);
            updateInstructions("<html><center><font color='red'>Capture failed.</font><br/>" +
                    "Please try again with better lighting.</center></html>");
            AppLogger.warn("Capture failed - insufficient valid images");
        }
    }

    /**
     * Show capture summary dialog
     */
    private void showCaptureSummary(FaceDetectionService.FaceCaptureResult result) {
        StringBuilder msg = new StringBuilder();
        msg.append(String.format("Capture completed successfully!\nCaptured %d images for %s.",
                result.getCapturedImages().size(), currentStudent.getName()));

        // Add batch processing results if available
        var batchResult = result.getBatchProcessingResult();
        if (batchResult != null) {
            int totalRemoved = batchResult.getTotalRemovedCount();
            if (totalRemoved > 0) {
                msg.append(String.format("\n\nAuto-removed %d image(s) for better recognition accuracy:", totalRemoved));
                if (batchResult.getRemovedOutlierCount() > 0) {
                    msg.append(String.format("\n  - %d statistical outliers", batchResult.getRemovedOutlierCount()));
                }
                if (batchResult.getRemovedWeakCount() > 0) {
                    msg.append(String.format("\n  - %d weak quality images", batchResult.getRemovedWeakCount()));
                }
                msg.append(String.format("\nFinal count: %d high-quality images retained.",
                        batchResult.getProcessedCount() - totalRemoved));
            } else {
                msg.append("\n\nAll images passed quality checks - no issues detected.");
            }
        }

        JOptionPane.showMessageDialog(null, htmlize(msg.toString()),
                "Capture Summary", JOptionPane.INFORMATION_MESSAGE);
    }

    // ===== UTILITY METHODS =====

    /**
     * Update status label
     */
    private void updateStatus(String text, Color color) {
        if (statusLabel != null) {
            statusLabel.setText(text);
            statusLabel.setForeground(color);
        }
    }

    /**
     * Update progress bar and text
     */
    private void updateProgress(int percentage, String text) {
        if (progressBar != null) {
            progressBar.setValue(percentage);
            progressBar.setString(text);
        }
        if (progressTextLabel != null) {
            progressTextLabel.setText(text);
        }
    }

    /**
     * Update instruction label
     */
    private void updateInstructions(String htmlText) {
        if (instructionLabel != null) {
            instructionLabel.setText(htmlText);
        }
    }

    /**
     * Show error message
     */
    private void showError(String message) {
        updateStatus("Error: " + message, ERROR_COLOR);
        AppLogger.error("Error: " + message);
        JOptionPane.showMessageDialog(null, htmlize(message), "Error", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Convert HTML entities
     */
    private String htmlize(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.toLowerCase().startsWith("<html>")) return text;
        String escaped = trimmed.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                                .replace("\n", "<br/>").replace("\\n", "<br/>");
        return "<html>" + escaped + "</html>";
    }

    /**
     * Convert OpenCV Mat to BufferedImage
     */
    private BufferedImage matToBufferedImage(Mat mat) {
        try {
            if (mat.empty()) return null;

            int type = mat.channels() == 1 ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_3BYTE_BGR;
            byte[] buffer = new byte[(int) (mat.total() * mat.elemSize())];
            mat.get(0, 0, buffer);

            BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
            final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            System.arraycopy(buffer, 0, targetPixels, 0, buffer.length);
            return image;
        } catch (Exception e) {
            AppLogger.error("Mat to BufferedImage conversion failed: " + e.getMessage(), e);
            return null;
        }
    }

    // ===== GETTERS =====

    public boolean isCapturing() { return isCapturing; }
    public boolean isPreviewing() { return isPreviewing; }
    public int getCapturedCount() { return capturedCount.get(); }
}






