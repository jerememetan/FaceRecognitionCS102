package gui.SettingsView;

import javax.swing.*;
import java.awt.*;

public class WelcomeView extends JPanel {
    public WelcomeView() {
        setLayout(new GridBagLayout());
        setBackground(new Color(248, 250, 252));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("<html><div style='text-align:center;'><h2>Settings & Configuration</h2></div></html>", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(new Color(34, 37, 41));

        JLabel subtitle = new JLabel("<html><div style='text-align:center;'>Select a category from the left to edit related settings.</div></html>", SwingConstants.CENTER);
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(new Color(71, 85, 105));

        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setOpaque(false);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(title);
        col.add(Box.createRigidArea(new Dimension(0, 12)));
        col.add(subtitle);

        add(col);
    }
}