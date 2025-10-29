package gui.SettingsView;
import javax.swing.*;
import java.awt.*;

// THIS IS HOW YOU ADD A NEW VIEW -> ADD A NEW Page here
// Next, Wire the sidebar button to show it in Settings.GUI
public class MyCustomView extends JPanel{
        public MyCustomView() {
        setLayout(new BorderLayout());
        setBackground(new Color(248,250,252));
        add(new JLabel("<html><h3>Wow Look! This is my Custom Page</h3><p>Put controls here.</p></html>", SwingConstants.CENTER), BorderLayout.CENTER);

        // Example: call listener when something changes
        // if (listener != null) listener.onSaveSettingsRequested();
    }
}
