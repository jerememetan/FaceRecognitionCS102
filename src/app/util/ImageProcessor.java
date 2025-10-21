package app.util;

import ConfigurationAndLogging.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;
import org.opencv.imgproc.CLAHE;
import org.opencv.objdetect.CascadeClassifier;
import ConfigurationAndLogging.*;
import java.util.Arrays;
import java.util.Comparator;

public class ImageProcessor {

    private static final double MIN_SHARPNESS_THRESHOLD = AppConfig.getInstance().getPreprocessingMinSharpnessThreshold();
    private static final double MIN_BRIGHTNESS = AppConfig.getInstance().getPreprocessingMinBrightness();
    private static final double MAX_BRIGHTNESS = AppConfig.getInstance().getPreprocessingMaxBrightness();
    private static final double MIN_CONTRAST = AppConfig.getInstance().getPreprocessingMinContrast();
    private static final int crop_size = AppConfig.KEY_RECOGNITION_CROP_SIZE_PX;
    private static final Size STANDARD_SIZE = new Size(crop_size, crop_size);

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

        double kernelSize = (double)AppConfig.KEY_PREPROCESSING_GAUSSIAN_KERNEL_SIZE; 
        double sigmaX = (double)AppConfig.KEY_PREPROCESSING_GAUSSIAN_SIGMA_X;
        // --- ADDED: FIXED GAUSSIAN BLUR (Noise Reduction) in replacement of bilateralFilter--- 
        Imgproc.GaussianBlur(processedImage, filteredImage, new Size(kernelSize, kernelSize), sigmaX);      
        
        // Imgproc.bilateralFilter(processedImage, filteredImage, 9, 75, 75);

        double clipLimit = AppConfig.KEY_PREPROCESSING_CLAHE_CLIP_LIMIT; 
        double gridSize = AppConfig.KEY_PREPROCESSING_CLAHE_GRID_SIZE;
        CLAHE clahe = Imgproc.createCLAHE(clipLimit, new Size(gridSize, gridSize));
        Mat contrastEnhanced = new Mat();
        clahe.apply(filteredImage, contrastEnhanced);

        Mat resized = new Mat();
        Imgproc.resize(faceImage, resized, STANDARD_SIZE, 0, 0, Imgproc.INTER_CUBIC);

        Mat normalized = new Mat();
        Core.normalize(resized, normalized, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);
        // Release intermediate Mats
        processedImage.release();
        filteredImage.release();
        contrastEnhanced.release();
        resized.release();       

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
        grayImage.release();

        StringBuilder feedback = new StringBuilder();
        boolean isQualityGood = true;
        double overallScore = 0;

        if (sharpness < MIN_SHARPNESS_THRESHOLD) {
            isQualityGood = false;
            feedback.append(String.format("Image too blurry (sharpness: %.1f). ", sharpness));
        } else {
            overallScore += 30;
        }

        if (brightness < MIN_BRIGHTNESS) {
            isQualityGood = false;
            feedback.append(String.format("Image too dark (brightness: %.1f). ", brightness));
        } else if (brightness > MAX_BRIGHTNESS) {
            isQualityGood = false;
            feedback.append(String.format("Image too bright (brightness: %.1f). ", brightness));
        } else {
            overallScore += 35;
        }

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
        stddev.release();
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
        double result = stddev.get(0, 0)[0];
        mean.release();
        stddev.release();
        return result;
    }

    public Mat enhanceLighting(Mat image) {
        Mat enhanced = new Mat();
        Mat lookupTable = createGammaLookupTable(1.2);
        Core.LUT(image, lookupTable, enhanced);
        lookupTable.release();
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

    public Mat reduceNoise(Mat image) {
        Mat denoised = new Mat();
        if (image.channels() == 1) {
            Photo.fastNlMeansDenoising(image, denoised, 7.0f, 7, 21);
        } else {
            Photo.fastNlMeansDenoisingColored(image, denoised, 7.0f, 7.0f, 7, 21);
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
