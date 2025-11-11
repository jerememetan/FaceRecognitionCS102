package service.recognition;

import org.opencv.core.Rect;

import util.ImageProcessor.ImageQualityResult;

/**
 * Captures the characteristics of the face detection for a single frame so
 * downstream components can adjust thresholds based on distance/quality.
 */
final class RecognitionFrameMetrics {

    private final int frameWidth;
    private final int frameHeight;
    private final int detectedWidth;
    private final int detectedHeight;
    private final int paddedWidth;
    private final int paddedHeight;
    private final double qualityScore;
    private final boolean borderlineQuality;

    private RecognitionFrameMetrics(
            int frameWidth,
            int frameHeight,
            int detectedWidth,
            int detectedHeight,
            int paddedWidth,
            int paddedHeight,
            double qualityScore,
            boolean borderlineQuality) {
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.detectedWidth = Math.max(1, detectedWidth);
        this.detectedHeight = Math.max(1, detectedHeight);
        this.paddedWidth = Math.max(1, paddedWidth);
        this.paddedHeight = Math.max(1, paddedHeight);
        this.qualityScore = qualityScore;
        this.borderlineQuality = borderlineQuality;
    }

    static RecognitionFrameMetrics from(
            int frameWidth,
            int frameHeight,
            Rect detectedRect,
            Rect paddedRect,
            ImageQualityResult qualityResult) {
        double qualityScore = qualityResult != null ? qualityResult.getQualityScore() : 0.0;
        boolean borderline = qualityResult != null && qualityResult.isBorderline();
        return new RecognitionFrameMetrics(
                frameWidth,
                frameHeight,
                detectedRect != null ? detectedRect.width : 0,
                detectedRect != null ? detectedRect.height : 0,
                paddedRect != null ? paddedRect.width : 0,
                paddedRect != null ? paddedRect.height : 0,
                qualityScore,
                borderline);
    }

    int frameWidth() {
        return frameWidth;
    }

    int frameHeight() {
        return frameHeight;
    }

    int detectedWidth() {
        return detectedWidth;
    }

    int detectedHeight() {
        return detectedHeight;
    }

    int paddedWidth() {
        return paddedWidth;
    }

    int paddedHeight() {
        return paddedHeight;
    }

    double qualityScore() {
        return qualityScore;
    }

    boolean borderlineQuality() {
        return borderlineQuality;
    }

    double detectionAreaRatio() {
        double frameArea = Math.max(1.0, (double) frameWidth * frameHeight);
        double detectionArea = (double) detectedWidth * detectedHeight;
        return detectionArea / frameArea;
    }

    double paddedAreaRatio() {
        double frameArea = Math.max(1.0, (double) frameWidth * frameHeight);
        double roiArea = (double) paddedWidth * paddedHeight;
        return roiArea / frameArea;
    }

    double normalizedScale(double baselineWidth) {
        double safeBaseline = Math.max(32.0, baselineWidth);
        return detectedWidth / safeBaseline;
    }

    double aspectRatio() {
        return detectedWidth / (double) detectedHeight;
    }
}
