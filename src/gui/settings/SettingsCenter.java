package gui.settings;

import config.IConfigChangeListener;
import gui.config.FaceCropSettingsPanel;
import gui.homepage.SettingsGUI;
import java.awt.*;
import javax.swing.*;

/**
 * Small CardLayout wrapper for center content.
 * Add more cards here (CAMERA, OTHER, etc.) and call showCard(key).
 */
public class SettingsCenter extends JPanel {
    public static final String WELCOME = "WELCOME";
    public static final String DETECTION = "DETECTION";
    public static final String MYPAGE = "MYPAGE";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);


    public SettingsCenter(IConfigChangeListener listener) {
        setLayout(new BorderLayout());
        setBackground(new Color(248, 250, 252));

        // register cards
        cards.add(new WelcomeView(), WELCOME);

        // Detection uses the FaceCropSettingsPanel (no internal save; SettingsGUI keeps the centered save button)
        cards.add(new FaceCropSettingsPanel(listener, false, true), DETECTION);

        // add the cards at the end
        add(cards, BorderLayout.CENTER);
    }

    public void addCard(String key, JPanel panel) {
        cards.add(panel, key);
    }
    
    public void showCard(String key) {
        cardLayout.show(cards, key);
    }


}






