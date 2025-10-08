package facecrop;
import java.awt.*;
import javax.swing.*;
import java.awt.event.*;
import java.io.File;
public class Name_ID_GUI extends JFrame {
    private JTextField IDText;
    private JTextField NameText;
    private JButton submit;
    private DataSubmittedListener Listener;

    static {
        // Load OpenCV native library
        System.load(new File("lib/opencv_java480.dll").getAbsolutePath());
    }

    public static interface DataSubmittedListener {
            void onDataSubmitted(int id, String name);
    }

    public Name_ID_GUI(){
            // set params
            setTitle("Enter your details");
            setSize(300,150);
            setVisible(true);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLayout(new GridLayout(3,1));
            // create first row
            JLabel IDLabel = new JLabel("Enter your ID: ");
            JTextField IDText = new JTextField(20);
            // create second row data
            JLabel NameLabel = new JLabel("Enter your Name:");
            JTextField NameText = new JTextField(20);
            // create submit button and add actionListener
            JButton submit = new JButton("Submit");


            submit.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent evt) {
                    if (IDText.getText().isEmpty() || NameText.getText().isEmpty()) {
                        // Change button appearance to show an error
                        submit.setText("Fields are empty!");
                        submit.setBackground(Color.RED);

                        // Create and start a timer to reset the button after 3 seconds
                        // The timer is a one-shot timer (setRepeats(false))
                        Timer timer = new Timer(3000, new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                // This code runs on the EDT after the delay
                                submit.setText("Submit");
                                submit.setBackground(null); // Reset color
                                ((Timer) e.getSource()).stop(); // Stop the timer
                            }
                        });
                        timer.setRepeats(false); // Ensure it only runs once
                        timer.start();
                    } else {
                        // Logic for valid input (e.g., save data, display message)
                        try {
                            int id = Integer.parseInt(IDText.getText());
                            String name = NameText.getText();
                            System.out.println("ID: " + id + ", Name: " + name);
                            if (Listener != null){
                            submit.setText("Success!");
                            submit.setBackground(Color.GREEN);
                            Listener.onDataSubmitted(id, name);
                            }
                            dispose();

                        } catch (NumberFormatException e) {
                            submit.setText("Invalid ID!");
                            submit.setBackground(Color.YELLOW);
                    
                        }
                    }
                }
            });
            
            //create panels to put all in the layout
            JPanel p = new JPanel();
            p.setLayout(new GridLayout(1,2));
            p.add(IDLabel);
            p.add(IDText);
            add(p);
            JPanel p2 = new JPanel();
            p2.setLayout(new GridLayout(1,2));
            p2.add(NameLabel);
            p2.add(NameText);
            add(p2);
            JPanel p3 = new JPanel();
            p3.setLayout(new GridLayout(1,2));
            p3.add(submit);
            add(p3);
            
            // center screen
            setLocationRelativeTo(null);
        }


        public void setDataSubmittedListener(DataSubmittedListener listener) {
            this.Listener = listener;
        }

        public static void main(String[] args){
                    SwingUtilities.invokeLater(() -> {
            Name_ID_GUI gui = new Name_ID_GUI();
            
            // Set the listener. The onDataSubmitted method will be called
            // when the submit button is pressed and the data is valid.
            gui.setDataSubmittedListener(new DataSubmittedListener() {
                @Override
                public void onDataSubmitted(int id, String name) {
                    System.out.println("Data received from GUI in the main class:");
                    System.out.println("ID: " + id);
                    System.out.println("Name: " + name);
                    // You can now process the data here
                    // e.g., save it to a database, perform calculations, etc.
                }
            });
        });
        }
    
}
