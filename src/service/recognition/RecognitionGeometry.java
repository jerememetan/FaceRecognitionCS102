package service.recognition;

import org.opencv.core.Rect;
import org.opencv.core.Size;
import service.embedding.FaceEmbeddingPreprocessor;

/**
 * Utility helpers for building and clamping regions of interest within a
 * frame.
 */
final class RecognitionGeometry {

    private RecognitionGeometry() {
    }

    static Rect paddedFaceRect(Size frameSize, Rect faceRect, double paddingRatio) {
        return FaceEmbeddingPreprocessor.buildPaddedRect(frameSize, faceRect, paddingRatio);
    }
}
