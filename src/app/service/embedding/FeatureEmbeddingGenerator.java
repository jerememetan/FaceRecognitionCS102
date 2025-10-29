package app.service.embedding;

import ConfigurationAndLogging.AppConfig;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import java.util.Arrays;

/**
 * Provides the hand-crafted feature embedding fallback when deep learning model is unavailable.
 */
public class FeatureEmbeddingGenerator {

    private final int embeddingSize;
    private final boolean debugLogs;

    public FeatureEmbeddingGenerator(boolean debugLogs) {
        this.embeddingSize = AppConfig.getInstance().getEmbeddingSize();
        this.debugLogs = debugLogs;
    }

    public byte[] generate(Mat faceImage) {
        if (faceImage == null || faceImage.empty()) {
            return null;
        }
        try {
            double[] features = new double[embeddingSize];

            Mat resized = new Mat();
            Imgproc.resize(faceImage, resized, new Size(64, 64));

            Mat gray = new Mat();
            if (resized.channels() > 1) {
                Imgproc.cvtColor(resized, gray, Imgproc.COLOR_BGR2GRAY);
            } else {
                gray = resized.clone();
            }

            extractHistogramFeatures(gray, features, 0);
            extractTextureFeatures(gray, features, 32);
            extractGeometricFeatures(gray, features, 64);
            extractGradientFeatures(gray, features, 96);

            gray.release();
            resized.release();

            byte[] result = normalizeAndConvert(features);
            debugEmbedding(result, "Feature-based embedding");
            return result;
        } catch (Exception e) {
            System.err.println("❌ Feature-based embedding generation failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private byte[] normalizeAndConvert(double[] features) {
        double norm = EmbeddingVectorUtils.magnitude(features);
        norm = Math.max(norm, 1e-12);
        for (int i = 0; i < features.length; i++) {
            features[i] /= norm;
        }
        if (debugLogs) {
            System.out.println("DEBUG After norm in doubleArrayToByteArray: Magnitude = " + EmbeddingVectorUtils.magnitude(features));
        }
        return EmbeddingVectorUtils.doublesToBytes(features);
    }

    private void extractHistogramFeatures(Mat image, double[] features, int offset) {
        Mat hist = new Mat();
        MatOfInt channels = new MatOfInt(0);
        MatOfInt histSize = new MatOfInt(32);
        MatOfFloat ranges = new MatOfFloat(0f, 256f);

        Imgproc.calcHist(Arrays.asList(image), channels, new Mat(), hist, histSize, ranges);
        Core.normalize(hist, hist, 0, 1, Core.NORM_MINMAX);

        for (int i = 0; i < 32 && i < hist.rows(); i++) {
            features[offset + i] = hist.get(i, 0)[0];
        }

        hist.release();
        channels.release();
        histSize.release();
        ranges.release();
    }

    private void extractTextureFeatures(Mat image, double[] features, int offset) {
        Mat gradX = new Mat();
        Mat gradY = new Mat();
        Imgproc.Sobel(image, gradX, CvType.CV_64F, 1, 0, 3);
        Imgproc.Sobel(image, gradY, CvType.CV_64F, 0, 1, 3);

        int regions = 4;
        int regionWidth = image.width() / 2;
        int regionHeight = image.height() / 2;

        for (int i = 0; i < regions && (offset + i * 8) < features.length; i++) {
            int x = (i % 2) * regionWidth;
            int y = (i / 2) * regionHeight;
            Rect roi = new Rect(x, y, regionWidth, regionHeight);

            Mat regionGradX = new Mat(gradX, roi);
            Mat regionGradY = new Mat(gradY, roi);

            MatOfDouble meanX = new MatOfDouble();
            MatOfDouble stdX = new MatOfDouble();
            Core.meanStdDev(regionGradX, meanX, stdX);

            MatOfDouble meanY = new MatOfDouble();
            MatOfDouble stdY = new MatOfDouble();
            Core.meanStdDev(regionGradY, meanY, stdY);

            int baseIdx = offset + i * 8;
            if (baseIdx + 3 < features.length) {
                features[baseIdx] = meanX.toArray()[0] / 255.0;
                features[baseIdx + 1] = stdX.toArray()[0] / 255.0;
                features[baseIdx + 2] = meanY.toArray()[0] / 255.0;
                features[baseIdx + 3] = stdY.toArray()[0] / 255.0;
            }

            meanX.release();
            stdX.release();
            meanY.release();
            stdY.release();
        }

        gradX.release();
        gradY.release();
    }

    private void extractGeometricFeatures(Mat image, double[] features, int offset) {
        Moments moments = Imgproc.moments(image);

        if (offset + 31 < features.length && moments.m00 != 0) {
            features[offset] = moments.m00 / (image.width() * image.height());
            features[offset + 1] = moments.m10 / moments.m00;
            features[offset + 2] = moments.m01 / moments.m00;

            Mat huMoments = new Mat();
            Imgproc.HuMoments(moments, huMoments);

            for (int i = 0; i < Math.min(7, huMoments.rows()) && (offset + 3 + i) < features.length; i++) {
                double hu = huMoments.get(i, 0)[0];
                features[offset + 3 + i] = Math.log(Math.abs(hu) + 1e-10);
            }

            huMoments.release();
        }
    }

    private void extractGradientFeatures(Mat image, double[] features, int offset) {
        Mat gradX = new Mat();
        Mat gradY = new Mat();
        Imgproc.Sobel(image, gradX, CvType.CV_64F, 1, 0, 3);
        Imgproc.Sobel(image, gradY, CvType.CV_64F, 0, 1, 3);

        Mat magnitude = new Mat();
        Mat angle = new Mat();
        Core.cartToPolar(gradX, gradY, magnitude, angle, true);

        int numBins = 16;
        double[] hogFeatures = new double[numBins];

        for (int y = 0; y < angle.rows(); y++) {
            for (int x = 0; x < angle.cols(); x++) {
                double ang = angle.get(y, x)[0];
                double mag = magnitude.get(y, x)[0];
                int bin = (int) (ang / (360.0 / numBins)) % numBins;
                hogFeatures[bin] += mag;
            }
        }

        double sum = Arrays.stream(hogFeatures).sum();
        if (sum > 0) {
            for (int i = 0; i < numBins && (offset + i) < features.length; i++) {
                features[offset + i] = hogFeatures[i] / sum;
            }
        }

        gradX.release();
        gradY.release();
        magnitude.release();
        angle.release();
    }

    private void debugEmbedding(byte[] embedding, String label) {
        if (!debugLogs) {
            return;
        }
        if (embedding == null) {
            System.err.println("DEBUG: " + label + " is NULL!");
            return;
        }
        double[] doubleEmb = EmbeddingVectorUtils.toDoubleArray(embedding);
        if (doubleEmb == null) {
            System.err.println("DEBUG: " + label + " decoding failed");
            return;
        }
        System.out.print("DEBUG " + label + " first 10 values: ");
        for (int i = 0; i < Math.min(10, doubleEmb.length); i++) {
            System.out.print(String.format("%.6f ", doubleEmb[i]));
        }
        System.out.println();
        System.out.println("| Magnitude: " + String.format("%.6f", EmbeddingVectorUtils.magnitude(doubleEmb)));
    }
}
