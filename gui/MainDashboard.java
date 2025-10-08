package gui;

import javax.swing.*;
import javax.swing.border.Border;

import java.awt.*;
import java.awt.event.*;

public class MainDashboard extends JFrame {
    private String role;
    private String id;
    private JPanel sidebar;
    private JPanel mainContent;
    private JButton menuButton; // burger button
    private boolean isSidebarVisible = true; // track if the sidebar is shown

    public MainDashboard(String role, String id) {
        this.role = role;
        this.id = id;

        setTitle("Dashboard - " + role + " (" + id + ")");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout()); // set the root layout
        setVisible(true);

        // burger menu button
        JPanel burger = new JPanel(new FlowLayout(FlowLayout.LEFT));
        menuButton = new JButton("☰"); // the symbol
        menuButton.setFont(new Font("Arial", Font.BOLD, 20)); // font, fontstyle, fontsize
        burger.add(menuButton);
        add(burger, BorderLayout.NORTH); // put it at the top

        // sidebar panel
        sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS)); // put it at the side along the yaxis
        sidebar.setBackground(new Color(50, 50, 50));
        sidebar.setPreferredSize(new Dimension(200, 600)); // set the deimension

        sidebar.add(new JButton("Live Recogniion"));
        sidebar.add(new JButton("Students"));
        sidebar.add(new JButton("Attendence Sessions"));
        sidebar.add(new JButton("Reports"));
        sidebar.add(new JButton("Settings"));

        //

        // Main content panel
        mainContent = new JPanel();
        JLabel welcomJLabel = new JLabel("Welcome!", SwingConstants.CENTER);
        welcomJLabel.setFont(new Font("Arial", Font.BOLD, 24)); // font, fontstyle, fontsize
        mainContent.add(welcomJLabel, BorderLayout.CENTER);

        // add both sidebar and maincontent to the frame
        add(sidebar, BorderLayout.WEST);
        add(mainContent, BorderLayout.CENTER);

        // burger menu button action
        menuButton.addActionListener(new ActionListener() {
            // make sidebar appear by sliding (animation)
            @Override
            public void actionPerformed(ActionEvent e){
                toggleSidebar(); // all the function
            }
        }); 

    }


    public void toggleSidebar(){
        int targetWidth = isSidebarVisible ? 0 : 200; // collapse or expand the sidebar
            int step = isSidebarVisible ? -20 : 20; // direction of the sidebar animation (how much to change the width)

            Timer timer = new Timer(15, null);
            timer.addActionListener(new ActionListener() {
                int width = sidebar.getWidth();

                @Override
                public void actionPerformed(ActionEvent e) {
                    width += step; // changes the siderbar's width gradually
                    
                    // stop the animation when target width reached
                    //check if reach the goal width 0 or 200
                    if((step<0 && width<=targetWidth) || (step>0 && width>=targetWidth)){
                        width = targetWidth;
                        ((Timer)e.getSource()).stop();  // if yes stop the timer
                        isSidebarVisible = !isSidebarVisible; // everytime click, if true-->false, if false-->true
                    }

                    sidebar.setPreferredSize(new Dimension(width, sidebar.getHeight()));
                    revalidate(); // tell Swing to recalculate the layout
                    repaint(); // refreshed the window
                }
            });
            timer.start();
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new MainDashboard("Admin", "123");
            }
        });
    }
}
