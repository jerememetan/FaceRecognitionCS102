package app.gui;

import app.entity.Student;
import app.service.StudentManager;
import app.service.FaceDetection;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

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

    private AtomicBoolean isCapturing = new AtomicBoolean(false);
    private AtomicInteger capturedCount = new AtomicInteger(0);
    private Timer previewTimer;
    private int targetImages;
    private boolean captureCompleted = false;

    private static final Color SUCCESS_COLOR = new Color(46, 125, 50);
    private static final Color WARNING_COLOR = new Color(255, 152, 0);
    private static final Color ERROR_COLOR = new Color(211, 47, 47);

    public FaceCaptureDialog(Frame parent, Student student, StudentManager studentManager) {
        super(parent, "Face Capture - " + student.getName(), true);
        this.student = student;
        this.studentManager = studentManager;
        this.faceDetection = new FaceDetection();

        System.out.println(" FaceCaptureDialog created for student: " + student.getName());

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

        System.out.println(" Dialog initialized");
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
        String[] options = { "10", "15", "20" };
        JComboBox<String> targetCombo = new JComboBox<>(options);
        targetCombo.setSelectedItem("15");

        startButton = new JButton("Start Capture");
        stopButton = new JButton("Stop Capture");
        closeButton = new JButton("Close");

        stopButton.setEnabled(false);

        startButton.addActionListener(e -> {
            targetImages = Integer.parseInt((String) targetCombo.getSelectedItem());
            startCapture();
        });

        stopButton.addActionListener(e -> stopCapture());
        closeButton.addActionListener(e -> dispose());

        panel.add(targetLabel);
        panel.add(targetCombo);
        panel.add(Box.createHorizontalStrut(20));
        panel.add(startButton);
        panel.add(stopButton);
        panel.add(closeButton);

        return panel;
    }

    private void startCameraPreview() {
        System.out.println(" Starting camera preview...");

        if (!faceDetection.isCameraAvailable()) {
            showError("Camera not available. Please check camera connection.");
            return;
        }

        statusLabel.setText("Camera ready");
        statusLabel.setForeground(SUCCESS_COLOR);

        previewTimer = new Timer(100, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updatePreview();
            }
        });
        previewTimer.start();

        System.out.println(" Preview timer started");
    }

    private void updatePreview() {
        try {
    
            Mat frame = faceDetection.getCurrentFrame();

            if (!frame.empty()) {
                System.out.println(" Frame received: " + frame.width() + "x" + frame.height());

         
                FaceDetection.FaceDetectionResult result = faceDetection.detectFaceForPreview(frame);

                
                Mat displayFrame = faceDetection.drawFaceOverlay(frame, result);

        
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

                if (!isCapturing.get()) {
                    updateQualityFeedback(result);
                }
                updateDebugInfo(result);

            } else {
                System.out.println("Debug: Empty frame received");
            }
        } catch (Exception e) {
            System.err.println(" Preview update failed: " + e.getMessage());
            e.printStackTrace();
            System.out.println("Debug: Preview error - " + e.getMessage());
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
                System.out.println(String.format("Debug: %d face(s) detected, best confidence: %.2f", faceCount, bestConf));
            } else {
                System.out.println("Debug: No faces detected this frame");
            }
        } else {
            System.out.println("Debug: Detection result is null");
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

        System.out.println(" Starting capture process...");

        isCapturing.set(true);
        capturedCount.set(0);

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
                    System.err.println(" Capture worker failed: " + e.getMessage());
                    showError("Capture failed: " + e.getMessage());
                    onCaptureCompleted(false);
                }
            }
        };
        captureWorker.execute();
    }

    private boolean performCapture() {
        System.out.println(" Performing capture with target: " + targetImages);

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
                    System.out.println(String.format("Debug: Image saved with confidence %.2f", confidence));
                });
            }

            @Override
            public void onWarning(String message) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText(message);
                    statusLabel.setForeground(WARNING_COLOR);
                    System.out.println("Debug: Warning - " + message);
                });
            }

            @Override
            public void onError(String message) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Error: " + message);
                    statusLabel.setForeground(ERROR_COLOR);
                    System.out.println("Debug: Error - " + message);
                });
            }

            @Override
            public void onCaptureCompleted() {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Capture process completed");
                    statusLabel.setForeground(SUCCESS_COLOR);
                    System.out.println("Debug: Capture process finished");
                });
            }
        };

        FaceDetection.FaceCaptureResult result = faceDetection.captureAndStoreFaceImages(
                student, targetImages, callback);

        System.out.println(" Capture result: " + result.isSuccess() + " - " + result.getMessage());
        return result.isSuccess();
    }

    private void stopCapture() {
        System.out.println(" Stopping capture...");
        isCapturing.set(false);

        startButton.setEnabled(true);
        stopButton.setEnabled(false);

        statusLabel.setText("Capture stopped");
        statusLabel.setForeground(WARNING_COLOR);
    System.out.println("Debug: Capture manually stopped");

        instructionLabel.setText("<html><center>Capture stopped by user.<br/>" +
                "You can start again when ready.</center></html>");
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
            System.out.println("Debug: All images captured successfully");

            JOptionPane.showMessageDialog(this,
                    String.format("Successfully captured %d face images for %s!\n\n" +
                            "Images are stored and ready for face recognition.",
                            capturedCount.get(), student.getName()),
                    "Capture Successful",
                    JOptionPane.INFORMATION_MESSAGE);
        } else {
            statusLabel.setText("Capture failed");
            statusLabel.setForeground(ERROR_COLOR);
        instructionLabel.setText("<html><center><font color='red'>Capture failed.</font><br/>" +
            "Please try again with better lighting.</center></html>");
        System.out.println("Debug: Capture failed - insufficient valid images");

            JOptionPane.showMessageDialog(this,
                    String.format("Face capture failed.\n\n" +
                            "Only captured %d valid images (need at least 10).\n" +
                            "Please ensure good lighting and try again.",
                            capturedCount.get()),
                    "Capture Failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showError(String message) {
        statusLabel.setText("Error: " + message);
        statusLabel.setForeground(ERROR_COLOR);
        System.out.println("Debug: Error - " + message);
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void dispose() {
        System.out.println(" Disposing FaceCaptureDialog...");

        if (previewTimer != null) {
            previewTimer.stop();
            System.out.println(" Preview timer stopped");
        }
        isCapturing.set(false);

        if (faceDetection != null) {
            faceDetection.release();
            System.out.println(" Face detection resources released");
        }

        super.dispose();
    }

    public boolean isCaptureCompleted() {
        return captureCompleted;
    }
}