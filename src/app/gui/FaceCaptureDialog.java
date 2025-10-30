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
import app.gui.*;
import app.service.*;
import ConfigurationAndLogging.*;
public class FaceCaptureDialog extends JDialog{
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
    private int targetImages;
    private boolean captureCompleted = false;

    private CameraPreviewManager cameraPreviewManager;
    private CaptureManager captureManager;

    private static final Color SUCCESS_COLOR = new Color(46, 125, 50);
    private static final Color WARNING_COLOR = new Color(255, 152, 0);
    private static final Color ERROR_COLOR = new Color(211, 47, 47);

    public FaceCaptureDialog(Frame parent, Student student, StudentManager studentManager) {
        super(parent, "Face Capture - " + student.getName(), true);
        this.student = student;
        this.studentManager = studentManager;
        this.faceDetection = new FaceDetection();

        AppLogger.info("FaceCaptureDialog created for student: " + student.getName());

        cameraPreviewManager = new CameraPreviewManager(faceDetection, null, null, null);
        captureManager = new CaptureManager(faceDetection, student, studentManager, isCapturing, capturedCount, targetImages, captureCompleted, null, null, null, null, this);

        initializeDialog();
        cameraPreviewManager.startPreview();
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

        cameraPreviewManager.setLabels(videoLabel, statusLabel, qualityLabel);
        captureManager.setLabels(statusLabel, progressBar, progressLabel, instructionLabel);

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
                    targetImages = 10; // default
                    captureManager.setTargetImages(targetImages);
                    captureManager.startCapture();
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
        FaceCropSettingsPanel settings = new FaceCropSettingsPanel(listener, false, true);
        // ensure panel has enough width to show south controls and force save visible
        settings.setPreferredSize(new Dimension(340, 0)); // give the EAST column a reasonable width
        settings.setSaveButtonVisible(true); // defensive: force visible at runtime
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

        stopButton.setEnabled(false);

        startButton.addActionListener(e -> {
            targetImages = Integer.parseInt((String) targetCombo.getSelectedItem());
            captureManager.setTargetImages(targetImages);
            captureManager.startCapture();
        });

        stopButton.addActionListener(e -> captureManager.stopCapture());
        closeButton.addActionListener(e -> dispose());

        panel.add(targetLabel);
        panel.add(targetCombo);
        panel.add(Box.createHorizontalStrut(20));
        panel.add(startButton);
        panel.add(stopButton);
        panel.add(closeButton);

        return panel;
    }

    @Override
    public void dispose() {
        AppLogger.info("Disposing FaceCaptureDialog...");

        if (cameraPreviewManager != null) {
            cameraPreviewManager.stopPreview();
        }
        isCapturing.set(false);

        if (faceDetection != null) {
            faceDetection.release();
            AppLogger.info("Face detection resources released");
        }

        super.dispose();
    }

    public boolean isCaptureCompleted() {
        return captureManager.isCaptureCompleted();
    }


}