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
if (image.channels() &gt; 1) {
Imgproc.cvtColor(image, grayImage, Imgproc.COLOR_BGR2GRAY);
} else {
grayImage = image.clone();
}
double sharpness = calculateSharpness(grayImage);
double brightness = calculateBrightness(grayImage);
double contrast = calculateContrast(grayImage);
