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
        //     System.out.println("Usage: java FaceRecognitionDemo <person1-dir> <person2-dir> <haarcascade-path>");
        //     System.out.println("Example: java FaceRecognitionDemo D:\\person1 D:\\person2 .\\haarcascade_frontalface_alt.xml");
        //     return;t
        // }

        String person1Dir = ".\\project\\Trump"; // args[0];
        String person2Dir = ".\\project\\Jereme"; // args[1];
        String person3Dir = ".\\project\\TaylorSwift"; // args[1];
        String cascadePath = ".\\haarcascade_frontalface_alt.xml"; // args[2];

        // Load face detector
        CascadeClassifier faceDetector = new CascadeClassifier(cascadePath);
        if (faceDetector.empty()) {
            System.out.println("Error loading cascade file: " + cascadePath);
            return;
        }

        // Load training images and compute histograms
        List<Mat> person1Images = loadImages(person1Dir);
        List<Mat> person2Images = loadImages(person2Dir);
        List<Mat> person3Images = loadImages(person3Dir);
        if (person1Images.isEmpty() || person2Images.isEmpty() || person3Images.isEmpty()) {
            System.out.println("No training images found in one or both directories!");
            return;
        }

        List<Mat> person1Histograms = computeHistograms(person1Images);
        List<Mat> person2Histograms = computeHistograms(person2Images);
        List<Mat> person3Histograms = computeHistograms(person3Images);

        // Open webcam
        VideoCapture capture = new VideoCapture(0);
        if (!capture.isOpened()) {
            System.out.println("Error opening webcam!");
            return;
        }

        // Create display window
        JFrame frame = new JFrame("Real-Time Face Recognition");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
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
                double bestScore1 = getBestHistogramScore(faceHist, person1Histograms);
                double bestScore2 = getBestHistogramScore(faceHist, person2Histograms);
                double bestScore3 = getBestHistogramScore(faceHist, person3Histograms);
                System.out.println("Scores: Trump=" + bestScore1 + ", Jereme=" + bestScore2 + ", Taylor=" + bestScore3);

                // Label based on best score (correlation: higher is better)
                // CHANGED THIS SO THAT IT CAN BE EXTENDABLE
                double[] scores = {bestScore1, bestScore2, bestScore3};
                String[] names = {"Donald Trump", "Jereme Tan", "Taylor Swift"};
                int maxIdx = 0;
                for (int i = 1; i < scores.length; i++) {
                    if (scores[i] > scores[maxIdx]) maxIdx = i;
                }
                String displayText = scores[maxIdx] > 0.65 ? names[maxIdx] : "Unknown";
                Imgproc.putText(webcamFrame, displayText, new Point(rect.x, rect.y - 10),
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.9, new Scalar(0, 255, 0), 2);
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
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".png"));
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
        byte[] data = new byte[width * height * (int)mat.elemSize()];
        mat.get(0, 0, data);
        image.getRaster().setDataElements(0, 0, width, height, data);
        return image;
    }
}