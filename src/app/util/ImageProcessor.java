package app.util;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;
import org.opencv.imgproc.CLAHE;
import org.opencv.objdetect.CascadeClassifier;

import java.util.Arrays;
import java.util.Comparator;

public class ImageProcessor {

    private static final double MIN_SHARPNESS_THRESHOLD = 80.0;
    private static final double MIN_BRIGHTNESS = 30.0;
    private static final double MAX_BRIGHTNESS = 230.0;
    private static final double MIN_CONTRAST = 20.0;
    private static final Size STANDARD_SIZE = new Size(200, 200);

    public Mat preprocessFaceImage(Mat faceImage) {
        if (faceImage.empty()) {
            return faceImage;
        }

        Mat processedImage = new Mat();

        if (faceImage.channels() > 1) {
            Imgproc.cvtColor(faceImage, processedImage, Imgproc.COLOR_BGR2GRAY);
        } else {
            processedImage = faceImage.clone();
        }

        Mat filteredImage = new Mat();
        Imgproc.bilateralFilter(processedImage, filteredImage, 9, 75, 75);

        CLAHE clahe = Imgproc.createCLAHE(2.0, new Size(8, 8));
        Mat contrastEnhanced = new Mat();
        clahe.apply(filteredImage, contrastEnhanced);

        Mat resized = new Mat();
        Imgproc.resize(contrastEnhanced, resized, STANDARD_SIZE, 0, 0, Imgproc.INTER_CUBIC);

        Mat normalized = new Mat();
        Core.normalize(resized, normalized, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);

        return normalized;
    }

    public ImageQualityResult validateImageQualityDetailed(Mat image) {
        if (image.empty()) {
            return new ImageQualityResult(false, 0, "Image is empty");
        }

        if (image.width() < 50 || image.height() < 50) {
            return new ImageQualityResult(false, 0, "Image too small (minimum 50x50 pixels)");
        }

        Mat grayImage = new Mat();
        if (image.channels() > 1) {
            Imgproc.cvtColor(image, grayImage, Imgproc.COLOR_BGR2GRAY);
        } else {
            grayImage = image.clone();
        }

        double sharpness = calculateSharpness(grayImage);
        double brightness = calculateBrightness(grayImage);
        double contrast = calculateContrast(grayImage);

        StringBuilder feedback = new StringBuilder();
        boolean isQualityGood = true;
        double overallScore = 0;

        if (sharpness < MIN_SHARPNESS_THRESHOLD) {
            isQualityGood = false;
            feedback.append("Image too blurry (sharpness: ").append(String.format("%.1f", sharpness)).append("). ");
            overallScore += 0;
        } else {
            overallScore += 30;
        }

        if (brightness < MIN_BRIGHTNESS) {
            isQualityGood = false;
            feedback.append("Image too dark (brightness: ").append(String.format("%.1f", brightness)).append("). ");
            overallScore += 0;
        } else if (brightness > MAX_BRIGHTNESS) {
            isQualityGood = false;
            feedback.append("Image too bright (brightness: ").append(String.format("%.1f", brightness)).append("). ");
            overallScore += 0;
        } else {
            overallScore += 35;
        }

        if (contrast < MIN_CONTRAST) {
            isQualityGood = false;
            feedback.append("Image has poor contrast (contrast: ").append(String.format("%.1f", contrast))
                    .append("). ");
            overallScore += 0;
        } else {
            overallScore += 35;
        }

        String message = isQualityGood ? "Good image quality" : feedback.toString();
        return new ImageQualityResult(isQualityGood, overallScore, message);
    }

    public boolean validateImageQuality(Mat image) {
        return validateImageQualityDetailed(image).isGoodQuality();
    }

    private double calculateSharpness(Mat image) {
        Mat laplacian = new Mat();
        Imgproc.Laplacian(image, laplacian, CvType.CV_64F);

        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stddev = new MatOfDouble();
        Core.meanStdDev(laplacian, mean, stddev);

        double variance = stddev.get(0, 0)[0] * stddev.get(0, 0)[0];
        return variance;
    }

    private double calculateBrightness(Mat image) {
        Scalar meanValue = Core.mean(image);
        return meanValue.val[0];
    }

