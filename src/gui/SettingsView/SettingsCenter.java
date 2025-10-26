
package gui.SettingsView;
import gui.SettingsView.*;
import gui.SettingsView.SettingsCenter;
import javax.swing.*;
import java.awt.*;
import ConfigurationAndLogging.*;

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

        // register MYPAGE here so callers can just call showCard(MYPAGE)
        cards.add(new MyCustomView(), MYPAGE);
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