package app.service;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;

/**
 * LiveRecognitionPreprocessor - LIVE RECOGNITION ONLY
 *
 * Handles preprocessing for live face recognition inference.
 * Applies optimal preprocessing for webcam-captured faces.
 *
 * CRITICAL FIXES APPLIED:
 * 1. ✅ NO grayscale conversion - preserves BGR color
 * 2. ✅ Histogram equalization for contrast normalization
 * 3. ✅ Direct resize to 96x96 (no double interpolation)
 * 4. ✅ Stronger denoising (h=5.0) for webcam noise
 * 5. ✅ Face alignment support (OpenCV FacemarkLBF)
 *
 * For TRAINING/ENROLLMENT, use FaceEmbeddingGenerator.preprocessForTraining()
 * instead.
 */
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
// ✅ STEP 1: Ensure BGR color
// Should already be BGR from webcam, but safety check
if (faceROI.channels() != 3) {
System.err.println("⚠️ WARNING: Expected BGR, got " + faceROI.channels());
// Emergency fallback ONLY
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
// ✅ STEP 2: Face alignment (if available)
if (alignmentAvailable &amp;&amp; faceAligner != null) {
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


}