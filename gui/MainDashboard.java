package gui;

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

        setTitle("Dashboard - " + role + " (" + id + ")");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        setVisible(true);

    }
}

