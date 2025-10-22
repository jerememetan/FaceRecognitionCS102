package app.gui;

import app.entity.Student;
import app.service.StudentManager;
import app.service.FaceDetection;
import app.service.FaceEmbeddingGenerator;
import org.opencv.core.*;
import ConfigurationAndLogging.AppConfig;
import ConfigurationAndLogging.AppLogger;
import ConfigurationAndLogging.FaceCropSettingsPanel;
import ConfigurationAndLogging.IConfigChangeListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class FaceCaptureDialog extends JDialog {
    private Student student;
    private StudentManager studentManager;
    private FaceDetection faceDetection;

    private JLabel videoLabel;
    private JLabel statusLabel;
    private JLabel instructionLabel;
    private JProgressBar progressBar;
    private JLabel progressLabel;
    private JLabel qualityLabel;
    private JButton startButton;
    private JButton stopButton;
    private JButton closeButton;

    private JButton cameraSettingsButton;
    private JComboBox<String> fpsCombo;

    private AtomicBoolean isCapturing = new AtomicBoolean(false);
    private AtomicInteger capturedCount = new AtomicInteger(0);
    private Timer previewTimer;
    private int targetImages;
    private boolean captureCompleted = false;

    // Performance optimization: Only run face detection every N frames
    private int frameCounter = 0;
    private static final int DETECTION_INTERVAL = 5; // Detect every 5 frames instead of every frame
    private FaceDetection.FaceDetectionResult lastDetectionResult = null;

    //  Multi-threading: Shared frame between capture and preview
    private volatile Mat sharedFrame = null;
    private final Object frameLock = new Object();

    private static final Color SUCCESS_COLOR = new Color(46, 125, 50);
    private static final Color WARNING_COLOR = new Color(255, 152, 0);
    private static final Color ERROR_COLOR = new Color(211, 47, 47);

    public FaceCaptureDialog(Frame parent, Student student, StudentManager studentManager) {
        super(parent, "Face Capture - " + student.getName(), true);
        this.student = student;
        this.studentManager = studentManager;
        this.faceDetection = new FaceDetection();

        AppLogger.info("FaceCaptureDialog created for student: " + student.getName());

        initializeDialog();
        startCameraPreview();
    }

    private void initializeDialog() {
        setSize(800, 700);
        setLocationRelativeTo(getParent());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        add(createInfoPanel(), BorderLayout.NORTH);
        add(createVideoPanel(), BorderLayout.CENTER);
        add(createControlPanel(), BorderLayout.SOUTH);
        add(createSettingsPanel(), BorderLayout.EAST);

        AppLogger.info("Dialog initialized");
    }

    private JPanel createInfoPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Student Information"));

        JLabel studentInfo = new JLabel(String.format(
                "<html><b>Student ID:</b> %s &nbsp;&nbsp; <b>Name:</b> %s</html>",
                student.getStudentId(), student.getName()));
        studentInfo.setFont(studentInfo.getFont().deriveFont(14f));

        instructionLabel = new JLabel(
                "<html><center>Position your face in the camera view and click 'Start Capture'.<br/>" +
                        "Look directly at the camera and maintain good lighting.<br/>" +
                        "<font color='blue'><b>DEBUG MODE: Rectangles will show detected faces with confidence scores</b></font></center></html>");
        instructionLabel.setHorizontalAlignment(JLabel.CENTER);
        instructionLabel.setFont(instructionLabel.getFont().deriveFont(12f));

        panel.add(studentInfo, BorderLayout.NORTH);
        panel.add(instructionLabel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createSettingsPanel() {
        // Integrate configuration and logging settings panel as a sidebar
        IConfigChangeListener listener = new IConfigChangeListener() {
            @Override
            public void onScaleFactorChanged(double newScaleFactor) {
                AppConfig.getInstance().setDetectionScaleFactor(newScaleFactor);
                AppLogger.info("Detection scale factor changed to " + newScaleFactor);
                statusLabel.setText("Scale Factor: " + String.format("%.2f", newScaleFactor));
                statusLabel.setForeground(SUCCESS_COLOR);
            }

            @Override
            public void onMinNeighborsChanged(int newMinNeighbors) {
                AppConfig.getInstance().setDetectionMinNeighbors(newMinNeighbors);
                AppLogger.info("Min neighbors changed to " + newMinNeighbors);
                statusLabel.setText("Min Neighbors: " + newMinNeighbors);
                statusLabel.setForeground(SUCCESS_COLOR);
            }

            @Override
            public void onMinSizeChanged(int newMinSize) {
                AppConfig.getInstance().setDetectionMinSize(newMinSize);
                AppLogger.info("Min size px changed to " + newMinSize);
                statusLabel.setText("Min Size: " + newMinSize + " px");
                statusLabel.setForeground(SUCCESS_COLOR);
            }

            @Override
            public void onCaptureFaceRequested() {
                // The settings panel can request a capture; reuse startCapture
                if (!isCapturing.get()) {
                    startCapture();
                }
            }

            @Override
            public void onSaveSettingsRequested() {
                AppConfig.getInstance().save();
                AppLogger.info("Detection settings saved to app.properties");
            }
        };

        // We already have capture controls in this dialog; hide capture button in
        // settings panel
        FaceCropSettingsPanel settings = new FaceCropSettingsPanel(listener, false);
        return settings;
    }

    private JPanel createVideoPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Live Camera Feed with Face Detection"));

        videoLabel = new JLabel();
        videoLabel.setPreferredSize(new Dimension(640, 480)); // Standard webcam resolution
        videoLabel.setBackground(Color.BLACK);
        videoLabel.setOpaque(true);
        videoLabel.setHorizontalAlignment(JLabel.CENTER);
        videoLabel.setText(
                "<html><center><font color='white'>Initializing camera...<br/>Please wait...</font></center></html>");
        videoLabel.setBorder(BorderFactory.createLoweredBevelBorder());

        JPanel statusPanel = new JPanel(new GridLayout(4, 1, 5, 5));
        statusPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        statusLabel = new JLabel("Initializing...", JLabel.CENTER);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 14f));
        statusLabel.setForeground(WARNING_COLOR);

        qualityLabel = new JLabel("Face quality: Initializing...", JLabel.CENTER);
        qualityLabel.setFont(qualityLabel.getFont().deriveFont(12f));

        progressLabel = new JLabel("Images captured: 0", JLabel.CENTER);
        progressLabel.setFont(progressLabel.getFont().deriveFont(12f));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("0%");

        statusPanel.add(statusLabel);
        statusPanel.add(qualityLabel);
        statusPanel.add(progressLabel);
        statusPanel.add(progressBar);

        panel.add(videoLabel, BorderLayout.CENTER);
        panel.add(statusPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout());

        JLabel targetLabel = new JLabel("Target images:");
        // EXPERIMENTAL: Added 25 option and changed default to 20 for more training
        // data
        // Revert to original if too time-consuming: remove "25", default to "15"
        String[] options = { "10", "15", "20", "25" };
        JComboBox<String> targetCombo = new JComboBox<>(options);
        targetCombo.setSelectedItem("20"); // Was "15" - more training data recommended

        startButton = new JButton("Start Capture");
        stopButton = new JButton("Stop Capture");
        closeButton = new JButton("Close");
        cameraSettingsButton = new JButton("Camera Settingsâ€¦");
        fpsCombo = new JComboBox<>(new String[] { "15 fps", "24 fps", "30 fps" });
        fpsCombo.setSelectedItem("30 fps");

        stopButton.setEnabled(false);

        startButton.addActionListener(e -> {
            targetImages = Integer.parseInt((String) targetCombo.getSelectedItem());
            startCapture();
        });

        stopButton.addActionListener(e -> stopCapture());
        closeButton.addActionListener(e -> dispose());

        cameraSettingsButton.addActionListener(e -> {
            // Camera settings dialog not implemented in FaceDetection class
            statusLabel.setText("Camera settings feature not available");
            statusLabel.setForeground(WARNING_COLOR);
            JOptionPane.showMessageDialog(this,
                    "Camera settings dialog is not currently implemented.\nAdjust camera settings in your system camera app.",
                    "Feature Not Available", JOptionPane.INFORMATION_MESSAGE);
        });

        fpsCombo.addActionListener(e -> {
            // FPS setting not implemented in FaceDetection class
            String sel = (String) fpsCombo.getSelectedItem();
            statusLabel.setText("FPS adjustment not available: " + sel);
            statusLabel.setForeground(WARNING_COLOR);
        });

        panel.add(targetLabel);
        panel.add(targetCombo);
        panel.add(Box.createHorizontalStrut(20));
        panel.add(new JLabel("FPS:"));
        panel.add(fpsCombo);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(cameraSettingsButton);
        panel.add(startButton);
        panel.add(stopButton);
        panel.add(closeButton);

        return panel;
    }

    private void startCameraPreview() {
        AppLogger.info("Starting camera preview...");

        if (!faceDetection.isCameraAvailable()) {
            showError("Camera not available. Please check camera connection.");
            return;
        }

        statusLabel.setText("Camera ready");
        statusLabel.setForeground(SUCCESS_COLOR);

        previewTimer = new Timer(50, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updatePreview();
            }
        });
        previewTimer.start();

        AppLogger.info("Preview timer started at 20fps");
    }

    private void updatePreview() {
        try {
            Mat frame = null;

            //  CRITICAL FIX: ALWAYS read from camera, never use shared frames
            // The capture thread operates independently - preview must be continuous
            frame = faceDetection.getCurrentFrame();

            if (frame != null && !frame.empty()) {
                //  PERFORMANCE: Only run face detection every N frames (not every frame!)
                // DNN face detection is expensive - running at 30fps causes freezing
                frameCounter++;
                if (frameCounter >= DETECTION_INTERVAL) {
                    frameCounter = 0;
                    lastDetectionResult = faceDetection.detectFaceForPreview(frame);
                }

                // Use last detection result (or null if first frame)
                Mat displayFrame;
                if (lastDetectionResult != null) {
                    displayFrame = faceDetection.drawFaceOverlay(frame, lastDetectionResult);
                } else {
                    displayFrame = frame.clone(); // First few frames - no overlay yet
                }

                // Convert and display
                BufferedImage bufferedImage = matToBufferedImage(displayFrame);
                if (bufferedImage != null) {
                    int displayWidth = videoLabel.getWidth();
                    int displayHeight = videoLabel.getHeight();

                    if (displayWidth > 0 && displayHeight > 0) {
                        Image scaledImage = bufferedImage.getScaledInstance(
                                displayWidth, displayHeight, Image.SCALE_FAST);
                        ImageIcon icon = new ImageIcon(scaledImage);
                        videoLabel.setIcon(icon);
                        videoLabel.setText("");
                    }
                }

                // Update quality feedback (only when not capturing)
                if (!isCapturing.get() && lastDetectionResult != null) {
                    updateQualityFeedback(lastDetectionResult);
                }

                // Release frames
                displayFrame.release();
                frame.release();

            } else {
                // Only log when not capturing to reduce spam
                if (!isCapturing.get()) {
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
                qualityLabel.setText(
                        "Face quality: Poor (" + String.format("%.1f%%", confidence * 100) + ") - Improve lighting");
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

    private void updateDebugInfo(FaceDetection.FaceDetectionResult result) {
        if (result != null) {
            int faceCount = result.getFaces().size();
            if (faceCount > 0) {
                double bestConf = result.getBestConfidence();
                AppLogger.info(String.format("Debug: %d face(s) detected, best confidence: %.2f", faceCount, bestConf));
            } else {
                AppLogger.info("Debug: No faces detected this frame");
            }
        } else {
            AppLogger.warn("Debug: Detection result is null");
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

    private void startCapture() {
        if (isCapturing.get())
            return;

        AppLogger.info("Starting capture process...");

        isCapturing.set(true);
        capturedCount.set(0);

        //  Multi-threading: Keep preview running, it will use shared frames from
        // capture

        startButton.setEnabled(false);
        stopButton.setEnabled(true);

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
                } finally {
                    //  Clean up shared frame
                    synchronized (frameLock) {
                        if (sharedFrame != null) {
                            sharedFrame.release();
                            sharedFrame = null;
                        }
                    }
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
            public void onFrameUpdate(Mat frame) {
                //  NO-OP: Preview reads directly from camera, doesn't need shared frames
                // This callback is kept for API compatibility but does nothing
            }

            @Override
            public void onImageCaptured(int current, int total, double confidence) {
                SwingUtilities.invokeLater(() -> {
                    capturedCount.set(current);
                    int percentage = (int) ((current * 100.0) / total);
                    progressBar.setValue(percentage);
                    progressBar.setString(current + "/" + total + " (" + percentage + "%)");
                    progressLabel.setText(String.format("Images captured: %d/%d (Quality: %.1f%%)",
                            current, total, confidence * 100));
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

        FaceDetection.FaceCaptureResult result = faceDetection.captureAndStoreFaceImages(
                student, targetImages, callback);

        AppLogger.info("Capture result: " + result.isSuccess() + " - " + result.getMessage());

        // Post-capture summary to the user
        java.util.List<String> capturedImages = result.getCapturedImages();
        if (capturedImages != null && !capturedImages.isEmpty()) {
            StringBuilder msg = new StringBuilder();
            msg.append(String.format("Capture completed successfully!\nCaptured %d images for %s.",
                    capturedImages.size(), student.getName()));

            //  OUTLIER REMOVAL FEEDBACK: Show if any outliers were removed
            FaceEmbeddingGenerator.BatchProcessingResult batchResult = result.getBatchProcessingResult();
            if (batchResult != null) {
                int removedCount = batchResult.getRemovedOutlierCount();
                if (removedCount > 0) {
                    msg.append(
                            String.format("\n\n Auto-removed %d low-quality image(s) for better recognition accuracy.",
                                    removedCount));
                    msg.append(String.format("\nFinal count: %d high-quality images retained.",
                            batchResult.getProcessedCount() - removedCount));
                } else {
                    msg.append("\n\n All images passed quality check - no outliers detected.");
                }
            }

            JOptionPane.showMessageDialog(this, htmlize(msg.toString()),
                    "Capture Summary", JOptionPane.INFORMATION_MESSAGE);
        } else {
            String msg = "Capture completed but no images were captured.\n" + result.getMessage();
            JOptionPane.showMessageDialog(this, htmlize(msg),
                    "Capture Summary", JOptionPane.WARNING_MESSAGE);
        }

        return result.isSuccess();
    }

    private void stopCapture() {
        AppLogger.info("Stopping capture...");
        isCapturing.set(false);

        startButton.setEnabled(true);
        stopButton.setEnabled(false);

        statusLabel.setText("Capture stopped");
        statusLabel.setForeground(WARNING_COLOR);
        AppLogger.info("Debug: Capture manually stopped");

        instructionLabel.setText("<html><center>Capture stopped by user.<br/>" +
                "You can start again when ready.</center></html>");

        //  Clean up shared frame
        synchronized (frameLock) {
            if (sharedFrame != null) {
                sharedFrame.release();
                sharedFrame = null;
            }
        }
    }

    private void onCaptureCompleted(boolean success) {
        isCapturing.set(false);
        captureCompleted = success;

        startButton.setEnabled(true);
        stopButton.setEnabled(false);

        if (success) {
            statusLabel.setText("Capture successful!");
            statusLabel.setForeground(SUCCESS_COLOR);
            instructionLabel.setText("<html><center><b>SUCCESS!</b><br/>" +
                    "Face images captured successfully.</center></html>");

            progressBar.setValue(100);
            progressBar.setString("100% Complete");
            AppLogger.info("Debug: All images captured successfully");

            // Success message is shown in the summary dialog above
        } else {
            statusLabel.setText("Capture failed");
            statusLabel.setForeground(ERROR_COLOR);
            instructionLabel.setText("<html><center><font color='red'>Capture failed.</font><br/>" +
                    "Please try again with better lighting.</center></html>");
            AppLogger.warn("Debug: Capture failed - insufficient valid images");
            // Failure details are shown in the summary dialog above
        }
    }

    private void showError(String message) {
        statusLabel.setText(htmlize("Error: " + message));
        statusLabel.setForeground(ERROR_COLOR);
        AppLogger.error("Debug: Error - " + message);
        JOptionPane.showMessageDialog(this, htmlize(message), "Error", JOptionPane.ERROR_MESSAGE);
    }

    private String htmlize(String text) {
        if (text == null)
            return "";
        String trimmed = text.trim();
        if (trimmed.toLowerCase().startsWith("<html>")) {
            return text; // already HTML
        }
        String escaped = trimmed
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                // Replace both actual newlines and literal backslash-n sequences
                .replace("\n", "<br/>")
                .replace("\\n", "<br/>");
        return "<html>" + escaped + "</html>";
    }

    @Override
    public void dispose() {
        AppLogger.info("Disposing FaceCaptureDialog...");

        if (previewTimer != null) {
            previewTimer.stop();
            AppLogger.info("Preview timer stopped");
        }
        isCapturing.set(false);

        if (faceDetection != null) {
            faceDetection.release();
            AppLogger.info("Face detection resources released");
        }

        super.dispose();
    }

    public boolean isCaptureCompleted() {
        return captureCompleted;
    }
}