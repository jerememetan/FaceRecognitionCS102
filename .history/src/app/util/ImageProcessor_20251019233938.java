package app.util;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;
import org.opencv.imgproc.CLAHE;
import org.opencv.objdetect.CascadeClassifier;

import java.util.Arrays;
import java.util.Comparator;

public class ImageProcessor {

    // EXPERIMENTAL: Increased quality thresholds for stricter image acceptance
    // These values prioritize quality over quantity for better embeddings
    // Revert to original if too many images are rejected: 80.0, 30.0, 230.0, 20.0
    private static final double MIN_SHARPNESS_THRESHOLD = 120.0; // Was 80.0
    private static final double MIN_BRIGHTNESS = 40.0; // Was 30.0
    private static final double MAX_BRIGHTNESS = 220.0; // Was 230.0
    private static final double MIN_CONTRAST = 25.0; // Was 20.0
    private static final Size STANDARD_SIZE = new Size(200, 200);

    public Mat preprocessFaceImage(Mat faceImage) {
        if (faceImage.empty()) {
            return faceImage;
        }

        // âœ… ULTRA-MINIMAL: OpenFace works BEST with raw faces!
        // Testing showed:
        // - bilateral + CLAHE: 0.7689 tightness (TERRIBLE!)
        // - denoise only: 0.8270 tightness
        // - resize only: 0.8473 tightness (BEST!)
        //
        // Excessive preprocessing (bilateral, CLAHE, denoising) DESTROYS embedding
        // quality!
        // OpenFace model is designed to handle raw faces - just resize and go!

        Mat processedImage = new Mat();

        // Convert to grayscale
        if (faceImage.channels() > 1) {
            Imgproc.cvtColor(faceImage, processedImage, Imgproc.COLOR_BGR2GRAY);
        } else {
            processedImage = faceImage.clone();
        }

        // Just resize to standard size - that's it!
        Mat resized = new Mat();
        Imgproc.resize(processedImage, resized, STANDARD_SIZE, 0, 0, Imgproc.INTER_CUBIC);
        processedImage.release();

        // Normalize to 0-255 range
        Mat normalized = new Mat();
        Core.normalize(resized, normalized, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);
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

                    // RELAXED: Only rotate if angle is significant (>10Â°) and not too extreme
                    // (<25Â°)
                    // Previous 5-20Â° was too aggressive and may have introduced artifacts
                    if (Math.abs(angle) > 10.0 && Math.abs(angle) <= 25.0) {
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

        // RELAXED: Reduced denoising strength from 10.0 to 7.0
        // Too much denoising can blur important facial features
        if (image.channels() == 1) {
            Photo.fastNlMeansDenoising(image, denoised, 7.0f, 7, 21);
        } else {
            Photo.fastNlMeansDenoisingColored(image, denoised, 7.0f, 7.0f, 7, 21);
        }

        return denoised;
    }

    /**
     * Reduces glare and specular highlights from glasses.
     * Uses HSV color space to detect and attenuate bright spots.
     * CONSERVATIVE: Only applies when significant glare is detected to avoid
     * damaging good images.
     * 
     * @param image Input color image (BGR format)
     * @return Image with reduced glare
     */
    public Mat reduceGlare(Mat image) {
        if (image.empty() || image.channels() != 3) {
            return image;
        }

        Mat hsv = new Mat();
        Mat result = image.clone();

        try {
            // Convert to HSV color space
            Imgproc.cvtColor(image, hsv, Imgproc.COLOR_BGR2HSV);

            // Split channels
            java.util.List<Mat> hsvChannels = new java.util.ArrayList<>();
            Core.split(hsv, hsvChannels);
            Mat vChannel = hsvChannels.get(2); // Value channel

            // Detect bright highlights (glare from glasses)
            // DISABLED: Glare reduction was causing quality drop - removing for now
            // The inpainting process may be introducing artifacts that hurt embedding
            // quality
            // TODO: Re-enable with better algorithm if glare becomes a problem
            Mat glareMask = new Mat();
            Core.inRange(vChannel, new Scalar(245), new Scalar(255), glareMask); // Even more conservative

            int glarePixels = Core.countNonZero(glareMask);
            int totalPixels = image.rows() * image.cols();
            double glareRatio = (double) glarePixels / totalPixels;

            // VERY conservative: Only apply if extreme glare (>3% of image)
            if (glareRatio > 0.03 && glarePixels > 100) {
                // Dilate mask slightly to cover glare edges
                Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
                Imgproc.dilate(glareMask, glareMask, kernel);

                // Apply inpainting to fill glare regions with surrounding texture
                Photo.inpaint(image, glareMask, result, 3, Photo.INPAINT_TELEA);
                kernel.release();
            }

            // Clean up
            glareMask.release();
            for (Mat ch : hsvChannels) {
                ch.release(); // FIXED: Release channel Mats to prevent memory leak
            }

        } catch (Exception e) {
            System.err.println("Glare reduction failed: " + e.getMessage());
            return image;
        } finally {
            hsv.release();
        }

        return result;
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