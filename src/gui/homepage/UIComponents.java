package gui.homepage;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public final class UIComponents {
	private UIComponents() {}

	// Apply consistent sidebar styling
	public static void applySidebarStyle(JPanel sidebar) {
		sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
		sidebar.setBackground(new Color(45, 55, 72));
		sidebar.setPreferredSize(new Dimension(220, 600));
	}

	// Apply consistent main content background
	public static void applyMainContentStyle(JPanel mainContent) {
		mainContent.setLayout(new GridBagLayout());
		mainContent.setBackground(new Color(248, 250, 252));
	}

	// Apply consistent text panel appearance used on the dashboard
	public static void applyTextPanelStyle(JPanel textPanel) {
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
		textPanel.setBackground(new Color(255, 255, 255));
		textPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
			BorderFactory.createEmptyBorder(30, 30, 30, 30)
		));
	}

	// Style the informational text area used inside dashboard text panels
	public static void styleInfoTextArea(JTextArea textArea) {
		textArea.setEditable(false);
		textArea.setOpaque(false);
		textArea.setFont(new Font("Segoe UI", Font.PLAIN, 16));
		textArea.setForeground(new Color(71, 85, 105));
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);
		textArea.setColumns(40);
		textArea.setAlignmentX(Component.CENTER_ALIGNMENT);
		textArea.setMargin(new Insets(10, 30, 10, 30));
	}

	// Centralized styling for sidebar buttons (hover effects, border, cursor, font)
	public static void styleSidebarButton(JButton button) {
		button.setFont(new Font("Segoe UI", Font.PLAIN, 14));
		button.setForeground(Color.WHITE);
		button.setBackground(new Color(59, 130, 246)); // Blue color
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(37, 99, 235), 1),
			BorderFactory.createEmptyBorder(8, 16, 8, 16)
		));
		button.setFocusPainted(false);
		button.setCursor(new Cursor(Cursor.HAND_CURSOR));

		// Hover effects
		button.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				button.setBackground(new Color(37, 99, 235));
			}

			@Override
			public void mouseExited(MouseEvent e) {
				button.setBackground(new Color(59, 130, 246));
			}
		});
	}
}










