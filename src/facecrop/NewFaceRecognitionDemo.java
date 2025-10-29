package facecrop;

import app.gui.LiveRecognitionViewer;

/**
 * Legacy entry point retained for backward compatibility. All implementation
 * now lives in {@link app.gui.LiveRecognitionViewer} inside the app package.
 */
public final class NewFaceRecognitionDemo {

    private NewFaceRecognitionDemo() {
        // no-op: this class only preserves the historical entry point
    }

    public static void main(String[] args) {
        LiveRecognitionViewer.main(args);
    }
}