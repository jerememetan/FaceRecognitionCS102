package ConfigurationAndLogging;
import java.awt.*;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
public class FaceCropSettingsPanel extends JPanel {

    private final IConfigChangeListener listener;
    private final JLabel footerLabel;

    private final Color PRIMARY = new Color(59, 130, 246);
    private final Color PRIMARY_HOVER = new Color(37, 99, 235);
    private final Color DANGER = new Color(239, 68, 68);
    private final Color DANGER_HOVER = new Color(220, 38, 38);

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

        // Build controls (now includes the save / capture buttons inside the grid so they won't be clipped)
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
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        int row = 0;

        JLabel scaleLabel = new JLabel("Scale Factor: " + String.format("%.2f", AppConfig.getInstance().getDetectionScaleFactor()), SwingConstants.CENTER);
        gbc.gridy = row++;
        controls.add(scaleLabel, gbc);

        JSlider scaleSlider = new JSlider(JSlider.HORIZONTAL, 101, 150, (int) (AppConfig.getInstance().getDetectionScaleFactor() * 100));
        scaleSlider.addChangeListener((ChangeEvent e) -> {
            double v = scaleSlider.getValue() / 100.0;
            scaleLabel.setText("Scale Factor: " + String.format("%.2f", v));
            if (listener != null) listener.onScaleFactorChanged(v);
            else AppConfig.getInstance().setDetectionScaleFactor(v);
        });
        gbc.gridy = row++;
        controls.add(wrapFullWidth(scaleSlider), gbc);

        JLabel scaleHint = new JLabel("(Lower = more accurate, slower)", SwingConstants.CENTER);
        scaleHint.setFont(scaleHint.getFont().deriveFont(11f));
        gbc.gridy = row++;
        controls.add(scaleHint, gbc);

        JLabel neighLabel = new JLabel("Min Neighbors: " + AppConfig.getInstance().getDetectionMinNeighbors(), SwingConstants.CENTER);
        gbc.gridy = row++;
        controls.add(neighLabel, gbc);

        JSlider neighSlider = new JSlider(JSlider.HORIZONTAL, 3, 10, AppConfig.getInstance().getDetectionMinNeighbors());
        neighSlider.addChangeListener(e -> {
            int v = neighSlider.getValue();
            neighLabel.setText("Min Neighbors: " + v);
            if (listener != null) listener.onMinNeighborsChanged(v);
            else AppConfig.getInstance().setDetectionMinNeighbors(v);
        });
        neighSlider.setPaintTicks(true);
        neighSlider.setMajorTickSpacing(1);
        gbc.gridy = row++;
        controls.add(wrapFullWidth(neighSlider), gbc);

        JLabel neighHint = new JLabel("(Higher = fewer false positives)", SwingConstants.CENTER);
        neighHint.setFont(neighHint.getFont().deriveFont(11f));
        gbc.gridy = row++;
        controls.add(neighHint, gbc);

        JLabel sizeLabel = new JLabel("Min Size (px): " + AppConfig.getInstance().getDetectionMinSize(), SwingConstants.CENTER);
        gbc.gridy = row++;
        controls.add(sizeLabel, gbc);

        JSlider sizeSlider = new JSlider(JSlider.HORIZONTAL, 20, 400, AppConfig.getInstance().getDetectionMinSize());
        sizeSlider.addChangeListener(e -> {
            int v = sizeSlider.getValue();
            sizeLabel.setText("Min Size (px): " + v);
            if (listener != null) listener.onMinSizeChanged(v);
            else AppConfig.getInstance().setDetectionMinSize(v);
        });
        sizeSlider.setPaintTicks(true);
        sizeSlider.setMajorTickSpacing(50);
        gbc.gridy = row++;
        controls.add(wrapFullWidth(sizeSlider), gbc);

        JLabel sizeHint = new JLabel("(Adjust to ignore distant faces)", SwingConstants.CENTER);
        sizeHint.setFont(sizeHint.getFont().deriveFont(11f));
        gbc.gridy = row++;
        controls.add(sizeHint, gbc);

        // Buttons row is now part of the controls grid (so it will always be visible)
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btnRow.setOpaque(false);

        if (Boolean.TRUE.equals(showCaptureButton)) {
            JButton capture = createStyledButton("Capture Face (Save to DB)", DANGER, DANGER_HOVER);
            capture.addActionListener(a -> {
                if (listener != null) listener.onCaptureFaceRequested();
                else JOptionPane.showMessageDialog(this, "No listener attached for capture.", "Capture", JOptionPane.INFORMATION_MESSAGE);
            });
            btnRow.add(capture);
        }

        // create the save button (always created; visibility controlled by flag)
        JButton saveButton = createStyledButton("Save Detection Settings", PRIMARY, PRIMARY_HOVER);
        saveButton.addActionListener(a -> {
            if (listener != null) {
                // let the caller handle confirmation/dialogs so we don't duplicate messages
                listener.onSaveSettingsRequested();
            } else {
                AppConfig.getInstance().save();
                JOptionPane.showMessageDialog(this, "Detection settings saved.", "Configuration Saved", JOptionPane.INFORMATION_MESSAGE);
            }
            // always show non-modal footer feedback
            showFooter("Settings saved");
         });

        // honor caller flag, expose the button to callers and add to row
        saveButton.setVisible(Boolean.TRUE.equals(showSaveButton));
        thisSaveButton = saveButton;
        btnRow.add(saveButton);

        // add buttons row into the controls grid so it is rendered
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

    private JButton createStyledButton(String text, Color base, Color hover) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btn.setForeground(Color.WHITE);
        btn.setBackground(base);
        btn.setOpaque(true);
        // Ensure LAF paints the background
        btn.setContentAreaFilled(true);
        // optional: keep border minimal — adjust as needed
        btn.setBorderPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { btn.setBackground(hover); }
            @Override public void mouseExited(java.awt.event.MouseEvent e)  { btn.setBackground(base);  }
        });
        
        return btn;
    }

    private void showFooter(String text) {
        footerLabel.setText(text);
        Timer t = new Timer(FOOTER_DURATION_MS, e -> footerLabel.setText(" "));
        t.setRepeats(false);
        t.start();
    }
}