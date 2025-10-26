package facecrop;
import ConfigurationAndLogging.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import javax.swing.*;

public class MyGUIProgram extends JFrame {
    static {
        // Load OpenCV native library
        System.load(new File("lib/opencv_java480.dll").getAbsolutePath());
    }
    
    public MyGUIProgram() {

        setTitle("My GUI Program");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new GridLayout(3, 1));
        JPanel panel = new JPanel();
        panel.setBackground(Color.yellow);
        panel.setLayout(new GridLayout(1, 2));
        // getText(), setText(String), getAlignment(), setAlignment(int)
        JButton btn = new JButton("Start FaceCropDemo");
        // getLabel(), setLabel(String), setEnable(boolean)
        JButton btn2 = new JButton("Start FaceRecognitionDemo");
        // getText(), setText(String), setColumns(int), setEditable(boolean)
        panel.add(btn);
        panel.add(btn2);
        btn.addActionListener(new RunFaceCropDemo());
        btn2.addActionListener(new RunFaceRecognitionDemo());
        add(panel);
        setLocationRelativeTo(null); // Center window
        setVisible(true);
    }
    private class RunFaceCropDemo implements ActionListener{

    @Override
    public void actionPerformed(ActionEvent evt) {
        // 1. Hide the launcher window immediately
        MyGUIProgram.this.setVisible(false);

        // --- NEW LOGIC: Start the Name/ID GUI on the EDT ---
        SwingUtilities.invokeLater(() -> {
            // Get necessary file paths (should be done once in the main launch sequence, but safe here)
            String saveFolder = AppConfig.getInstance().getDatabaseStoragePath();
            new File(saveFolder).mkdirs(); 
            
            Name_ID_GUI gui = new Name_ID_GUI();
            
            gui.setDataSubmittedListener((int id, String name1) -> {
                // This is executed when the user submits ID/Name
                // 1. Determine the final save path
                String finalPath = saveFolder + "/"+ id + "_" + name1;
                new File(finalPath).mkdirs();
                AppLogger.info("Launching NewFaceCropDemo for Id:" + id + " Name:" + name1);
                // 2. Launch the NEW DEMO WINDOW
                // This must also be called on the EDT
                SwingUtilities.invokeLater(() -> {
                    NewFaceCropDemo demoWindow = new NewFaceCropDemo(finalPath);
                    // 3. Set a listener to show the launcher when the demo is closed
                    demoWindow.addWindowListener(new java.awt.event.WindowAdapter() {
                        @Override
                        public void windowClosed(java.awt.event.WindowEvent windowEvent) {
                            // Show the launcher again when the demo is closed (via the 'X' button)
                            SwingUtilities.invokeLater(() -> MyGUIProgram.this.setVisible(true));
                        }
                    });
                });
            });
            // You might need to set the Name_ID_GUI visible here if it's not by default
            gui.setVisible(true);
        });
    }
    }
    private class RunFaceRecognitionDemo implements ActionListener{
        @Override
        public void actionPerformed(ActionEvent evt){
            MyGUIProgram.this.setVisible(false);
            SwingUtilities.invokeLater(()->{
                NewFaceRecognitionDemo demoWindow = new NewFaceRecognitionDemo();
                demoWindow.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosed(java.awt.event.WindowEvent windowEvent) {
                        SwingUtilities.invokeLater(() -> MyGUIProgram.this.setVisible(true));
                    }
                });
            
            });

        }
        
    }
    public static void main(String[] args) {
        // load App Configs
        AppLogger.info("Configuration and core components loaded.");
        AppConfig.getInstance();
        AppLogger.info("Configuration file loaded");
        // start up MyGUIProgram
        AppLogger.info("MyGUIProgram Started!");
        SwingUtilities.invokeLater(MyGUIProgram::new);
    }

}
