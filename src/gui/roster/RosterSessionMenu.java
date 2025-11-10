package gui.roster;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import gui.session.SessionViewer;
import java.awt.*;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Font;
import service.roster.RosterManager;
import service.session.SessionManager;
import gui.homepage.*;
//Landing Page upon selecting "Sessions" from "MainDashboard.java"
//Users choose to either manage rosters or manage sessions
public class RosterSessionMenu extends JFrame{
    private RosterManager rosterManager;
    private SessionManager sessionManager;
    public RosterSessionMenu(RosterManager rosterManager, SessionManager sessionManager){
        this.rosterManager = rosterManager;
        this.sessionManager = sessionManager;
        rosterManager.populateRosters();
        sessionManager.populateSessions();
        setTitle("Roster Session Menu");
        setSize(700,500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        

        // --- Title ---
        JLabel titleLabel = new JLabel("Welcome to Session Management System", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(40, 10, 30, 10));
        add(titleLabel, BorderLayout.NORTH);

        // --- Center panel with buttons ---
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(3, 1, 20, 30));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(40, 200, 40, 200)); // side margins

        JButton manageRosterButton = UIComponents.createAccentButton("Manage Roster", new Color(59, 130, 246));
        JButton viewSessionsButton = UIComponents.createAccentButton("View Sessions", new Color(59, 130, 246));
        JButton attendanceHistoryButton = UIComponents.createAccentButton("Attendance History", new Color(59, 130, 246));

        manageRosterButton.setFont(new Font("SansSerif", Font.BOLD, 22));
        viewSessionsButton.setFont(new Font("SansSerif", Font.BOLD, 22));
        attendanceHistoryButton.setFont(new Font("SansSerif", Font.BOLD, 22));

        manageRosterButton.setFocusPainted(false);
        viewSessionsButton.setFocusPainted(false);
        attendanceHistoryButton.setFocusPainted(false);

        buttonPanel.add(manageRosterButton);
        buttonPanel.add(viewSessionsButton);
        buttonPanel.add(attendanceHistoryButton);

        add(buttonPanel, BorderLayout.CENTER);

        // --- Actions ---
        manageRosterButton.addActionListener(e -> {
            setVisible(false); // Close the menu
            RosterViewer rosterViewer = new RosterViewer(rosterManager); // Or replace with your ManageRoster window
            rosterViewer.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            rosterViewer.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent windowEvent) {
                    SwingUtilities.invokeLater(() -> {
                        RosterSessionMenu.this.setVisible(true);
                    });
                }
            });
        });

        viewSessionsButton.addActionListener(e -> {
            setVisible(false);
            SessionViewer sessionViewer = new SessionViewer(sessionManager);
            sessionViewer.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            sessionViewer.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent windowEvent) {
                    SwingUtilities.invokeLater(() -> {
                        RosterSessionMenu.this.setVisible(true);
                    });
                }
            });
        });

        attendanceHistoryButton.addActionListener(e -> {
            setVisible(false);
            gui.attendance.AttendanceHistoryViewer historyViewer = new gui.attendance.AttendanceHistoryViewer();
            historyViewer.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            historyViewer.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent windowEvent) {
                    SwingUtilities.invokeLater(() -> {
                        RosterSessionMenu.this.setVisible(true);
                    });
                }
            });
            historyViewer.setVisible(true);
        });

        setVisible(true);
    }
}
