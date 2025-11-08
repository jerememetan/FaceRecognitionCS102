package gui.recognition;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import org.opencv.core.Mat;
import org.opencv.highgui.HighGui;

// Assuming HighGui.toBufferedImage is available (common in modern OpenCV)
public class CameraPanel extends JPanel {

    // The image that will be drawn onto the panel
    private BufferedImage image; 
    
    // --- CONSTRUCTOR ---
    public CameraPanel() {
        // Set a default size for the video feed area (e.g., 640x480)
        setPreferredSize(new Dimension(640, 480));
        setBackground(Color.BLACK);
    }

    /**
     * Converts an OpenCV Mat frame into a BufferedImage and requests a repaint.
     * This method is called from the camera thread (startCameraLoop).
     * * @param mat The frame captured from the webcam.
     */
    public void displayMat(Mat mat) {
        if (mat.empty()) {
            return;
        }

        // Convert the Mat to a BufferedImage using OpenCV's utility.
        // NOTE: This utility often requires the Mat to be BGR, which your camera frame 'frame' is.
    image = (BufferedImage) HighGui.toBufferedImage(mat);

        // Schedule a repaint call on the Event Dispatch Thread (EDT) 
        // as all Swing drawing must happen on the EDT.
        if (image != null) {
            SwingUtilities.invokeLater(this::repaint);
        }
    }

    /**
     * Overrides the paintComponent method to draw the buffered image.
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        if (image != null) {
            // Calculate scale to fit the image into the panel while maintaining aspect ratio
            double scaleX = (double) getWidth() / image.getWidth();
            double scaleY = (double) getHeight() / image.getHeight();
            double scale = Math.min(scaleX, scaleY);
            
            int width = (int) (scale * image.getWidth());
            int height = (int) (scale * image.getHeight());
            int x = (getWidth() - width) / 2;
            int y = (getHeight() - height) / 2;

            // Draw the image
            g.drawImage(image, x, y, width, height, this);
        } else {
            // Draw placeholder text if no frame has been received yet
            g.setColor(Color.WHITE);
            g.drawString("Waiting for camera feed...", getWidth() / 2 - 70, getHeight() / 2);
        }
    }
}







