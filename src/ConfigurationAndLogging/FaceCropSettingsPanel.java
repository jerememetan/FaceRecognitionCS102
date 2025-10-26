package ConfigurationAndLogging;
import java.awt.*;
import javax.swing.*;
public class FaceCropSettingsPanel extends JPanel {

    private final IConfigChangeListener listener;
    
    // Pass the listener into the constructor
    public FaceCropSettingsPanel(IConfigChangeListener listener, Boolean showCaptureButton) {
        this.listener = listener;

        // Use BoxLayout to stack components vertically in a simple column
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); 
        setPreferredSize(new Dimension(320, 600)); 
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // Padding

        // Add a Capture Button and a Label
        if (showCaptureButton){
            addCaptureButton();
            add(Box.createVerticalStrut(25));  
        }
        add(new JLabel("<html><b><font size='+1'>Detection Tuning</font></b></html>"));
        add(Box.createVerticalStrut(15));
        // Add all controls
        addDetectionScaleFactorControl();
        add(Box.createVerticalStrut(15));
        addMinNeighborsControl();
        add(Box.createVerticalStrut(15));
        addMinSizeControl();
        add(Box.createVerticalGlue()); // Pushes everything up
        
        addSaveConfigButton(); 
    }
    
    // --- Implementations for Controls ---

    private void addDetectionScaleFactorControl() {
        // Range: 1.01 (High Accuracy) to 1.5 (High Speed)
        // We'll use a JSlider and convert the integer value back to a double
        int min = 101, max = 150, initial = (int)(AppConfig.getInstance().getDetectionScaleFactor() * 100);
        
        JLabel label = new JLabel("Scale Factor: " + (double)initial/100);
        JSlider slider = new JSlider(JSlider.HORIZONTAL, min, max, initial);
        slider.setMajorTickSpacing(10);
        slider.setMinorTickSpacing(1);
        slider.setPaintTicks(true);

        slider.addChangeListener(e -> {
            double newValue = (double)slider.getValue() / 100.0;
            label.setText("Scale Factor: " + String.format("%.2f", newValue));
            listener.onScaleFactorChanged(newValue);
        });

        add(label);
        add(slider);
        add(new JLabel("<html><font size='-2'>(Lower = more accurate, slower)</font></html>"));
    }

    private void addMinNeighborsControl() {
        // Range: 3 (Default) to 10 (Strict)
        int min = 3, max = 10, initial = AppConfig.getInstance().getDetectionMinNeighbors();

        JLabel label = new JLabel("Min Neighbors: " + initial);
        JSlider slider = new JSlider(JSlider.HORIZONTAL, min, max, initial);
        slider.setMajorTickSpacing(1);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);

        slider.addChangeListener(e -> {
            int newValue = slider.getValue();
            label.setText("Min Neighbors: " + newValue);
            listener.onMinNeighborsChanged(newValue);
        });

        add(label);
        add(slider);
        add(new JLabel("<html><font size='-2'>(Higher = fewer false positives)</font></html>"));
    }
    
    private void addMinSizeControl() {
        // Range: 20px (Further faces) to 200px (Closer faces)
        int min = 20, max = 200, initial = AppConfig.getInstance().getDetectionMinSize();

        JLabel label = new JLabel("Min Size (px): " + initial);
        JSlider slider = new JSlider(JSlider.HORIZONTAL, min, max, initial);
        slider.setMajorTickSpacing(50);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);

        slider.addChangeListener(e -> {
            int newValue = slider.getValue();
            label.setText("Min Size (px): " + newValue);
            listener.onMinSizeChanged(newValue);
        });

        add(label);
        add(slider);
        add(new JLabel("<html><font size='-2'>(Adjust to screen out distant faces)</font></html>"));
    }
    
    private void addSaveConfigButton() {
        JButton saveButton = new JButton("Save Detection Settings");
        saveButton.setAlignmentX(Component.CENTER_ALIGNMENT); 
        
        saveButton.addActionListener(e -> {
            listener.onSaveSettingsRequested();
            JOptionPane.showMessageDialog(this, "Detection settings saved to AppConfig file.", 
            "Configuration Saved", JOptionPane.INFORMATION_MESSAGE);
        });
        
        add(saveButton);
    }

        private void addCaptureButton() {
        JButton captureButton = new JButton("CAPTURE FACE (Save to DB)");
        captureButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        captureButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        
        captureButton.addActionListener(e -> {
            listener.onCaptureFaceRequested();
            // You might want to provide immediate feedback, like changing the button color briefly
        });
        
        add(captureButton);
    }
}