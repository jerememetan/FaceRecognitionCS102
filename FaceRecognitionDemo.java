import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FaceRecognitionDemo {
    static {
        // Load OpenCV native library
        System.load(new File("lib/opencv_java480.dll").getAbsolutePath());
    }

    public static void main(String[] args) {
        // if (args.length < 3) {
        // System.out.println("Usage: java FaceRecognitionDemo <person1-dir>
        // <person2-dir> <haarcascade-path>");
        // System.out.println("Example: java FaceRecognitionDemo D:\\person1 D:\\person2
        // .\\haarcascade_frontalface_alt.xml");
        // return;t
        // }
        // make this an array in the future for this entire part
        String image_folder_path = ".\\data\\facedata\\";
        File folder_directories = new File(image_folder_path);

        ArrayList<String> folder_names = new ArrayList<String>(); // this stores the folder_names in an array list
        // [.\project\1_taylor, .\projects\5_jere]

        File[] list_files = folder_directories.listFiles(); // this
        if (list_files != null) {
            for (File file : list_files) { // for each file in list files
                // if its a folder
                if (file.isDirectory()) {
                    folder_names.add(image_folder_path + "\\" + file.getName());
                }

            }
        }

        System.out.println("=== Folder debug ===");
        for (String s : folder_names) {
            System.out.println("Found folder: " + s);
            File f = new File(s);
            if (f.exists()) {
                File[] imgs = f.listFiles();
                if (imgs != null) {
                    System.out.println("  Contains: " + imgs.length + " items");
                } else {
                    System.out.println("  (Cannot list files)");
                }
            } else {
                System.out.println("  (Folder does not exist!)");
            }
        }
        System.out.println("====================");

        // REPLACED
        // String person1Dir = ".\\project\\Trump"; // args[0];
        // String person2Dir = ".\\project\\Jereme"; // args[1];
        // String person3Dir = ".\\project\\TaylorSwift"; // args[1];
        String cascadePath = ".\\haarcascade_frontalface_alt.xml"; // args[2];

        // Load face detector
        CascadeClassifier faceDetector = new CascadeClassifier(cascadePath);
        if (faceDetector.empty()) {
            System.out.println("Error loading cascade file: " + cascadePath);
            return;
        }

        // Load training images and compute histograms
        // personImages will be an array that has a list of MAT Images, seperated by
        // folders
        ArrayList<List<Mat>> personImages = new ArrayList<List<Mat>>();
        for (String s : folder_names) {
            List<Mat> temp = loadImages(s);
            personImages.add(temp);
        }
        // REPLACED
        // List<Mat> person1Images = loadImages(person1Dir);
        // List<Mat> person2Images = loadImages(person2Dir);
        // List<Mat> person3Images = loadImages(person3Dir);

        // Check empty of any of the files
        for (List<Mat> t : personImages) {
            if (t.isEmpty()) {
                System.out.println("No training images found in one of the directories!");
                return;
            }
        }
        // REPLACED
        // if (person1Images.isEmpty() || person2Images.isEmpty() ||
        // person3Images.isEmpty()) {
        // System.out.println("No training images found in one of the directories!");
        // return;
        // }

        ArrayList<List<Mat>> personHistograms = new ArrayList<List<Mat>>();
        for (List<Mat> s : personImages) {
            List<Mat> temp = computeHistograms(s);
            personHistograms.add(temp);
        }
        // REPLACED
        // List<Mat> person1Histograms = computeHistograms(person1Images);
        // List<Mat> person2Histograms = computeHistograms(person2Images);
        // List<Mat> person3Histograms = computeHistograms(person3Images);

        // Open webcam
        VideoCapture capture = new VideoCapture(0);
        if (!capture.isOpened()) {
            System.out.println("Error opening webcam!");
            return;
        }

        // Create display window
        JFrame frame = new JFrame("Real-Time Face Recognition");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        JLabel label = new JLabel();
        frame.add(label);
        frame.setSize(640, 480);
        frame.setVisible(true);

        Mat webcamFrame = new Mat();
        while (frame.isVisible() && capture.read(webcamFrame)) {
            Mat gray = new Mat();
            Imgproc.cvtColor(webcamFrame, gray, Imgproc.COLOR_BGR2GRAY);
            // CHANGED: ADDED HISTOGRAM EQUALIZATION TO IMPROVE CONTRAST
            Imgproc.equalizeHist(gray, gray);
            // Detect faces
            MatOfRect faces = new MatOfRect();
            faceDetector.detectMultiScale(gray, faces, 1.05, 5, 0, new Size(80, 80), new Size());

            for (Rect rect : faces.toArray()) {
                // Draw rectangle
                Imgproc.rectangle(webcamFrame, new Point(rect.x, rect.y),
                        new Point(rect.x + rect.width, rect.y + rect.height),
                        new Scalar(0, 255, 0), 2);

                // Crop and resize face
                Mat face = gray.submat(rect);
                Imgproc.resize(face, face, new Size(200, 200));
                Mat faceHist = computeHistogram(face);

                // Compare with training histograms
                ArrayList<Double> personScores = new ArrayList<Double>();
                for (List<Mat> s : personHistograms) {
                    double temp = getBestHistogramScore(faceHist, s);
                    personScores.add(temp);
                }

                System.out.println("Person Scores: " + personScores.toString());
                // REPLACED
                // double bestScore1 = getBestHistogramScore(faceHist, person1Histograms);
                // double bestScore2 = getBestHistogramScore(faceHist, person2Histograms);
                // double bestScore3 = getBestHistogramScore(faceHist, person3Histograms);

                // double[] scores = {bestScore1, bestScore2, bestScore3};
                // String[] names = {"Donald Trump", "Jereme Tan", "Taylor Swift"};
                String displayText;
                int maxIdx = 0;
                for (int i = 1; i < personScores.size(); i++) {
                    if (personScores.get(i) > personScores.get(maxIdx)) {
                        maxIdx = i;
                    }
                }
                if (personScores.get(maxIdx) > 0.65) {
                    String[] parts = folder_names.get(maxIdx).split("_");
                    String ShowScore = String.format("%.2f", personScores.get(maxIdx));
                    displayText = parts[1] + " - " + ShowScore;

                } else {
                    displayText = "unknown";
                }
                // String displayText = scores[maxIdx] > 0.65 ? names[maxIdx] : "Unknown";
                Imgproc.putText(webcamFrame, displayText, new Point(rect.x, rect.y - 10),
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.9, new Scalar(15, 255, 15), 2);
            }

            // Display frame
            BufferedImage image = matToBufferedImage(webcamFrame);
            label.setIcon(new ImageIcon(image));
            label.repaint();
        }

        // Cleanup
        capture.release();
        frame.dispose();
    }

    // Load images from a directory
    private static List<Mat> loadImages(String dirPath) {
        List<Mat> images = new ArrayList<>();
        File dir = new File(dirPath);
        File[] files = dir
                .listFiles((d, name) -> name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".png"));
        if (files != null) {
            for (File file : files) {
                Mat img = Imgcodecs.imread(file.getAbsolutePath(), Imgcodecs.IMREAD_GRAYSCALE);
                if (!img.empty()) {
                    Imgproc.resize(img, img, new Size(200, 200));
                    images.add(img);
                } else {
                    System.out.println("Failed to load image: " + file.getAbsolutePath());
                }
            }
        }
        return images;
    }

    // Compute histogram for a single image
    private static Mat computeHistogram(Mat image) {
        Mat hist = new Mat();
        MatOfInt histSize = new MatOfInt(256);
        MatOfFloat ranges = new MatOfFloat(0f, 256f);
        MatOfInt channels = new MatOfInt(0);
        Imgproc.calcHist(List.of(image), channels, new Mat(), hist, histSize, ranges);
        Core.normalize(hist, hist, 0, 1, Core.NORM_MINMAX);
        return hist;
    }

    // Compute histograms for a list of images
    private static List<Mat> computeHistograms(List<Mat> images) {
        List<Mat> histograms = new ArrayList<>();
        for (Mat img : images) {
            histograms.add(computeHistogram(img));
        }
        return histograms;
    }

    // Get best histogram comparison score
    private static double getBestHistogramScore(Mat faceHist, List<Mat> histograms) {
        double bestScore = 0;
        for (Mat hist : histograms) {
            double score = Imgproc.compareHist(faceHist, hist, Imgproc.HISTCMP_CORREL);
            bestScore = Math.max(bestScore, score);
        }
        return bestScore;
    }

    // Convert Mat to BufferedImage for display
    private static BufferedImage matToBufferedImage(Mat mat) {
        int width = mat.cols();
        int height = mat.rows();
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (mat.channels() > 1) {
            type = BufferedImage.TYPE_3BYTE_BGR;
            // Convert BGR to RGB
            Mat rgbMat = new Mat();
            Imgproc.cvtColor(mat, rgbMat, Imgproc.COLOR_BGR2RGB);
            mat = rgbMat;
        }

        BufferedImage image = new BufferedImage(width, height, type);
        byte[] data = new byte[width * height * (int) mat.elemSize()];
        mat.get(0, 0, data);
        image.getRaster().setDataElements(0, 0, width, height, data);
        return image;
    }
}