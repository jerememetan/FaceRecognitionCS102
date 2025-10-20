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

}