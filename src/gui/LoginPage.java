package gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

import gui.MainDashboard;

public class LoginPage extends JFrame{
    private JTextField idField;
    private JPasswordField passwordField;
    // private JComboBox<String> roleBox;
    private JButton loginButton;

    public LoginPage(){
        setTitle("Login");
        setSize(350, 150);
        setVisible(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // center the screen
        setLayout(new BorderLayout());

        // create the main panel
        JPanel formPanel = new JPanel(new GridLayout(2, 2));

        // First row userID
        formPanel.add(new JLabel("Enter your ID:"));
        idField = new JTextField(20);
        formPanel.add(idField);
        //second row password
        formPanel.add(new JLabel("Password:"));
        passwordField = new JPasswordField();
        formPanel.add(passwordField);
        
        add(formPanel);
        
        //login button
        loginButton = new JButton("Login");
        // create the button panel
        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.add(loginButton, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH); // stretch horizontaly to occupy the full width

        // add the actionListener
        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event){
                String id = idField.getText().trim();
                String password = new String(passwordField.getPassword());
                
                // check if any of the input is empty
                if(id.isEmpty() || password.isEmpty()){
                    loginButton.setText("Please enter both ID and password!");
                    loginButton.setBackground(Color.red);
                    return;
                }

                // todo: replace with the actual DB lookup for authentication
                String role = authenticateUser(id, password);
                // if there's no match of id or password --> role == null

                // if the user input invalid id/password
                if(role == null) {
                    JOptionPane.showMessageDialog(LoginPage.this, "Invalid ID or password!");
                    loginButton.setBackground(Color.red);
                    return;
                }

                System.out.println("Logging in as " + role + " with ID: " + id);
                
                // show the GUI of the main dashboard if login success
                SwingUtilities.invokeLater(new Runnable(){
                    @Override
                    public void run(){
                        new MainDashboard(role,id); // when pass role and id
                    }
                });
                dispose();
            }
        });

    }

    // Placeholder authentication (Testing only)
    private String authenticateUser(String id, String password) {
        // Example
        if(id.equals("123") && password.equals("admin123")) return "Admin";
        if(id.equals("456") && password.equals("ta123")) return "TA";

        //invalid login
        return null;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable(){
            @Override
            public void run(){
                new LoginPage();
            }
        });
    }

    
}

