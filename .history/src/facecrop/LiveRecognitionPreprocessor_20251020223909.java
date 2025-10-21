package app.service;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;


public class LiveRecognitionPreprocessor {
    private static final Size INPUT_SIZE = new Size(96, 96);
    private OpenCVFaceAligner faceAligner;
    private boolean alignmentAvailable = false;

    public LiveRecognitionPreprocessor() {
        try {
            faceAligner = new OpenCVFaceAligner();
            alignmentAvailable = faceAligner.isInitialized();
            if (alignmentAvailable) {
                System.out.println("✅ Face alignment enabled (OpenCV FacemarkLBF)");
            } else {
                System.out.println("⚠️ Face alignment not available - continuing without it!");
            }
        } catch (Exception e) {
            System.out.println("⚠️ Face alignment initialization failed: " + e.getMessage());
            alignmentAvailable = false;
        }
    }

    public Mat preprocessForLiveRecognition(Mat faceROI, Rect faceRect) {
if (faceROI == null || faceROI.empty()) {
System.err.println("❌ Empty face ROI provided");
return new Mat();
}
Mat processed = null;
try {

if (faceROI.channels() != 3) {
System.err.println("⚠️ WARNING: Expected BGR, got " + faceROI.channels());

Mat temp = new Mat();
if (faceROI.channels() == 1) {
Imgproc.cvtColor(faceROI, temp, Imgproc.COLOR_GRAY2BGR);
processed = temp;
} else {
processed = faceROI.clone();
}
} else {
processed = faceROI.clone();
}

if (alignmentAvailable && faceAligner != null) {
try {
Mat aligned = faceAligner.alignFace(processed, faceRect);
if (aligned != null &amp;&amp; !aligned.empty()) {
processed.release();
processed = aligned;
}
} catch (Exception e) {
System.err.println("⚠️ Face alignment failed, continuing without it");
}
}
// ✅ STEP 3: Histogram equalization for contrast
Mat equalized = applyHistogramEqualization(processed);
processed.release();
processed = equalized;
// ✅ STEP 4: Direct resize to 96x96 (no intermediate steps!)
Mat resized = new Mat();
Imgproc.resize(processed, resized, INPUT_SIZE, 0, 0, Imgproc.INTER_CUBIC);
processed.release();
processed = resized;
// ✅ STEP 5: Strong denoising for webcam noise
Mat denoised = new Mat();
if (processed.channels() == 1) {
Photo.fastNlMeansDenoising(processed, denoised, 5.0f, 7, 21);
} else {
Photo.fastNlMeansDenoisingColored(processed, denoised, 5.0f, 5.0f, 7, 21)
}
processed.release();
return denoised;
} catch (Exception e) {
System.err.println("❌ Live preprocessing failed: " + e.getMessage());
e.printStackTrace();
if (processed != null) {
processed.release();
}
// Emergency fallback: return clone
return faceROI.clone();
}
}

    private Mat applyHistogramEqualization(Mat colorImage) {
        try {
            // Convert to grayscale
            Mat gray = new Mat();
            Imgproc.cvtColor(colorImage, gray, Imgproc.COLOR_BGR2GRAY);
            // Apply histogram equalization
            Mat equalized = new Mat();
            Imgproc.equalizeHist(gray, equalized);
            gray.release();
            // Convert back to BGR (preserving equalized intensity)
            Mat result = new Mat();
            Imgproc.cvtColor(equalized, result, Imgproc.COLOR_GRAY2BGR);
            equalized.release();
            return result;
        } catch (Exception e) {
            System.err.println("❌ Histogram equalization failed: " + e.getMessage());
            return colorImage.clone();
        }
    }

    public void release() {
        if (faceAligner != null) {
            faceAligner.release();
        }
    }

}