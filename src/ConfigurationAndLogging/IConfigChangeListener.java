package ConfigurationAndLogging;

public interface IConfigChangeListener {
    // These are the adjustable parameters in the AppConfig class
    // Each method corresponds to a parameter change event
    void onScaleFactorChanged(double newScaleFactor);
    void onMinNeighborsChanged(int newMinNeighbors);
    void onMinSizeChanged(int newMinSize);
    void onCaptureFaceRequested();
    // On save settings button clicked function
    void onSaveSettingsRequested();

}
