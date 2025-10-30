package service.detection;

import config.AppLogger;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import util.ModuleLoader;

public class FaceRegionProcessor {
    private static final double MIN_FACE_SIZE = 50.0;

    public FaceRegionProcessor() {
        ModuleLoader.ensureOpenCVLoaded();
    }

    public Mat extractFaceROI(Mat frame, Rect face) {
        try {
            // Add padding to preserve natural face aspect ratio instead of forcing square
            Rect captureRegion = buildRectangularRegionWithPadding(frame.size(), face, 0.25);
            return new Mat(frame, captureRegion).clone();
        } catch (Exception e) {
            AppLogger.error("Failed to extract face ROI: " + e.getMessage());
            return null;
        }
    }

    private Rect buildRectangularRegionWithPadding(Size frameSize, Rect face, double paddingRatio) {
        int frameWidth = (int) frameSize.width;
        int frameHeight = (int) frameSize.height;

        if (frameWidth <= 0 || frameHeight <= 0) {
            return face;
        }

        double safePadding = Math.max(0.0, paddingRatio);

        // Calculate padded dimensions while preserving aspect ratio
        int paddedWidth = (int) Math.round(face.width * (1.0 + safePadding));
        int paddedHeight = (int) Math.round(face.height * (1.0 + safePadding));

        // Ensure minimum face size
        paddedWidth = Math.max(paddedWidth, (int) Math.round(MIN_FACE_SIZE));
        paddedHeight = Math.max(paddedHeight, (int) Math.round(MIN_FACE_SIZE));

        // Ensure we don't exceed frame boundaries
        paddedWidth = Math.min(paddedWidth, frameWidth);
        paddedHeight = Math.min(paddedHeight, frameHeight);

        // Center the padded region on the original face
        int centerX = face.x + face.width / 2;
        int centerY = face.y + face.height / 2;

        int x = centerX - paddedWidth / 2;
        int y = centerY - paddedHeight / 2;

        // Adjust if we go outside frame boundaries
        if (x < 0) {
            x = 0;
        }
        if (y < 0) {
            y = 0;
        }
        if (x + paddedWidth > frameWidth) {
            x = frameWidth - paddedWidth;
        }
        if (y + paddedHeight > frameHeight) {
            y = frameHeight - paddedHeight;
        }

        x = Math.max(0, x);
        y = Math.max(0, y);

        return new Rect(x, y, paddedWidth, paddedHeight);
    }
}






