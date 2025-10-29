package facecrop;
import ConfigurationAndLogging.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import javax.swing.*;

import app.gui.LiveRecognitionViewer;

public class MyGUIProgram extends JFrame {
    static {
        // Load OpenCV native library
        System.load(new File("lib/opencv_java480.dll").getAbsolutePath());
    }
    
    public MyGUIProgram() {

        setTitle("My GUI Program");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new GridLayout(2, 1));
        JPanel panel = new JPanel();
        panel.setBackground(Color.yellow);
        panel.setLayout(new GridLayout(1, 1));
        // getLabel(), setLabel(String), setEnable(boolean)
        JButton btn2 = new JButton("Start FaceRecognitionDemo");
        // getText(), setText(String), setColumns(int), setEditable(boolean)
        panel.add(btn2);
        btn2.addActionListener(new RunFaceRecognitionDemo());
        add(panel);
        setLocationRelativeTo(null); // Center window
        setVisible(true);
    }
    private class RunFaceRecognitionDemo implements ActionListener{
        @Override
        public void actionPerformed(ActionEvent evt){
            MyGUIProgram.this.setVisible(false);
            SwingUtilities.invokeLater(()->{
                LiveRecognitionViewer demoWindow = new LiveRecognitionViewer();
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
