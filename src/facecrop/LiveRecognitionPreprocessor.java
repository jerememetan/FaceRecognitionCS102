package facecrop;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.dnn.Dnn;
import app.util.FaceAligner;

public class LiveRecognitionPreprocessor {

    private static final Size INPUT_SIZE = new Size(112, 112); // ✅ CHANGED for ArcFace
    private FaceAligner aligner;
    private static int debugCounter = 0;

    public LiveRecognitionPreprocessor() {
        this.aligner = new FaceAligner();
    }

    /**
     * ✅ FIXED: Now matches EXACTLY the training preprocessing pipeline!
     */
    public Mat preprocessForLiveRecognition(Mat faceROI, Rect faceRect) {
        // === STAGE 1: Face Detection & Preprocessing ===
        System.out.println("=== STAGE 1: Face Detection & Preprocessing ===");
        System.out.println("Original frame resolution: " + (faceROI != null ? faceROI.size() : "null"));
        System.out.println("Face ROI size: " + (faceROI != null ? faceROI.size() : "null"));
        if (faceROI != null && !faceROI.empty()) {
            System.out.println("Face ROI mean pixel value (BGR): " + Core.mean(faceROI));
            // Calculate std deviation
            MatOfDouble mean = new MatOfDouble();
            MatOfDouble stddev = new MatOfDouble();
            Core.meanStdDev(faceROI, mean, stddev);
            System.out.println("Face ROI std deviation: " + stddev.get(0, 0)[0]);
        }

        if (faceROI == null || faceROI.empty()) {
            System.err.println("❌ Empty face ROI provided");
            return new Mat();
        }

        // IMPORTANT: keep the mean in pixel space. OpenCV subtracts the mean before
        // scaling, so this results in (pixel - 127.5) / 128.0 as required by ArcFace.
        Scalar arcFaceMean = new Scalar(127.5, 127.5, 127.5);

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
            Mat aligned = aligner.align(processed, null); // ROI already isolated; use internal fallbacks

            if (aligned == null || aligned.empty()) {
                System.err.println("⚠️ Alignment failed in live preprocessing, resizing ROI directly");
                aligned = new Mat();
                Imgproc.resize(processed, aligned, INPUT_SIZE, 0, 0, Imgproc.INTER_CUBIC);
            }

            processed.release();

            Mat blob = Dnn.blobFromImage(aligned, 1.0 / 128.0, INPUT_SIZE,
                    arcFaceMean, true, false);
            aligned.release();

            // ✅ Return the properly formatted blob, NOT raw pixels!
            return blob;

        } catch (Exception e) {
            System.err.println("❌ Live preprocessing failed: " + e.getMessage());
            e.printStackTrace();

            // Fallback with proper preprocessing
            Mat fallback = new Mat();
            Imgproc.resize(faceROI, fallback, INPUT_SIZE, 0, 0, Imgproc.INTER_CUBIC);

            Mat blob = Dnn.blobFromImage(fallback, 1.0 / 128.0, INPUT_SIZE,
                    arcFaceMean, true, false);
            fallback.release();

            return blob;
        }
    }

    public void release() {
        if (aligner != null) {
            aligner.release();
        }
    }
}
