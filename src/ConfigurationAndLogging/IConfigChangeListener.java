package ConfigurationAndLogging;

public interface IConfigChangeListener {

    // --- Detection tuning ---
    default void onDnnConfidenceChanged(double newConfidence) {
    }

    default void onMinSizeChanged(int newMinSize) {
    }

    default void onScaleFactorChanged(double newScaleFactor) {
    }

    default void onMinNeighborsChanged(int newMinNeighbors) {
    }

    // --- Recognition tuning ---
    default void onRecognitionMinFaceWidthChanged(int newMinWidth) {
    }

    default void onConsistencyWindowChanged(int newWindowSize) {
    }

    default void onConsistencyMinCountChanged(int newMinCount) {
    }

    // --- Actions ---
    default void onCaptureFaceRequested() {
    }

    default void onSaveSettingsRequested() {
    }
}
