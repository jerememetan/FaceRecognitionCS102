package app.gui;

import app.entity.Student;
import app.service.StudentManager;
import app.service.FaceDetection;
import app.service.FaceEmbeddingGenerator;
import ConfigurationAndLogging.AppLogger;
import org.opencv.core.Mat;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CaptureManager {
    private FaceDetection faceDetection;
    private Student student;
    private StudentManager studentManager;
    private AtomicBoolean isCapturing;
    private AtomicInteger capturedCount;
    private int targetImages;
    private boolean captureCompleted = false;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private JLabel progressLabel;
    private JLabel instructionLabel;
    private FaceCaptureDialog dialog;
    private static final Color SUCCESS_COLOR = new Color(46, 125, 50);
    private static final Color WARNING_COLOR = new Color(255, 152, 0);
    private static final Color ERROR_COLOR = new Color(211, 47, 47);

    public CaptureManager(FaceDetection fd, Student s, StudentManager sm, AtomicBoolean ic, AtomicInteger cc, int ti, boolean cc2, JLabel sl, JProgressBar pb, JLabel pl, JLabel il, FaceCaptureDialog d) {
        this.faceDetection = fd;
        this.student = s;
        this.studentManager = sm;
        this.isCapturing = ic;
        this.capturedCount = cc;
        this.targetImages = ti;
        this.captureCompleted = cc2;
        setLabels(sl, pb, pl, il);
        this.dialog = d;
    }

    public void setLabels(JLabel sl, JProgressBar pb, JLabel pl, JLabel il) {
        this.statusLabel = sl;
        this.progressBar = pb;
        this.progressLabel = pl;
        this.instructionLabel = il;
    }

    public void setTargetImages(int ti) {
        this.targetImages = ti;
    }

    public void startCapture() {
        if (isCapturing.get()) return;
        AppLogger.info("Starting capture process...");
        isCapturing.set(true);
        capturedCount.set(0);
        statusLabel.setText("Capturing...");
        statusLabel.setForeground(WARNING_COLOR);
        progressBar.setValue(0);
        instructionLabel.setText("<html><center><b>CAPTURING IN PROGRESS</b><br/>" +
                "Look at the camera and keep still!<br/>" +
                "Green rectangles = Good quality faces</center></html>");
        SwingWorker<Boolean, Void> captureWorker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return performCapture();
            }
            @Override
            protected void done() {
                try {
                    boolean success = get();
                    onCaptureCompleted(success);
                } catch (Exception e) {
                    AppLogger.error("Capture worker failed: " + e.getMessage(), e);
                    showError("Capture failed: " + e.getMessage());
                    onCaptureCompleted(false);
                }
            }
        };
        captureWorker.execute();
    }

    private boolean performCapture() {
        AppLogger.info("Performing capture with target: " + targetImages);
        FaceDetection.FaceCaptureCallback callback = new FaceDetection.FaceCaptureCallback() {
            @Override
            public void onCaptureStarted() {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Starting capture...");
                    System.out.println("Debug: Capture process started");
                });
            }
            @Override
            public void onFrameUpdate(Mat frame) {}
            @Override
            public void onImageCaptured(int current, int total, double confidence) {
                SwingUtilities.invokeLater(() -> {
                    capturedCount.set(current);
                    int percentage = (int) ((current * 100.0) / total);
                    progressBar.setValue(percentage);
                    progressBar.setString(current + "/" + total + " (" + percentage + "%)");
                    progressLabel.setText(String.format("Images captured: %d/%d (Quality: %.1f%%)", current, total, confidence * 100));
                    statusLabel.setText(String.format("Captured %d/%d", current, total));
                    AppLogger.info(String.format("Debug: Image saved with confidence %.2f", confidence));
                });
            }
            @Override
            public void onWarning(String message) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText(htmlize(message));
                    statusLabel.setForeground(WARNING_COLOR);
                    System.out.println("Debug: Warning - " + message);
                });
            }
            @Override
            public void onError(String message) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText(htmlize("Error: " + message));
                    statusLabel.setForeground(ERROR_COLOR);
                    System.out.println("Debug: Error - " + message);
                });
            }
            @Override
            public void onCaptureCompleted() {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Capture process completed");
                    statusLabel.setForeground(SUCCESS_COLOR);
                    AppLogger.info("Debug: Capture process finished");
                });
            }
        };
        FaceDetection.FaceCaptureResult result = faceDetection.captureAndStoreFaceImages(student, targetImages, callback);
        AppLogger.info("Capture result: " + result.isSuccess() + " - " + result.getMessage());
        java.util.List<String> capturedImages = result.getCapturedImages();
        if (capturedImages != null && !capturedImages.isEmpty()) {
            StringBuilder msg = new StringBuilder();
            msg.append(String.format("Capture completed successfully!\nCaptured %d images for %s.", capturedImages.size(), student.getName()));
            FaceEmbeddingGenerator.BatchProcessingResult batchResult = result.getBatchProcessingResult();
            if (batchResult != null) {
                int outlierCount = batchResult.getRemovedOutlierCount();
                int weakCount = batchResult.getRemovedWeakCount();
                int totalRemoved = batchResult.getTotalRemovedCount();
                if (totalRemoved > 0) {
                    msg.append(String.format("\n\n Auto-removed %d image(s) for better recognition accuracy:", totalRemoved));
                    if (outlierCount > 0) {
                        msg.append(String.format("\n  - %d statistical outliers", outlierCount));
                    }
                    if (weakCount > 0) {
                        msg.append(String.format("\n  - %d weak quality images", weakCount));
                    }
                    msg.append(String.format("\nFinal count: %d high-quality images retained.", batchResult.getProcessedCount() - totalRemoved));
                } else {
                    msg.append("\n\n All images passed quality checks - no issues detected.");
                }
            }
            JOptionPane.showMessageDialog(dialog, htmlize(msg.toString()), "Capture Summary", JOptionPane.INFORMATION_MESSAGE);
        } else {
            String msg = "Capture completed but no images were captured.\n" + result.getMessage();
            JOptionPane.showMessageDialog(dialog, htmlize(msg), "Capture Summary", JOptionPane.WARNING_MESSAGE);
        }
        return result.isSuccess();
    }

    public void stopCapture() {
        AppLogger.info("Stopping capture...");
        isCapturing.set(false);
        statusLabel.setText("Capture stopped");
        statusLabel.setForeground(WARNING_COLOR);
        AppLogger.info("Debug: Capture manually stopped");
        instructionLabel.setText("<html><center>Capture stopped by user.<br/>" +
                "You can start again when ready.</center></html>");
    }

    private void onCaptureCompleted(boolean success) {
        isCapturing.set(false);
        captureCompleted = success;
        if (success) {
            statusLabel.setText("Capture successful!");
            statusLabel.setForeground(SUCCESS_COLOR);
            instructionLabel.setText("<html><center><b>SUCCESS!</b><br/>" +
                    "Face images captured successfully.</center></html>");
            progressBar.setValue(100);
            progressBar.setString("100% Complete");
            AppLogger.info("Debug: All images captured successfully");
        } else {
            statusLabel.setText("Capture failed");
            statusLabel.setForeground(ERROR_COLOR);
            instructionLabel.setText("<html><center><font color='red'>Capture failed.</font><br/>" +
                    "Please try again with better lighting.</center></html>");
            AppLogger.warn("Debug: Capture failed - insufficient valid images");
        }
    }

    private void showError(String message) {
        statusLabel.setText(htmlize("Error: " + message));
        statusLabel.setForeground(ERROR_COLOR);
        AppLogger.error("Debug: Error - " + message);
        JOptionPane.showMessageDialog(dialog, htmlize(message), "Error", JOptionPane.ERROR_MESSAGE);
    }

    private String htmlize(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.toLowerCase().startsWith("<html>")) return text;
        String escaped = trimmed.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br/>").replace("\\n", "<br/>");
        return "<html>" + escaped + "</html>";
    }

    public boolean isCaptureCompleted() {
        return captureCompleted;
    }
}