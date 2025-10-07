
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class MainDashboard  extends JFrame{
    private JPanel sidebar;
    private JPanel mainContent;
    private String role;
    private String id;

    public MainDashboard(String role, String id){
        this.role = role;
        this.id = id;
    }
}

