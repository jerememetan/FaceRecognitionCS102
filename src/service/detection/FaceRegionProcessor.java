package service.detection;

import config.AppLogger;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import service.embedding.FaceEmbeddingPreprocessor;
import util.ModuleLoader;

public class FaceRegionProcessor {
    public FaceRegionProcessor() {
        ModuleLoader.ensureOpenCVLoaded();
    }

    public Mat extractFaceROI(Mat frame, Rect face) {
        try {
            // Add padding to preserve natural face aspect ratio instead of forcing square
            Rect captureRegion = FaceEmbeddingPreprocessor.buildPaddedRect(frame.size(), face, 0.15);
            return new Mat(frame, captureRegion).clone();
        } catch (Exception e) {
            AppLogger.error("Failed to extract face ROI: " + e.getMessage());
            return null;
        }
    }
}
