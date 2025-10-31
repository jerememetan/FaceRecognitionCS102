package service.recognition;

import config.AppConfig;
import org.opencv.core.Rect;
import org.opencv.core.Size;

/**
 * Utility helpers for building and clamping regions of interest within a
 * frame.
 */
final class RecognitionGeometry {

    private RecognitionGeometry() {
    }

    static Rect paddedFaceRect(Size frameSize, Rect faceRect, double paddingRatio) {
        if (frameSize == null || faceRect == null) {
            return faceRect;
        }

        int frameWidth = (int) frameSize.width;
        int frameHeight = (int) frameSize.height;

        if (frameWidth <= 0 || frameHeight <= 0) {
            return faceRect;
        }

        double safePadding = Math.max(0.0, paddingRatio);
        int paddedWidth = (int) Math.round(faceRect.width * (1.0 + safePadding));
        int paddedHeight = (int) Math.round(faceRect.height * (1.0 + safePadding));

        int minWidth = Math.max(1, AppConfig.getInstance().getRecognitionMinFaceWidthPx());
        paddedWidth = Math.max(paddedWidth, minWidth);
        paddedHeight = Math.max(paddedHeight, minWidth);

        paddedWidth = Math.min(paddedWidth, frameWidth);
        paddedHeight = Math.min(paddedHeight, frameHeight);

        int centerX = faceRect.x + faceRect.width / 2;
        int centerY = faceRect.y + faceRect.height / 2;

        int x = centerX - paddedWidth / 2;
        int y = centerY - paddedHeight / 2;

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







