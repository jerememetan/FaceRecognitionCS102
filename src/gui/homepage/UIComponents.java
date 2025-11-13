package gui.homepage;
import util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public final class UIComponents {
	private UIComponents() {}

	// Apply consistent sidebar styling
	public static void applySidebarStyle(JPanel sidebar) {
		sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
		sidebar.setBackground(ColourTheme.HEADER_PANEL_BACKGROUND);
		sidebar.setPreferredSize(new Dimension(220, 600));
	}

	// Apply consistent main content background
	public static void applyMainContentStyle(JPanel mainContent) {
		mainContent.setLayout(new GridBagLayout());
		mainContent.setBackground(ColourTheme.BACKGROUND);
	}

	// Apply consistent text panel appearance used on the dashboard
	public static void applyTextPanelStyle(JPanel textPanel) {
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
		textPanel.setBackground(Color.WHITE);
		textPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColourTheme.LINE_BORDER, 1),
			BorderFactory.createEmptyBorder(30, 30, 30, 30)
		));
	}

	// Style the informational text area used inside dashboard text panels
	public static void styleInfoTextArea(JTextArea textArea) {
		textArea.setEditable(false);
		textArea.setOpaque(false);
		textArea.setFont(new Font("Segoe UI", Font.PLAIN, 16));
		textArea.setForeground(ColourTheme.FOREGROUND_COLOR);
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
		button.setBackground(ColourTheme.PRIMARY_COLOR); // Blue color
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
				button.setBackground(ColourTheme.PRIMARY_COLOR);
			}
		});
	}

	// Create an accent (action) button used in headers and dialogs
	public static JButton createAccentButton(String text, Color color) {
		JButton button = new JButton(text);
		// Try to pick an emoji-capable font if available on the system.
		String[] preferredEmojiFonts = {"Segoe UI Emoji", "Segoe UI Symbol", "Noto Color Emoji", "Apple Color Emoji", "EmojiOne Color", "Segoe UI"};
		String chosenFamily = null;
		String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
		for (String pf : preferredEmojiFonts) {
			for (String fam : families) {
				if (fam.equalsIgnoreCase(pf)) {
					chosenFamily = fam;
					break;
				}
			}
			if (chosenFamily != null) break;
		}
		if (chosenFamily == null) chosenFamily = "Segoe UI"; // safe fallback

		button.setFont(new Font(chosenFamily, Font.PLAIN, 14));
		button.setForeground(Color.WHITE);
		button.setBackground(color);
		button.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
		button.setFocusPainted(false);
		button.setCursor(new Cursor(Cursor.HAND_CURSOR));
		// Ensure background shows (some LAFs ignore background if not opaque)
		button.setOpaque(true);
		button.setContentAreaFilled(true);

		// Remove any codepoints that the chosen font can't display (avoid tofu/white boxes)
		Font f = button.getFont();
		int[] cps = text.codePoints().toArray();
		StringBuilder sb = new StringBuilder();
		for (int cp : cps) {
			if (f.canDisplay(cp)) sb.appendCodePoint(cp);
		}
		String safeText = sb.toString().trim();
		if (safeText.isEmpty()) {
			// If nothing is displayable, keep original text (so caller can set an icon instead)
			button.setText("");
		} else {
			button.setText(safeText);
		}

		return button;
	}

	// Create a styled status card (title + centered value) used in settings/welcome views
	public static JPanel createStatusCard(String title, String value, Color color) {
		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(Color.WHITE);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColourTheme.LINE_BORDER, 1),
			BorderFactory.createEmptyBorder(15, 15, 15, 15)
		));

		JLabel titleLabel = new JLabel(title);
		titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
		titleLabel.setForeground(ColourTheme.FOREGROUND_COLOR);
		titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

		JLabel valueLabel = new JLabel(value);
		valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
		valueLabel.setForeground(color);
		valueLabel.setHorizontalAlignment(SwingConstants.CENTER);

		card.add(titleLabel, BorderLayout.NORTH);
		card.add(valueLabel, BorderLayout.CENTER);

		return card;
	}

	// Create a reusable white content card with the standard border used across the UI
	public static JPanel createContentCard() {
		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(Color.WHITE);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColourTheme.LINE_BORDER, 1),
			BorderFactory.createEmptyBorder(8, 8, 8, 8)
		));
		return card;
	}
}










