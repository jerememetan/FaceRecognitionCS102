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

        addSideBarButtons();

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
            public void actionPerformed(ActionEvent e) {
                toggleSidebar(); // all the function
            }
        });

    }

    // function adding buttons
    public void addSideBarButtons() {
        JButton recognitionBtn = new JButton("Live Recognition");
        JButton studentBtn = new JButton("Students");
        JButton sessionBtn = new JButton("Attendence Sessions");
        JButton reportBtn = new JButton("Reports");
        JButton settingBtn = new JButton("Settings");

        // define a consistent size for all the buttons
        Dimension buttonSize = new Dimension(180, 40);
        // put them in an array
        JButton[] buttons = { recognitionBtn, studentBtn, sessionBtn, reportBtn, settingBtn };
        for (JButton btn : buttons) {
            btn.setMaximumSize(buttonSize);
            btn.setPreferredSize(buttonSize);
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            sidebar.add(Box.createRigidArea(new Dimension(0, 10))); // veritcal spacing
            sidebar.add(btn);
        }
        sidebar.add(Box.createVerticalGlue()); // push everything up neatly

        // ---------ActionListener for the buttons--------------
        recognitionBtn.addActionListener(e ->{
            MainDashboard.this.setVisible(false);
            MyGUIProgram.main(null); // just run the program
            // show the window again after MYGUIProgram close
            MainDashboard.this.setVisible(true);
        });
    }

    // function of toggle sidebar
    private void toggleSidebar() {
        // make it as an animation (sliding)
        int startWidth = sidebar.getWidth(); // get the current width
        int targetWidth = isSidebarVisible ? 0 : 200; // 0 to hide, 200 to show
        int step = (targetWidth > startWidth) ? 10 : -10; // slide direction, how much to change the width

        Timer timer = new Timer(5, null); // runs every 5 milliseconds
        timer.addActionListener(new ActionListener() {
            int width = startWidth;

            @Override
            public void actionPerformed(ActionEvent e) {
                width += step; // changes the sidebar's width gradually

                // stop animation when we reach the target
                if ((step < 0 && width <= targetWidth) || (step > 0 && width >= targetWidth)) {
                    width = targetWidth;
                    timer.stop();
                    isSidebarVisible = !isSidebarVisible; // update the status, if true-->false, if false->true 
                }

                // resize the sidebar & window
                sidebar.setPreferredSize(new Dimension(Math.max(0, width), sidebar.getHeight()));
                sidebar.revalidate(); // recalculate and adjust the layout
                sidebar.repaint(); // redraws the window
                MainDashboard.this.revalidate();
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
