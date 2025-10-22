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


    public Mat preprocessFaceImage(Mat faceImage) {
        if (faceImage.empty()) {
            return faceImage;
        }
        // ✅ Keep color
        Mat resized = new Mat();
        Imgproc.resize(faceImage, resized, STANDARD_SIZE, 0, 0, Imgproc.INTER_CUBIC);

        // Normalize to 0–255 range
        Mat normalized = new Mat();
