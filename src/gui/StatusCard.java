package gui;
import util.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class StatusCard extends JPanel {
    private final JLabel lblValue;

    public StatusCard(String title, String initialValue) {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(220, 90));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColourTheme.LINE_BORDER, 1),
            BorderFactory.createEmptyBorder(12,12,12,12)
        ));
        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblTitle.setForeground(ColourTheme.FOREGROUND_COLOR);
        lblValue = new JLabel(initialValue);
        lblValue.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblValue.setForeground(new Color(107,114,128));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(lblTitle, BorderLayout.NORTH);
        top.add(lblValue, BorderLayout.SOUTH);
        add(top, BorderLayout.CENTER);
    }

    public void setValue(String value, Color color) {
        SwingUtilities.invokeLater(() -> {
            lblValue.setText(value);
            if (color != null) lblValue.setForeground(color);
        });
    }
}