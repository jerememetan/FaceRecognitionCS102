package app.test;

import app.service.FaceDetection;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;


public class FaceDetectionTest extends JFrame {
    private FaceDetection faceDetection;
    private JLabel videoLabel;
    private JLabel statusLabel;
    private Timer previewTimer;
    private boolean isRunning = false;

    public FaceDetectionTest() {
        setTitle("Face Detection Debug Test");
        setSize(800, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        initComponents();
        initFaceDetection();
    }

    private void initComponents() {
    
        videoLabel = new JLabel();
        videoLabel.setPreferredSize(new Dimension(640, 480));
        videoLabel.setBackground(Color.BLACK);
        videoLabel.setOpaque(true);
        videoLabel.setHorizontalAlignment(JLabel.CENTER);
        videoLabel.setText("Initializing...");
        videoLabel.setForeground(Color.WHITE);
       
        statusLabel = new JLabel("Click Start Test to begin");
        statusLabel.setHorizontalAlignment(JLabel.CENTER);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 14f));

       
        JPanel controlPanel = new JPanel();
        JButton startButton = new JButton("Start Test");
        JButton stopButton = new JButton("Stop Test");

        startButton.addActionListener(e -> startTest());
        stopButton.addActionListener(e -> stopTest());

        controlPanel.add(startButton);
        controlPanel.add(stopButton);

        add(videoLabel, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.NORTH);
        add(controlPanel, BorderLayout.SOUTH);
    }

    private void initFaceDetection() {
        try {
            System.out.println("[TEST] Initializing FaceDetection...");
            faceDetection = new FaceDetection();

            if (faceDetection.isCameraAvailable()) {
                statusLabel.setText("Camera ready - Click Start Test");
                statusLabel.setForeground(Color.GREEN);
            } else {
                statusLabel.setText("Camera not available");
                statusLabel.setForeground(Color.RED);
            }
        } catch (Exception e) {
            System.err.println("[TEST] FaceDetection initialization failed: " + e.getMessage());
            e.printStackTrace();
            statusLabel.setText("FaceDetection initialization failed: " + e.getMessage());
            statusLabel.setForeground(Color.RED);
        }
    }

    private void startTest() {
        if (isRunning || faceDetection == null)
            return;

        System.out.println("[TEST] Starting face detection test...");
        isRunning = true;
        statusLabel.setText("Running face detection test...");
        statusLabel.setForeground(Color.BLUE);

        previewTimer = new Timer(100, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updatePreview();
            }
        });
        previewTimer.start();
    }

    private void stopTest() {
        System.out.println("[TEST] Stopping test...");
        isRunning = false;

        if (previewTimer != null) {
            previewTimer.stop();
        }

        statusLabel.setText("Test stopped");
        statusLabel.setForeground(Color.ORANGE);
    }

    private void updatePreview() {
        try {
            Mat frame = faceDetection.getCurrentFrame();

            if (!frame.empty()) {
          
                FaceDetection.FaceDetectionResult result = faceDetection.detectFaceForPreview(frame);

                Mat displayFrame = faceDetection.drawFaceOverlay(frame, result);

             
                String frameInfo = String.format("Frame: %dx%d", frame.cols(), frame.rows());
                Imgproc.putText(displayFrame, frameInfo, new org.opencv.core.Point(10, 30),
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(255, 255, 255), 2);

                String detectionInfo = String.format("Faces: %d", result.getFaces().size());
                Imgproc.putText(displayFrame, detectionInfo, new org.opencv.core.Point(10, 60),
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(255, 255, 255), 2);

                BufferedImage bufferedImage = matToBufferedImage(displayFrame);
                if (bufferedImage != null) {
                    ImageIcon icon = new ImageIcon(bufferedImage.getScaledInstance(
                            videoLabel.getWidth(), videoLabel.getHeight(), Image.SCALE_FAST));
                    videoLabel.setIcon(icon);
                    videoLabel.setText("");
                }

            
                if (result.hasValidFace()) {
                    statusLabel.setText(String.format("FACE DETECTED! Count: %d, Best confidence: %.2f",
                            result.getFaces().size(), result.getBestConfidence()));
                    statusLabel.setForeground(Color.GREEN);
                } else {
                    statusLabel.setText("No faces detected - move closer to camera");
                    statusLabel.setForeground(Color.RED);
                }
            } else {
                statusLabel.setText("Empty frame from camera");
                statusLabel.setForeground(Color.RED);
            }
        } catch (Exception e) {
            System.err.println("[TEST] Preview update failed: " + e.getMessage());
            e.printStackTrace();
            statusLabel.setText("Preview error: " + e.getMessage());
            statusLabel.setForeground(Color.RED);
        }
    }

    private BufferedImage matToBufferedImage(Mat mat) {
        try {
            if (mat.empty())
                return null;

            int type = mat.channels() > 1 ? BufferedImage.TYPE_3BYTE_BGR : BufferedImage.TYPE_BYTE_GRAY;

            byte[] buffer = new byte[(int) (mat.total() * mat.elemSize())];
            mat.get(0, 0, buffer);

            BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
            final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            System.arraycopy(buffer, 0, targetPixels, 0, buffer.length);

            return image;
        } catch (Exception e) {
            System.err.println("[TEST] Mat conversion failed: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void dispose() {
        stopTest();
        if (faceDetection != null) {
            faceDetection.release();
        }
        super.dispose();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeel());
            } catch (Exception e) {
                e.printStackTrace();
            }

            new FaceDetectionTest().setVisible(true);
        });
    }
}