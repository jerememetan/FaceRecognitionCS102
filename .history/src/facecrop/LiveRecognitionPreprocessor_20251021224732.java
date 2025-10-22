package facecrop;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;
import java.util.ArrayList;
import java.util.List;
import app.util.FaceAligner;

public class LiveRecognitionPreprocessor {

    private static final Size INPUT_SIZE = new Size(96, 96);
    private FaceAligner aligner;

    public LiveRecognitionPreprocessor() {
        this.aligner = new FaceAligner();
    }

    public Mat preprocessForLiveRecognition(Mat faceROI, Rect faceRect) {
        if (faceROI == null || faceROI.empty()) {
            System.err.println("Empty face ROI provided");
            return new Mat();
        }

        try {
            // Ensure 3-channel BGR
            Mat processed;
            if (faceROI.channels() != 3) {
                Mat temp = new Mat();
                if (faceROI.channels() == 1) {
                    Imgproc.cvtColor(faceROI, temp, Imgproc.COLOR_GRAY2BGR);
                } else {
                    temp = faceROI.clone();
                }
                processed = temp;
            } else {
                processed = faceROI.clone();
            }

            // *** CRITICAL: Apply face alignment ***
            Mat aligned = aligner.align(processed, faceRect);
            processed.release();

            if (aligned == null || aligned.empty()) {
                System.err.println("Alignment failed in live preprocessing");
                Mat fallback = new Mat();
                Imgproc.resize(faceROI, fallback, INPUT_SIZE, 0, 0, Imgproc.INTER_CUBIC);
                return fallback;
            }

            // Already 96x96 from aligner
            // No additional preprocessing - keep it simple
            return aligned;

        } catch (Exception e) {
            System.err.println("Live preprocessing failed: " + e.getMessage());
            e.printStackTrace();
            Mat fallback = new Mat();
            Imgproc.resize(faceROI, fallback, INPUT_SIZE, 0, 0, Imgproc.INTER_CUBIC);
            return fallback;
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
