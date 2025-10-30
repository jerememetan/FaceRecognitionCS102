package app;

import app.gui.LiveRecognitionViewer;
import ConfigurationAndLogging.AppConfig;
import ConfigurationAndLogging.AppLogger;
import javax.swing.*;
import java.io.File;

/**
 * Entry point for the Face Recognition system.
 * Launches the Live Recognition Viewer.
 */
public class FaceRecognitionApp {

    static {
        // Load OpenCV native library
        System.load(new File("lib/opencv_java480.dll").getAbsolutePath());
    }

    public static void main(String[] args) {
        // Load App Configs
        AppLogger.info("Configuration and core components loaded.");
        AppConfig.getInstance();
        AppLogger.info("Configuration file loaded");

        // Start up Live Recognition Viewer
        AppLogger.info("Live Recognition Viewer Starting!");
        SwingUtilities.invokeLater(() -> {
            try {
                LiveRecognitionViewer viewer = new LiveRecognitionViewer();
                viewer.setVisible(true);
                AppLogger.info("Live Recognition Viewer launched successfully");
            } catch (Exception e) {
                AppLogger.error("Failed to launch Live Recognition Viewer: " + e.getMessage());
                e.printStackTrace();
                JOptionPane.showMessageDialog(null,
                    "Failed to start Live Recognition: " + e.getMessage(),
                    "Startup Error",
                    JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}