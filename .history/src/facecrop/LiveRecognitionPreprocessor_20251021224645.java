package facecrop;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;
import java.util.ArrayList;
import java.util.List;
import app.util.FaceAligner;

public class LiveRecognitionPreprocessor {

    private static final Size INPUT_SIZE = new Size(96, 96);

    public LiveRecognitionPreprocessor() {
        // No initialization needed
    }

    public Mat preprocessForLiveRecognition(Mat faceROI, Rect faceRect) {
        if (faceROI == null || faceROI.empty()) {
            System.err.println("❌ Empty face ROI provided");
            return new Mat();
        }

        Mat processed = null;
        try {
            // ✅ STEP 1: Ensure 3-channel BGR
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

            // ✅ STEP 2: Apply CLAHE per-channel (preserves color!)
            Mat enhanced = applyColorCLAHE(processed);
            processed.release();
            processed = enhanced;

            // ✅ STEP 3: Direct resize to 96x96
            Mat resized = new Mat();
            Imgproc.resize(processed, resized, INPUT_SIZE, 0, 0, Imgproc.INTER_CUBIC);
            processed.release();
            processed = resized;

            // ✅ STEP 4: Strong denoising for webcam noise
            Mat denoised = new Mat();
            Photo.fastNlMeansDenoisingColored(processed, denoised, 5.0f, 5.0f, 7, 21);
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


    private Mat applyColorCLAHE(Mat colorImage) {
        try {
            // Split into B, G, R channels
            List<Mat> channels = new ArrayList<>();
            Core.split(colorImage, channels);

            // Create CLAHE with reduced clip limit to avoid over-enhancement
            org.opencv.imgproc.CLAHE clahe = Imgproc.createCLAHE(1.0, new Size(8, 8));

            // Apply CLAHE to each channel independently
            for (int i = 0; i < channels.size(); i++) {
                Mat enhanced = new Mat();
                clahe.apply(channels.get(i), enhanced);
                channels.get(i).release();
                channels.set(i, enhanced);
            }

            // Merge channels back
            Mat result = new Mat();
            Core.merge(channels, result);

            // Release channel mats
            for (Mat ch : channels) {
                ch.release();
            }

            return result;

        } catch (Exception e) {
            System.err.println("❌ CLAHE failed: " + e.getMessage());
            return colorImage.clone();
        }
    }

    public void release() {
        // No resources to release
    }
}
