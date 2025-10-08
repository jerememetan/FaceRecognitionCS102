package facecrop;
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
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
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
        public void actionPerformed(ActionEvent evt){
            MyGUIProgram.this.setVisible(false);
            Thread demoThread = new Thread(() -> FaceCropDemo.main(null));
            demoThread.start();
        // Wait for demo to finish, then show GUI again
            new Thread(() -> {
                try {
                    demoThread.join(); // waits for the thread to terminate
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // Show GUI again on the AWT event thread
                SwingUtilities.invokeLater(() -> MyGUIProgram.this.setVisible(true));
            }).start();
            }
    }
    private class RunFaceRecognitionDemo implements ActionListener{
        @Override
        public void actionPerformed(ActionEvent evt){
            MyGUIProgram.this.setVisible(false);
            Thread demoThread = new Thread(() -> FaceRecognitionDemo.main(null));
            demoThread.start();

            new Thread(() -> {
                try {
                    demoThread.join(); // waits for the thread to terminate
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                SwingUtilities.invokeLater(() -> MyGUIProgram.this.setVisible(true));
            }).start();

        }
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(MyGUIProgram::new);
    }
}
