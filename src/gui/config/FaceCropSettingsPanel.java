package gui.config;

import config.AppConfig;
import config.AppLogger;
import config.IConfigChangeListener;
import java.awt.*;
import javax.swing.*;
import gui.homepage.UIComponents;
// import javax.swing.event.ChangeEvent; (not needed)

public class FaceCropSettingsPanel extends JPanel {

    private final IConfigChangeListener listener;
    private final JLabel footerLabel;

    private final Color PRIMARY = new Color(59, 130, 246);
    // hover variants no longer required; UIComponents handles hover internally
    private final Color DANGER = new Color(239, 68, 68);

    private static final int FOOTER_DURATION_MS = 3000;

    // Pass the listener into the constructor
    public FaceCropSettingsPanel(IConfigChangeListener listener, Boolean showCaptureButton) {
        this(listener, showCaptureButton, true);
    }

    // Primary constructor
    public FaceCropSettingsPanel(IConfigChangeListener listener, Boolean showCaptureButton, Boolean showSaveButton) {
        this.listener = listener;

        // Debug log to confirm runtime flag (helps detect stale-class problems)
        AppLogger.info("FaceCropSettingsPanel ctor - showCapture=" + showCaptureButton + " showSave=" + showSaveButton);

        // Add a Capture Button and a Label
        setLayout(new BorderLayout(8, 8));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JLabel title = new JLabel("Detection Tuning", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        add(title, BorderLayout.NORTH);

        // Build controls (now includes the save / capture buttons inside the grid so
        // they won't be clipped)
        JPanel controls = buildControls(showCaptureButton, showSaveButton);
        add(controls, BorderLayout.CENTER);

        // Footer remains in SOUTH (small text feedback)
        footerLabel = new JLabel(" ", SwingConstants.CENTER);
        footerLabel.setFont(footerLabel.getFont().deriveFont(12f));
        add(footerLabel, BorderLayout.SOUTH);
    }

    // field to reference the save button so callers can toggle visibility
    private JButton thisSaveButton;

    public void setSaveButtonVisible(boolean visible) {
        if (thisSaveButton != null) {
            thisSaveButton.setVisible(visible);
            // ensure layout recalculation so parent displays button
            revalidate();
            repaint();
        }
    }

    // --- Implementations for Controls ---

    private JPanel buildControls(Boolean showCaptureButton, Boolean showSaveButton) {
        JPanel controls = new JPanel(new GridBagLayout());
        controls.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.insets = new Insets(8, 8, 6, 8);
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        int row = 0;

        // --- Detection section ---
        JLabel detectionHeader = new JLabel("Detection (ArcFace DNN)", SwingConstants.CENTER);
        detectionHeader.setFont(new Font("Segoe UI", Font.BOLD, 14));
        gbc.gridy = row++;
        controls.add(detectionHeader, gbc);

        double initialConfidence = AppConfig.getInstance().getDnnConfidence();
        int confidenceSliderValue = (int) Math.round(initialConfidence * 100);
        confidenceSliderValue = Math.max(20, Math.min(90, confidenceSliderValue));

        JLabel confidenceLabel = new JLabel(
                "Confidence Threshold: " + String.format("%.2f", confidenceSliderValue / 100.0),
                SwingConstants.CENTER);
        gbc.gridy = row++;
        controls.add(confidenceLabel, gbc);

        JSlider confidenceSlider = new JSlider(JSlider.HORIZONTAL, 20, 90, confidenceSliderValue);
        confidenceSlider.addChangeListener(e -> {
            double value = confidenceSlider.getValue() / 100.0;
            confidenceLabel.setText("Confidence Threshold: " + String.format("%.2f", value));
            if (!confidenceSlider.getValueIsAdjusting()) {
                if (listener != null) {
                    listener.onDnnConfidenceChanged(value);
                } else {
                    AppConfig.getInstance().setDnnConfidence(value);
                }
            }
        });
        gbc.gridy = row++;
        controls.add(wrapFullWidth(confidenceSlider), gbc);

        JLabel confidenceHint = new JLabel("Higher = stricter detection, lower = more candidates",
                SwingConstants.CENTER);
        confidenceHint.setFont(confidenceHint.getFont().deriveFont(11f));
        gbc.gridy = row++;
        controls.add(confidenceHint, gbc);

    int initialMinSize = AppConfig.getInstance().getDetectionMinSize();
    initialMinSize = Math.max(24, Math.min(260, initialMinSize));

        JLabel minSizeLabel = new JLabel("Minimum Face Size: " + initialMinSize + " px", SwingConstants.CENTER);
        gbc.gridy = row++;
        controls.add(minSizeLabel, gbc);

    JSlider minSizeSlider = new JSlider(JSlider.HORIZONTAL, 24, 260, initialMinSize);
        minSizeSlider.setPaintTicks(true);
    minSizeSlider.setMajorTickSpacing(40);
        minSizeSlider.addChangeListener(e -> {
            int value = minSizeSlider.getValue();
            minSizeLabel.setText("Minimum Face Size: " + value + " px");
            if (!minSizeSlider.getValueIsAdjusting()) {
                if (listener != null) {
                    listener.onMinSizeChanged(value);
                } else {
                    AppConfig.getInstance().setDetectionMinSize(value);
                }
            }
        });
        gbc.gridy = row++;
        controls.add(wrapFullWidth(minSizeSlider), gbc);

        JLabel minSizeHint = new JLabel("Lower = detect smaller faces, Higher = ignore distant faces",
                SwingConstants.CENTER);
        minSizeHint.setFont(minSizeHint.getFont().deriveFont(11f));
        gbc.gridy = row++;
        controls.add(minSizeHint, gbc);

        // --- Recognition section ---
        JLabel recognitionHeader = new JLabel("Recognition Stability", SwingConstants.CENTER);
        recognitionHeader.setFont(new Font("Segoe UI", Font.BOLD, 14));
        gbc.gridy = row++;
        controls.add(recognitionHeader, gbc);

    int initialMinWidth = AppConfig.getInstance().getRecognitionMinFaceWidthPx();
    initialMinWidth = Math.max(48, Math.min(260, initialMinWidth));

        JLabel minWidthLabel = new JLabel("Min Face Width for Recognition: " + initialMinWidth + " px",
                SwingConstants.CENTER);
        gbc.gridy = row++;
        controls.add(minWidthLabel, gbc);

    JSlider minWidthSlider = new JSlider(JSlider.HORIZONTAL, 48, 260, initialMinWidth);
        minWidthSlider.setPaintTicks(true);
    minWidthSlider.setMajorTickSpacing(32);
        minWidthSlider.addChangeListener(e -> {
            int value = minWidthSlider.getValue();
            minWidthLabel.setText("Min Face Width for Recognition: " + value + " px");
            if (!minWidthSlider.getValueIsAdjusting()) {
                if (listener != null) {
                    listener.onRecognitionMinFaceWidthChanged(value);
                } else {
                    AppConfig.getInstance().setRecognitionMinFaceWidthPx(value);
                }
            }
        });
        gbc.gridy = row++;
        controls.add(wrapFullWidth(minWidthSlider), gbc);

        JLabel minWidthHint = new JLabel("Avoids scoring faces that are too small to be reliable",
                SwingConstants.CENTER);
        minWidthHint.setFont(minWidthHint.getFont().deriveFont(11f));
        gbc.gridy = row++;
        controls.add(minWidthHint, gbc);

        int initialWindow = Math.max(3, Math.min(20, AppConfig.getInstance().getConsistencyWindow()));
        JLabel windowLabel = new JLabel("Consistency Window: " + initialWindow + " frames", SwingConstants.CENTER);
        gbc.gridy = row++;
        controls.add(windowLabel, gbc);

        JSlider windowSlider = new JSlider(JSlider.HORIZONTAL, 3, 20, initialWindow);
        gbc.gridy = row++;
        controls.add(wrapFullWidth(windowSlider), gbc);

        JLabel windowHint = new JLabel("Frames considered when smoothing live predictions", SwingConstants.CENTER);
        windowHint.setFont(windowHint.getFont().deriveFont(11f));
        gbc.gridy = row++;
        controls.add(windowHint, gbc);

        int initialMinCount = Math.max(1,
                Math.min(initialWindow, AppConfig.getInstance().getConsistencyMinCount()));
        JLabel minCountLabel = new JLabel("Frames Needed for Confirmation: " + initialMinCount,
                SwingConstants.CENTER);
        gbc.gridy = row++;
        controls.add(minCountLabel, gbc);

        JSlider minCountSlider = new JSlider(JSlider.HORIZONTAL, 1, initialWindow, initialMinCount);
        gbc.gridy = row++;
        controls.add(wrapFullWidth(minCountSlider), gbc);

        JLabel minCountHint = new JLabel("Higher values reduce false accepts, but need more frames",
                SwingConstants.CENTER);
        minCountHint.setFont(minCountHint.getFont().deriveFont(11f));
        gbc.gridy = row++;
        controls.add(minCountHint, gbc);

        windowSlider.addChangeListener(e -> {
            int value = windowSlider.getValue();
            windowLabel.setText("Consistency Window: " + value + " frames");
            minCountSlider.setMaximum(value);
            if (minCountSlider.getValue() > value) {
                minCountSlider.setValue(value);
            }
            if (!windowSlider.getValueIsAdjusting()) {
                if (listener != null) {
                    listener.onConsistencyWindowChanged(value);
                } else {
                    AppConfig.getInstance().setConsistencyWindow(value);
                }
            }
        });

        minCountSlider.addChangeListener(e -> {
            int value = minCountSlider.getValue();
            minCountLabel.setText("Frames Needed for Confirmation: " + value);
            if (!minCountSlider.getValueIsAdjusting()) {
                if (listener != null) {
                    listener.onConsistencyMinCountChanged(value);
                } else {
                    AppConfig.getInstance().setConsistencyMinCount(value);
                }
            }
        });

        // --- Buttons ---
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btnRow.setOpaque(false);

        if (Boolean.TRUE.equals(showCaptureButton)) {
            JButton capture = UIComponents.createAccentButton("Capture Face (Save to DB)", DANGER);
            capture.addActionListener(a -> {
                if (listener != null) {
                    listener.onCaptureFaceRequested();
                } else {
                    JOptionPane.showMessageDialog(this, "No listener attached for capture.", "Capture",
                            JOptionPane.INFORMATION_MESSAGE);
                }
            });
            btnRow.add(capture);
        }

        JButton saveButton = UIComponents.createAccentButton("Save Detection Settings", PRIMARY);
        saveButton.addActionListener(a -> {
            if (listener != null) {
                listener.onSaveSettingsRequested();
            } else {
                AppConfig.getInstance().save();
                JOptionPane.showMessageDialog(this, "Detection settings saved.", "Configuration Saved",
                        JOptionPane.INFORMATION_MESSAGE);
            }
            showFooter("Settings saved");
        });

        saveButton.setVisible(Boolean.TRUE.equals(showSaveButton));
        thisSaveButton = saveButton;
        btnRow.add(saveButton);

        gbc.gridy = row++;
        controls.add(btnRow, gbc);

        return controls;
    }

    private JComponent wrapFullWidth(JSlider slider) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(slider, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, slider.getPreferredSize().height + 6));
        return p;
    }

    // createStyledButton removed: use UIComponents.createAccentButton(...) instead

    private void showFooter(String text) {
        footerLabel.setText(text);
        Timer t = new Timer(FOOTER_DURATION_MS, e -> footerLabel.setText(" "));
        t.setRepeats(false);
        t.start();
    }
}






