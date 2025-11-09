package app;

import config.AppConfig;
import config.AppLogger;
import gui.recognition.LiveRecognitionViewer;
import gui.FullScreenUtil;
import java.io.File;
import javax.swing.*;

/**
 * Entry point for the Face Recognition system.
 * Launches the Live Recognition Viewer.
 */
public class FaceRecognitionApp {

    static {
        // Load OpenCV native library
        System.load(new File(AppConfig.getInstance().getOpenCvLibPath()).getAbsolutePath());
    }

    public static void main(String[] args) {
        // Load App Configs
        AppLogger.info("Configuration and core components loaded.");
        AppLogger.info("Configuration file loaded");
        // Start up Live Recognition Viewer
        AppLogger.info("Live Recognition Viewer Starting!");
        SwingUtilities.invokeLater(() -> {
            try {
                LiveRecognitionViewer viewer = new LiveRecognitionViewer();
                // Make the recognition viewer fullscreen (windowed fullscreen) by default
                try {
                    FullScreenUtil.enableFullScreen(viewer, FullScreenUtil.Mode.MAXIMIZED);
                } catch (Throwable t) {
                    // If fullscreen fails for any reason, log and continue showing normally
                    AppLogger.warn("Unable to enable fullscreen for LiveRecognitionViewer: " + t.getMessage());
                }
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






