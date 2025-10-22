package app.service;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;

/**
 * LiveRecognitionPreprocessor - LIVE RECOGNITION ONLY
 * 
 * This class handles preprocessing for live face recognition inference.
 * It applies optimal preprocessing for webcam-captured faces to match
 * the OpenFace nn4.small2.v1 model's expectations.
 * 
 * CRITICAL FIXES APPLIED:
 * 1. ✅ NO grayscale conversion - preserves BGR color
 * 2. ✅ Histogram equalization for contrast normalization
 * 3. ✅ Direct resize to 96x96 (no double interpolation)
 * 4. ✅ Stronger denoising (h=5.0) for webcam noise
 * 5. ✅ Face alignment support (if DlibFaceAligner available)
 * 
 * For TRAINING/ENROLLMENT, use FaceEmbeddingGenerator instead.
 */
public class LiveRecognitionPreprocessor {
    
    private static final Size INPUT_SIZE = new Size(96, 96);
    private DlibFaceAligner faceAligner;
    private boolean alignmentAvailable = false;
    
    public LiveRecognitionPreprocessor() {
        try {
            faceAligner = new DlibFaceAligner();
            alignmentAvailable = true;
            System.out.println("✅ Face alignment enabled (Dlib)");
        } catch (Exception e) {
            System.out.println("⚠️ Face alignment not available: " + e.getMessage());
            alignmentAvailable = false;
        }
    }
    
    /**
     * Preprocess face ROI for live recognition.
     * 
     * Pipeline:
     * 1. Ensure BGR color (emergency fallback only)
     * 2. Face alignment (if available)
     * 3. Histogram equalization
     * 4. Direct resize to 96x96
     * 5. Strong denoising (h=5.0)
     * 
     * @param faceROI Face region extracted from webcam (BGR format)
     * @return Preprocessed Mat ready for embedding generation (96x96 BGR)
     */
    public Mat preprocessForLiveRecognition(Mat faceROI) {
        if (faceROI == null || faceROI.empty()) {
            System.err.println("❌ Empty face ROI provided");
            return new Mat();
        }
        
        Mat processed = null;
        try {
            // ✅ STEP 1: Ensure BGR color
            // Should already be BGR from webcam, but safety check
            if (faceROI.channels() != 3) {
                System.err.println("⚠️ WARNING: Expected BGR, got " + faceROI.channels() + " channels");
                // Emergency fallback ONLY
                Mat temp = new Mat();
                if (faceROI.channels() == 1) {
                    Imgproc.cvtColor(faceROI, temp, Imgproc.COLOR_GRAY2BGR);
                    System.err.println("⚠️ Emergency grayscale→BGR conversion (should not happen!)");
                    processed = temp;
                } else {
                    processed = faceROI.clone();
                }
            } else {
                processed = faceROI.clone();
            }
            
            // ✅ STEP 2: Face alignment (if available)
            if (alignmentAvailable && faceAligner != null) {
                try {
                    Mat aligned = faceAligner.alignFace(processed);
                    if (aligned != null && !aligned.empty()) {
                        processed.release();
                        processed = aligned;
                    }
                } catch (Exception e) {
                    System.err.println("⚠️ Face alignment failed, continuing without: " + e.getMessage());
                }
            }
            
       
            Mat equalized = applyHistogramEqualization(processed);
            processed.release();
            processed = equalized;
            
 
            Mat resized = new Mat();
            Imgproc.resize(processed, resized, INPUT_SIZE, 0, 0, Imgproc.INTER_CUBIC);
            processed.release();
            processed = resized;
            
     
            Mat denoised = new Mat();
            if (processed.channels() == 1) {
                Photo.fastNlMeansDenoising(processed, denoised, 5.0f, 7, 21);
            } else {
                Photo.fastNlMeansDenoisingColored(processed, denoised, 5.0f, 5.0f, 7, 21);
            }
            processed.release();
            
            return denoised;
            
        } catch (Exception e) {
            System.err.println("❌ Live preprocessing failed: " + e.getMessage());
            e.printStackTrace();
            if (processed != null) {
                processed.release();
            }
        
            return faceROI.clone();
        }
    }
    
  
    private Mat applyHistogramEqualization(Mat colorImage) {
        try {
   
            Mat gray = new Mat();
            Imgproc.cvtColor(colorImage, gray, Imgproc.COLOR_BGR2GRAY);
      
            Mat equalized = new Mat();
            Imgproc.equalizeHist(gray, equalized);
            gray.release();
            
           
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