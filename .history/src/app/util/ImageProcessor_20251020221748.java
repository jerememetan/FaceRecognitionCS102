package app.util;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;

public class ImageProcessor {
    private static final double MIN_SHARPNESS_THRESHOLD = 120.0;
    private static final double MIN_BRIGHTNESS = 40.0;
    private static final double MAX_BRIGHTNESS = 220.0;
    private static final double MIN_CONTRAST = 25.0;
    private static final Size STANDARD_SIZE = new Size(200, 200);

    /**
     * ✅ FIXED: Removed grayscale conversion.
     * Now preserves BGR color for training pipeline.
     */
    public Mat preprocessFaceImage(Mat faceImage) {
        if (faceImage.empty()) {
            return faceImage;
        }
        // ✅ Keep color
        Mat resized = new Mat();
        Imgproc.resize(faceImage, resized, STANDARD_SIZE, 0, 0, Imgproc.INTER_CUBIC);

        // Normalize to 0–255 range
        Mat normalized = new Mat();
        Core.normalize(resized, normalized, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);
        resized.release();
        return normalized;
    }

    /**
     * Quality validation (works on grayscale for metrics)
     */
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
        grayImage.release();

        StringBuilder feedback = new StringBuilder();
        boolean isQualityGood = true;
        double overallScore = 0;

        // Sharpness check
        if (sharpness < MIN_SHARPNESS_THRESHOLD) {
            isQualityGood = false;
            feedback.append(String.format("Image too blurry (sharpness: %.1f). ", sharpness));
        } else {
            overallScore += 30;
        }

        // Brightness check
        if (brightness < MIN_BRIGHTNESS) {
            isQualityGood = false;
            feedback.append(String.format("Image too dark (brightness: %.1f). ", brightness));
        } else if (brightness > MAX_BRIGHTNESS) {
            isQualityGood = false;
            feedback.append(String.format("Image too bright (brightness: %.1f). ", brightness));
        } else {
            overallScore += 35;
        }

        // Contrast check
        if (contrast < MIN_CONTRAST) {
            isQualityGood = false;
            feedback.append(String.format("Image has poor contrast (contrast: %.1f). ", contrast));
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
        double variance = Math.pow(stddev.get(0, 0)[0], 2);
        laplacian.release();
        mean.release();
        stddev.relea
