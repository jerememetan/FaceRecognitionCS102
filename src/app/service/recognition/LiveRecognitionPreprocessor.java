package app.service.recognition;

import app.util.FaceAligner;
import app.util.ModuleLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.imgproc.Imgproc;

/**
 * Performs the same preprocessing pipeline used for ArcFace training so that
 * live recognition inputs are compatible with the ONNX model expectations.
 */
public class LiveRecognitionPreprocessor {

    private static final Size INPUT_SIZE = new Size(112, 112);

    private final FaceAligner aligner;

    public LiveRecognitionPreprocessor() {
        ModuleLoader.ensureOpenCVLoaded();
        this.aligner = new FaceAligner();
    }

    /**
     * Runs the full preprocessing pipeline required by the ArcFace model.
     *
     * @param faceROI  the cropped face region in BGR color space
     * @param faceRect rectangle describing the face location (used only for logging)
     * @return a blob ready to be fed to the ArcFace ONNX model
     */
    public Mat preprocessForLiveRecognition(Mat faceROI, Rect faceRect) {
        System.out.println("=== STAGE 1: Face Detection & Preprocessing ===");
        System.out.println("Original frame resolution: " + (faceROI != null ? faceROI.size() : "null"));
        System.out.println("Face ROI size: " + (faceROI != null ? faceROI.size() : "null"));
        if (faceROI != null && !faceROI.empty()) {
            System.out.println("Face ROI mean pixel value (BGR): " + Core.mean(faceROI));
            MatOfDouble mean = new MatOfDouble();
            MatOfDouble stddev = new MatOfDouble();
            Core.meanStdDev(faceROI, mean, stddev);
            System.out.println("Face ROI std deviation: " + stddev.get(0, 0)[0]);
        }

        if (faceROI == null || faceROI.empty()) {
            System.err.println("❌ Empty face ROI provided");
            return new Mat();
        }

        Scalar arcFaceMean = new Scalar(127.5, 127.5, 127.5);

        try {
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

            Mat aligned = aligner.align(processed, null);
            if (aligned == null || aligned.empty()) {
                System.err.println("⚠️ Alignment failed in live preprocessing, resizing ROI directly");
                aligned = new Mat();
                Imgproc.resize(processed, aligned, INPUT_SIZE, 0, 0, Imgproc.INTER_CUBIC);
            }

            processed.release();

            Mat blob = Dnn.blobFromImage(aligned, 1.0 / 128.0, INPUT_SIZE,
                    arcFaceMean, true, false);
            aligned.release();
            return blob;

        } catch (Exception e) {
            System.err.println("❌ Live preprocessing failed: " + e.getMessage());
            e.printStackTrace();

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
