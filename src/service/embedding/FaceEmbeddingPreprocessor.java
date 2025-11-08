package service.embedding;

import config.AppConfig;
import config.AppLogger;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.imgproc.Imgproc;
import util.FaceAligner;
import util.ModuleLoader;

/**
 * Shared preprocessing utilities used when preparing face crops for ArcFace
 * embedding generation. This class consolidates the padding, alignment, size
 * normalization, and blob conversion logic so detection and recognition flows
 * remain consistent.
 */
public class FaceEmbeddingPreprocessor {

    private static final Size INPUT_SIZE = new Size(112, 112);
    private static final Scalar ARC_FACE_MEAN = new Scalar(127.5, 127.5, 127.5);

    private final FaceAligner aligner;

    public FaceEmbeddingPreprocessor() {
        ModuleLoader.ensureOpenCVLoaded();
        this.aligner = new FaceAligner();
    }

    /**
     * Computes a padded rectangle centred on the detected face while clamping to
     * the frame boundaries and enforcing a minimum size configured in
     * {@link AppConfig}.
     */
    public static Rect buildPaddedRect(Size frameSize, Rect faceRect, double paddingRatio) {
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

    /**
     * Extracts a padded face crop from the frame and returns a cloned Mat so the
     * caller can safely dispose the original frame.
     */
    public Mat extractPaddedFace(Mat frame, Rect faceRect, double paddingRatio) {
        Rect padded = buildPaddedRect(frame.size(), faceRect, paddingRatio);
        return new Mat(frame, padded).clone();
    }

    /**
     * Applies ArcFace-compatible preprocessing to a face ROI, returning a blob
     * suitable for {@link FaceEmbeddingGenerator#generateEmbeddingFromBlob(Mat)}.
     */
    public Mat preprocessForEmbedding(Mat faceROI) {
        if (faceROI == null || faceROI.empty()) {
            AppLogger.warn("FaceEmbeddingPreprocessor received empty ROI");
            return new Mat();
        }

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
                AppLogger.warn("Face alignment failed; falling back to simple resize");
                aligned = new Mat();
                Imgproc.resize(processed, aligned, INPUT_SIZE, 0, 0, Imgproc.INTER_CUBIC);
            }

            processed.release();

            Mat blob = Dnn.blobFromImage(aligned, 1.0 / 128.0, INPUT_SIZE,
                    ARC_FACE_MEAN, true, false);
            aligned.release();
            return blob;
        } catch (Exception ex) {
            AppLogger.error("ArcFace preprocessing failed: " + ex.getMessage());
            Mat fallback = new Mat();
            Imgproc.resize(faceROI, fallback, INPUT_SIZE, 0, 0, Imgproc.INTER_CUBIC);
            Mat blob = Dnn.blobFromImage(fallback, 1.0 / 128.0, INPUT_SIZE,
                    ARC_FACE_MEAN, true, false);
            fallback.release();
            return blob;
        }
    }

    /**
     * Convenience that extracts a padded face and converts it straight into a
     * blob ready for embedding generation.
     */
    public Mat preprocessFromFrame(Mat frame, Rect faceRect, double paddingRatio) {
        Mat roi = extractPaddedFace(frame, faceRect, paddingRatio);
        try {
            return preprocessForEmbedding(roi);
        } finally {
            roi.release();
        }
    }

    public void release() {
        if (aligner != null) {
            aligner.release();
        }
    }
}
