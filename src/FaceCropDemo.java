package src;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;
import org.opencv.highgui.HighGui;
import java.util.concurrent.atomic.AtomicReference;
import java.io.File;
import src.ConfigurationAndLogging.*;

import javax.swing.SwingUtilities;

public class FaceCropDemo {
    static {
        // Load OpenCV native library
        System.load(new File("lib/opencv_java480.dll").getAbsolutePath());
    }
    final static Object syncObject = new Object();
    final static AtomicReference<String> finalSaveFolder = new AtomicReference<>(null);

    public static void main(String[] args) {
        String saveFolder = ".\\project\\";
        String cascadePath =".\\opencv-cascade-classifier\\haarcascade_frontalface_alt.xml";
        new File(saveFolder).mkdirs();         // Create save folder if it doesn't exist


    SwingUtilities.invokeLater(() -> {
        Name_ID_GUI gui = new Name_ID_GUI();
            gui.setDataSubmittedListener(new Name_ID_GUI.DataSubmittedListener() {
                public void onDataSubmitted(int id, String name) {
                    // The data is now extracted and available here
                    
                    finalSaveFolder.set(saveFolder + id + "_" + name);
                    new File(finalSaveFolder.get()).mkdirs(); // creates a folder if no folder is found
                    AppLogger.info("Launching FaceCropDemo for Id:" + id + " Name:" + name );
                    // Notify the main thread that the data is ready
                    synchronized(syncObject) {
                        syncObject.notify();
                    };
                }
            });
        });

    // sync data
    synchronized(syncObject) {
        try {
            syncObject.wait();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    String finalPath = finalSaveFolder.get(); 
        // Load face detector
        CascadeClassifier faceDetector = new CascadeClassifier(cascadePath);
        if (faceDetector.empty()) {
            AppLogger.error("Error loading cascade file: " + cascadePath);
            return;
        }

        // Open webcam
        VideoCapture capture = new VideoCapture(0);
        if (!capture.isOpened()) {
            AppLogger.error("Error opening webcam!");
            return;
        }

        Mat frame = new Mat();
        Mat gray = new Mat();
        HighGui.namedWindow("Face Detection - Press 'p' to save face, 'q' to quit", HighGui.WINDOW_AUTOSIZE);
        AppLogger.info("FaceCropDemo Program Started!");

        while (true) {
            if (!capture.read(frame)) {
                AppLogger.warn("No Frame Captured!");
                break;
            }
            // Img camera settings
            Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
            // Adding Noise Reduction with blurring
            Imgproc.GaussianBlur(gray, gray, new Size(5, 5), 0);
            // OPTION 2: using Adpative Histogram Equalization to improve constract with fancy algothrim
            Imgproc.createCLAHE(2.0, new Size(8, 8)).apply(gray, gray);
            // Detect faces
            MatOfRect faces = new MatOfRect();
            // CHANGABLE: This is changable and adjust its settings
            // scale factor - increase for speed, decrease for accuracy
            // Min neigbours: increase for better detection and the risk of false positives
            // Min Size: minimum size, increase, if faces are closer to camera, decrease if faces are further from camera
            faceDetector.detectMultiScale(gray, faces, 1.05, 5, 0, new Size(80, 80), new Size());

            Rect[] faceArray = faces.toArray();
            
            // Draw rectangles around detected faces
            for (Rect rect : faceArray) {
                Imgproc.rectangle(frame, new Point(rect.x, rect.y),
                        new Point(rect.x + rect.width, rect.y + rect.height),
                        new Scalar(0, 255, 0), 2);
                        
                // Add face count text
                Imgproc.putText(frame, "Face detected", 
                    new Point(rect.x, rect.y - 10),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(0, 255, 0), 2);
            }

            // Add instruction text to frame
            String instruction = "Press 'p' to save, 'q' to quit. Faces: " + faceArray.length;
            Imgproc.putText(frame, instruction, new Point(10, 30),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(255, 255, 255), 2);

            // Show frame
            HighGui.imshow("Face Detection - Press 'p' to save face, 'q' to quit", frame);

            // SOLUTION 1: Use shorter wait time and mask the key
            int key = HighGui.waitKey(5) & 0xFF;
            
            if (faceArray.length > 0 && key == 'p' || key == 'P') {
                    // Crop and save the first detected face
                    try {
                        Rect rect = faceArray[0];
                        // image preprocess
                        Mat face = gray.submat(rect);
                        Mat resizedFace = new Mat();
                        Imgproc.resize(face, resizedFace, new Size(200, 200));
                        String fileName = finalPath + "\\face_" + System.currentTimeMillis() + ".jpg";
                        boolean saved = Imgcodecs.imwrite(fileName, resizedFace);
                        if (saved) {
                            AppLogger.info("✓ Captured Image has been saved! ");
                        } else {
                            AppLogger.error("✗ Captured Image failed to save!");
                        }
                        // Clean up temporary Mat
                        resizedFace.release();
                        face.release();

                    } catch (ArrayIndexOutOfBoundsException e) {
                        AppLogger.warn("Face did not get captured!");

                    }
                    


                    


            }
            
            if (key == 'q' || key == 'Q' || key == 27) { // 'q', 'Q', or ESC
                break;
            }
            
        }

        // Cleanup
        capture.release();
        frame.release();
        gray.release();
        HighGui.waitKey(3);
        HighGui.destroyAllWindows(); // Ensure all windows are closed properly
        AppLogger.info("Ended Capturing Session for "+ finalPath);
        System.gc();
    }
}