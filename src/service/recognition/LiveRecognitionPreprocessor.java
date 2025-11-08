package service.recognition;

import config.AppLogger;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import service.embedding.FaceEmbeddingPreprocessor;

/**
 * Performs the same preprocessing pipeline used for ArcFace training so that
 * live recognition inputs are compatible with the ONNX model expectations.
 */
public class LiveRecognitionPreprocessor {
    private final FaceEmbeddingPreprocessor preprocessor;

    public LiveRecognitionPreprocessor() {
        this.preprocessor = new FaceEmbeddingPreprocessor();
    }

    /**
     * Runs the full preprocessing pipeline required by the ArcFace model.
     *
     * @param faceROI  the cropped face region in BGR color space
     * @param faceRect rectangle describing the face location (used only for
     *                 logging)
     * @return a blob ready to be fed to the ArcFace ONNX model
     */
    public Mat preprocessForLiveRecognition(Mat faceROI, Rect faceRect) {
        AppLogger.info("=== STAGE 1: Face Detection & Preprocessing ===");
        AppLogger.info("Face ROI size: " + (faceROI != null ? faceROI.size() : "null"));
        return preprocessor.preprocessForEmbedding(faceROI);
    }

    public void release() {
        preprocessor.release();
    }
}
