package app;

import java.io.File;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import config.AppConfig;
import config.AppLogger;
import gui.FullScreenUtil;
import gui.recognition.LiveRecognitionViewer;

/**
 * Entry point for the Face Recognition system.
 * Launches the Live Recognition Viewer.
 */
public class FaceRecognitionApp {

    static {
        // Load OpenCV native library using AppConfig
        try {
            String libPath = AppConfig.getInstance().getOpenCvLibPath();
            System.load(new File(libPath).getAbsolutePath());
            AppLogger.info("OpenCV library loaded from: " + libPath);
        } catch (Exception e) {
            AppLogger.error("Failed to load OpenCV library: " + e.getMessage(), e);
            throw new RuntimeException("Failed to load OpenCV native library", e);
        }
    }

    public static void main(String[] args) {
        // Load App Configs
        AppLogger.info("Configuration and core components loaded.");
        AppLogger.info("Configuration file loaded");
        // Start up Live Recognition Viewer
        AppLogger.info("Live Recognition Viewer Starting!");
        
        boolean launchedFromDashboard = "true".equals(System.getProperty("launched.from.dashboard"));
        
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
                
                // Show user-friendly error message
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.toLowerCase().contains("camera")) {
                    JOptionPane.showMessageDialog(null,
                        "Failed to initialize camera!\n\n" +
                        "Please check:\n" +
                        "• Camera is connected and not in use by another app\n" +
                        "• Camera permissions are granted\n" +
                        "• Camera drivers are installed",
                        "Camera Error",
                        JOptionPane.ERROR_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(null,
                        "Failed to start Live Recognition:\n" + errorMsg,
                        "Startup Error",
                        JOptionPane.ERROR_MESSAGE);
                }
                
                // Only exit if launched standalone, not from MainDashboard
                if (!launchedFromDashboard) {
                    System.exit(1);
                }
                // If launched from dashboard, just show error dialog and return
                // MainDashboard will detect no window was created and become visible again
            }
        });
    }
}






