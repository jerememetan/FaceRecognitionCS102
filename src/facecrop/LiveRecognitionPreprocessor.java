package facecrop;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.dnn.Dnn;
import app.util.FaceAligner;

public class LiveRecognitionPreprocessor {

    private static final Size INPUT_SIZE = new Size(96, 96);
    private FaceAligner aligner;

    public LiveRecognitionPreprocessor() {
        this.aligner = new FaceAligner();
    }

    /**
     * ✅ FIXED: Now matches EXACTLY the training preprocessing pipeline!
     */
    public Mat preprocessForLiveRecognition(Mat faceROI, Rect faceRect) {
        if (faceROI == null || faceROI.empty()) {
            System.err.println("❌ Empty face ROI provided");
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

            // *** Apply face alignment ***
            Mat aligned = aligner.align(processed, faceRect);

            if (aligned == null || aligned.empty()) {
                System.err.println("⚠️ Alignment failed in live preprocessing, using fallback");
                aligned = new Mat();

                // Create proper face ROI fallback
                Mat faceROIForResize;
                if (faceRect != null && faceRect.width > 0 && faceRect.height > 0) {
                    Rect safeRect = new Rect(
                        Math.max(0, faceRect.x),
                        Math.max(0, faceRect.y),
                        Math.min(faceRect.width, processed.width() - Math.max(0, faceRect.x)),
                        Math.min(faceRect.height, processed.height() - Math.max(0, faceRect.y))
                    );
                    faceROIForResize = new Mat(processed, safeRect);
                } else {
                    faceROIForResize = processed.clone();
                }

                Imgproc.resize(faceROIForResize, aligned, INPUT_SIZE, 0, 0, Imgproc.INTER_CUBIC);
                faceROIForResize.release();
            }

            processed.release();

            // ✅ CRITICAL FIX: Apply EXACT same preprocessing as training!
            // Normalize to [0, 1] range - convert to float
            Mat normalized = new Mat();
            aligned.convertTo(normalized, CvType.CV_32F, 1.0 / 255.0);
            aligned.release();

            // ✅ CRITICAL FIX: Create blob EXACTLY like training
            // swapRB=true converts BGR to RGB (OpenFace expects RGB)
            Mat blob = Dnn.blobFromImage(normalized, 1.0, INPUT_SIZE,
                    new Scalar(0, 0, 0), true, false);
            normalized.release();

            // ✅ Return the properly formatted blob, NOT raw pixels!
            return blob;

        } catch (Exception e) {
            System.err.println("❌ Live preprocessing failed: " + e.getMessage());
            e.printStackTrace();

            // Fallback with proper preprocessing
            Mat fallback = new Mat();
            Imgproc.resize(faceROI, fallback, INPUT_SIZE, 0, 0, Imgproc.INTER_CUBIC);

            Mat normalized = new Mat();
            fallback.convertTo(normalized, CvType.CV_32F, 1.0 / 255.0);
            fallback.release();

            Mat blob = Dnn.blobFromImage(normalized, 1.0, INPUT_SIZE,
                    new Scalar(0, 0, 0), true, false);
            normalized.release();

            return blob;
        }
    }

    public void release() {
        if (aligner != null) {
            aligner.release();
        }
    }
}
