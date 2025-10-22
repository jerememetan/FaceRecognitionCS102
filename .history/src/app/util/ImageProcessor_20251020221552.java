package app.util;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;
import org.opencv.imgproc.CLAHE;
import org.opencv.objdetect.CascadeClassifier;

import java.util.Arrays;
import java.util.Comparator;

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
// ✅ CRITICAL FIX: Keep as BGR, no conversion to grayscale
// OpenFace requires color images for best performance
// Just resize to standard size
Mat resized = new Mat();
Imgproc.resize(faceImage, resized, STANDARD_SIZE, 0, 0, Imgproc.INTER_CUBIC);
// Normalize to 0-255 range
Mat normalized = new Mat();
Core.normalize(resized, normalized, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);
resized.release();
return normalized;
}

/**
* Quality validation (unchanged - works on grayscale for metrics)
*/
public ImageQualityResult validateImageQualityDetailed(Mat image) {
if (image.empty()) {
return new ImageQualityResult(false, 0, "Image is empty");
}
if (image.width() < 50 || image.height() < 50) {
return new ImageQualityResult(false, 0, "Image too small (minimum 50x50 pixels)")
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
if (sharpness &lt; MIN_SHARPNESS_THRESHOLD) {
isQualityGood = false;
feedback.append("Image too blurry (sharpness: ").append(String.format("%.1f",
overallScore += 0;
} else {
overallScore += 30;
}
if (brightness &lt; MIN_BRIGHTNESS) {
isQualityGood = false;
feedback.append("Image too dark (brightness: ").append(String.format("%.1f",
overallScore += 0;
} else if (brightness &gt; MAX_BRIGHTNESS) {
isQualityGood = false;
feedback.append("Image too bright (brightness: ").append(String.format("%.1f"
overallScore += 0;
} else {
overallScore += 35;
}
if (contrast &lt; MIN_CONTRAST) {
isQualityGood = false;
feedback.append("Image has poor contrast (contrast: ").append(String.format("
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
laplacian.release();
mean.release();
stddev.release()