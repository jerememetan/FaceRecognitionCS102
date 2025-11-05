package gui;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class StatusMonitor {
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener l){ pcs.addPropertyChangeListener(l); }
    public void removePropertyChangeListener(PropertyChangeListener l){ pcs.removePropertyChangeListener(l); }

    public void checkCamera(int index) {
        new Thread(() -> {
            boolean available = false;
            try {
                // requires OpenCV on classpath; if OpenCV not used, replace with other logic or always false
                org.opencv.videoio.VideoCapture cap = new org.opencv.videoio.VideoCapture(index);
                if (cap.isOpened()) {
                    available = true;
                    cap.release();
                }
            } catch (Throwable t) {
                available = false;
            }
            pcs.firePropertyChange("camera", null, available);
        }, "StatusCameraCheck").start();
    }

    public void checkDatabase(String jdbcUrl, String user, String password) {
        new Thread(() -> {
            boolean ok = false;
            try (java.sql.Connection conn = java.sql.DriverManager.getConnection(jdbcUrl, user, password)) {
                ok = conn != null && !conn.isClosed();
            } catch (Throwable t) {
                ok = false;
            }
            pcs.firePropertyChange("database", null, ok);
        }, "StatusDatabaseCheck").start();
    }
}