    private double calculateContrast(Mat image) {
        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stddev = new MatOfDouble();
        Core.meanStdDev(image, mean, stddev);

        return stddev.get(0, 0)[0];
    }

    public Mat enhanceLighting(Mat image) {
        Mat enhanced = new Mat();

        Mat lookupTable = createGammaLookupTable(1.2);
        Core.LUT(image, lookupTable, enhanced);

        return enhanced;
    }

    private Mat createGammaLookupTable(double gamma) {
        Mat lookupTable = new Mat(1, 256, CvType.CV_8U);

        for (int i = 0; i < 256; i++) {
            double value = Math.pow(i / 255.0, gamma) * 255.0;
            lookupTable.put(0, i, Math.min(255, Math.max(0, value)));
        }

        return lookupTable;
    }

    public Mat correctFaceOrientation(Mat faceImage) {
        if (faceImage == null || faceImage.empty()) {
            return faceImage;
        }

        Mat gray = new Mat();
        Mat equalized = new Mat();

        try {
            if (faceImage.channels() > 1) {
                Imgproc.cvtColor(faceImage, gray, Imgproc.COLOR_BGR2GRAY);
            } else {
                gray = faceImage.clone();
            }

            Imgproc.equalizeHist(gray, equalized);

            CascadeClassifier eyeDetector = new CascadeClassifier("data/resources/haarcascade_eye.xml");
            if (eyeDetector.empty()) {
                return faceImage;
            }

            MatOfRect eyes = new MatOfRect();
            eyeDetector.detectMultiScale(equalized, eyes, 1.1, 3, 0, new Size(20, 20), new Size());

            Rect[] eyeArray = eyes.toArray();
            if (eyeArray.length >= 2) {
                Arrays.sort(eyeArray, Comparator.comparingInt(rect -> rect.x));

                Rect leftEye = eyeArray[0];
                Rect rightEye = eyeArray[eyeArray.length - 1];

                Point leftCenter = new Point(leftEye.x + leftEye.width / 2.0,
                        leftEye.y + leftEye.height / 2.0);
                Point rightCenter = new Point(rightEye.x + rightEye.width / 2.0,
                        rightEye.y + rightEye.height / 2.0);

                double dx = rightCenter.x - leftCenter.x;
                double dy = rightCenter.y - leftCenter.y;

                if (Math.abs(dx) > 1e-3) {
                    double angle = Math.toDegrees(Math.atan2(dy, dx));

                    if (Math.abs(angle) > 5.0 && Math.abs(angle) <= 20.0) {
                        Mat rotated = rotateImage(faceImage, -angle);
                        eyes.release();
                        return rotated;
                    }
                }
            }

            eyes.release();
        } catch (Exception e) {
            System.err.println("Face orientation correction failed: " + e.getMessage());
        } finally {
            gray.release();
            equalized.release();
        }

        return faceImage;
    }

    private Mat rotateImage(Mat image, double angle) {
        Point center = new Point(image.width() / 2.0, image.height() / 2.0);
        Mat rotationMatrix = Imgproc.getRotationMatrix2D(center, angle, 1.0);

        Mat rotated = new Mat();
        Imgproc.warpAffine(image, rotated, rotationMatrix, image.size(), Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE);
        rotationMatrix.release();

        return rotated;
    }

    public Mat reduceNoise(Mat image) {
        Mat denoised = new Mat();

        if (image.channels() == 1) {
            Photo.fastNlMeansDenoising(image, denoised, 10.0f, 7, 21);
        } else {
            Photo.fastNlMeansDenoisingColored(image, denoised, 10.0f, 10.0f, 7, 21);
        }

        return denoised;
    }

    public static class ImageQualityResult {
        private boolean goodQuality;
        private double qualityScore;
        private String feedback;

        public ImageQualityResult(boolean goodQuality, double qualityScore, String feedback) {
            this.goodQuality = goodQuality;
            this.qualityScore = qualityScore;
            this.feedback = feedback;
        }

        public boolean isGoodQuality() {
            return goodQuality;
        }

        public double getQualityScore() {
            return qualityScore;
        }

        public String getFeedback() {
            return feedback;
        }

        @Override
        public String toString() {
            return String.format("Quality: %s, Score: %.1f, Feedback: %s",
                    goodQuality ? "Good" : "Poor", qualityScore, feedback);
        }
    }
}