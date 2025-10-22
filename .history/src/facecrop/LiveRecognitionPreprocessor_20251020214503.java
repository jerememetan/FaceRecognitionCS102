package app.facecrop;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;


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
    
 
    public Mat preprocessForLiveRecognition(Mat faceROI) {
        if (faceROI == null || faceROI.empty()) {
            System.err.println("❌ Empty face ROI provided");
            return new Mat();
        }
        
        Mat processed = null;
        try {
     
            if (faceROI.channels() != 3) {
                System.err.println("⚠️ WARNING: Expected BGR, got " + faceROI.channels() + " channels");
              
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