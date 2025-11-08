package service.embedding;

import config.AppConfig;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;
import util.FaceAligner;
import util.ModuleLoader;
import config.*;

/**
 * Handles deep-learning based embedding generation using ArcFace.
 */
public class DeepEmbeddingGenerator {

    private final int embeddingSize;
    private final Size inputSize;
    private final boolean debugLogs;

    private Net embeddingNet;
    private boolean initialized;
    private final FaceAligner aligner;

    public DeepEmbeddingGenerator(boolean debugLogs) {
        this.debugLogs = debugLogs;
        AppConfig config = AppConfig.getInstance();
        this.embeddingSize = config.getEmbeddingSize();
        this.inputSize = new Size(config.getEmbeddingInputSize(), config.getEmbeddingInputSize());
        ModuleLoader.ensureOpenCVLoaded();
        this.aligner = new FaceAligner();
        initializeEmbeddingNet();
    }

    private void initializeEmbeddingNet() {
        try {
            String modelPath = AppConfig.getInstance().getEmbeddingModelPath();
            if (new java.io.File(modelPath).exists()) {
                embeddingNet = Dnn.readNetFromONNX(modelPath);
                initialized = true;
                AppLogger.info("✅ ArcFace ResNet100 model loaded successfully");
            } else {
                AppLogger.info("⚠️ ArcFace model not found, using feature-based embeddings");
                initialized = false;
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to load face embedding model: " + e.getMessage());
            initialized = false;
        }
    }

    public boolean isAvailable() {
        return initialized;
    }

    public byte[] generate(Mat faceImage) {
        if (!initialized || faceImage == null || faceImage.empty()) {
            return null;
        }
        try {
            Mat processed = ensureThreeChannels(faceImage);
            Mat aligned = aligner.align(processed, (Rect) null);
            if (aligned == null || aligned.empty()) {
                System.err.println("⚠️ Alignment failed, using processed face directly");
                aligned = processed.clone();
            }
            processed.release();

            logAlignedStats(aligned);

            Mat blobNHWC = createAlignedBlob(aligned);
            aligned.release();

            embeddingNet.setInput(blobNHWC);
            Mat embedding = embeddingNet.forward();
            blobNHWC.release();

            logEmbeddingStats(embedding);

            byte[] result = matToByteArray(embedding);
            embedding.release();
            debugEmbedding(result, "Generated embedding");
            return result;
        } catch (Exception e) {
            System.err.println("Deep embedding generation failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public byte[] generateFromBlob(Mat preprocessedBlob) {
        if (!initialized || preprocessedBlob == null || preprocessedBlob.empty()) {
            return null;
        }
        try {
            printBlobStats(preprocessedBlob, "External blob input (NCHW)");

            Mat convertedBlob = new Mat();
            MatOfInt nchwToNhwc = new MatOfInt(0, 2, 3, 1);
            Core.transposeND(preprocessedBlob, nchwToNhwc, convertedBlob);
            nchwToNhwc.release();
            preprocessedBlob.release();

            embeddingNet.setInput(convertedBlob);
            Mat embedding = embeddingNet.forward();
            convertedBlob.release();

            byte[] result = matToByteArray(embedding);
            embedding.release();
            debugEmbedding(result, "Generated embedding from blob");
            return result;
        } catch (Exception e) {
            System.err.println("Deep embedding generation from blob failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public void release() {
        aligner.release();
        embeddingNet = null;
    }

    private Mat ensureThreeChannels(Mat faceImage) {
        if (faceImage.channels() == 3) {
            return faceImage.clone();
        }
        Mat temp = new Mat();
        if (faceImage.channels() == 1) {
            Imgproc.cvtColor(faceImage, temp, Imgproc.COLOR_GRAY2BGR);
        } else {
            temp = faceImage.clone();
        }
        return temp;
    }

    private Mat createAlignedBlob(Mat aligned) {
        Scalar arcFaceMean = new Scalar(127.5, 127.5, 127.5);
        Mat blobNCHW = Dnn.blobFromImage(aligned, 1.0 / 128.0, inputSize, arcFaceMean, true, false);
        printBlobStats(blobNCHW, "Aligned face blob (NCHW)");

        Mat blobNHWC = new Mat();
        MatOfInt nchwToNhwc = new MatOfInt(0, 2, 3, 1);
        Core.transposeND(blobNCHW, nchwToNhwc, blobNHWC);
        nchwToNhwc.release();
        blobNCHW.release();
        return blobNHWC;
    }

    private byte[] matToByteArray(Mat mat) {
        float[] floatArray = new float[embeddingSize];
        mat.get(0, 0, floatArray);

        if (debugLogs) {
            AppLogger.info("=== STAGE 4: L2 Normalization ===");
            AppLogger.info("Before normalization:");
            double[] floatArrayDouble = new double[floatArray.length];
            for (int i = 0; i < floatArray.length; i++) {
                floatArrayDouble[i] = floatArray[i];
            }
            AppLogger.info("  Magnitude: " + EmbeddingVectorUtils.magnitude(floatArrayDouble));
            double[] first10Before = getFirstN(floatArrayDouble, 10);
            AppLogger.info("  First 10 values: " + java.util.Arrays.toString(first10Before));
        }

        for (float f : floatArray) {
            if (Float.isNaN(f) || Float.isInfinite(f)) {
                System.err.println("❌ Neural network produced invalid output (NaN/Inf detected)");
                return null;
            }
        }

        double norm = EmbeddingVectorUtils.magnitude(floatArray);
        norm = Math.max(norm, 1e-12);

        for (int i = 0; i < floatArray.length; i++) {
            floatArray[i] /= (float) norm;
        }

        if (debugLogs) {
            double[] floatArrayDoubleAfter = new double[floatArray.length];
            for (int i = 0; i < floatArray.length; i++) {
                floatArrayDoubleAfter[i] = floatArray[i];
            }
            AppLogger.info("After normalization:");
            AppLogger.info("  Magnitude: " + EmbeddingVectorUtils.magnitude(floatArrayDoubleAfter));
            double[] first10After = getFirstN(floatArrayDoubleAfter, 10);
            AppLogger.info("  First 10 values: " + java.util.Arrays.toString(first10After));
        }

        byte[] result = EmbeddingVectorUtils.floatsToBytes(floatArray);
        if (debugLogs) {
            long hash = computeHash(result);
            AppLogger.info("  Normalized hash: " + hash);
            double checkNorm = EmbeddingVectorUtils.magnitude(floatArray);
            AppLogger.info("DEBUG After norm in matToByteArray: Magnitude = " + checkNorm);
        }
        return result;
    }

    private void debugEmbedding(byte[] emb, String label) {
        if (!debugLogs) {
            return;
        }
        if (emb == null) {
            System.err.println("DEBUG: " + label + " is NULL!");
            return;
        }
        float[] floatEmb = EmbeddingVectorUtils.toFloatArray(emb, embeddingSize * 4);
        if (floatEmb == null) {
            System.err.println("DEBUG: " + label + " decoding failed");
            return;
        }
        AppLogger.info("DEBUG " + label + " first 10 values: ");
        for (int i = 0; i < Math.min(10, floatEmb.length); i++) {
            AppLogger.info(String.format("%.6f ", floatEmb[i]));
        }
        AppLogger.info("| Magnitude: " + String.format("%.6f", EmbeddingVectorUtils.magnitude(floatEmb)));
    }

    private void logAlignedStats(Mat aligned) {
        if (!debugLogs) {
            return;
        }
        AppLogger.info("=== STAGE 2: Blob Creation ===");
        AppLogger.info("Aligned face stats:");
        AppLogger.info("  Resolution: " + aligned.size());
        AppLogger.info("  Mean: " + Core.mean(aligned));
        double[] minMax = getMinMax(aligned);
        AppLogger.info("  Min/Max: " + minMax[0] + " / " + minMax[1]);
    }

    private void logEmbeddingStats(Mat embedding) {
        if (!debugLogs) {
            return;
        }
        AppLogger.info("=== STAGE 3: Model Forward Pass ===");
        AppLogger.info("Model output stats:");
        AppLogger.info("  Shape: " + embedding.size());
        AppLogger.info("  Mean: " + Core.mean(embedding));
        double[] embMinMax = getMinMax(embedding);
        AppLogger.info("  Min/Max: " + embMinMax[0] + " / " + embMinMax[1]);
        double[] first10 = getFirstN(embedding, 10);
        AppLogger.info("  First 10 values: " + java.util.Arrays.toString(first10));
    }

    private void printBlobStats(Mat blob, String label) {
        if (!debugLogs) {
            return;
        }
        if (blob == null || blob.empty()) {
            AppLogger.info(label + ": blob is null or empty");
            return;
        }
        AppLogger.info(label + ":");
        AppLogger.info("  Dims: " + blob.dims());
        AppLogger.info("  Size: " + blob.size());

        int totalElements = (int) blob.total();
        if (totalElements <= 0) {
            AppLogger.info("  No data to analyze (totalElements=" + totalElements + ")");
            return;
        }

        float[] data = new float[totalElements];
        blob.get(0, 0, data);

        int spatialSize = (int) (inputSize.width * inputSize.height);
        int channels = (spatialSize > 0) ? Math.max(totalElements / spatialSize, 1) : 3;

        AppLogger.info("  Total elements: " + totalElements + ", Channels (estimated): " + channels);

        for (int c = 0; c < channels; c++) {
            double sum = 0.0;
            double sumSq = 0.0;
            float min = Float.POSITIVE_INFINITY;
            float max = Float.NEGATIVE_INFINITY;

            int offset = c * spatialSize;
            int limit = Math.min(offset + spatialSize, data.length);

            int count = 0;
            for (int i = offset; i < limit; i++) {
                float val = data[i];
                sum += val;
                sumSq += val * val;
                if (val < min) {
                    min = val;
                }
                if (val > max) {
                    max = val;
                }
                count++;
            }

            if (count == 0) {
                AppLogger.info("    Channel " + c + ": no data");
                continue;
            }

            double mean = sum / count;
            double variance = Math.max((sumSq / count) - (mean * mean), 0.0);
            double std = Math.sqrt(variance);

            System.out.printf("    Channel %d: mean=%.4f std=%.4f min=%.4f max=%.4f%n",
                    c, mean, std, min, max);
        }
    }

    private double[] getMinMax(Mat mat) {
        if (mat.dims() > 2) {
            Mat reshaped = mat.reshape(1, mat.rows() * mat.cols() * mat.channels());
            Core.MinMaxLocResult result = Core.minMaxLoc(reshaped);
            reshaped.release();
            return new double[] { result.minVal, result.maxVal };
        } else if (mat.channels() > 1) {
            Mat gray = new Mat();
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
            Core.MinMaxLocResult result = Core.minMaxLoc(gray);
            gray.release();
            return new double[] { result.minVal, result.maxVal };
        } else {
            Core.MinMaxLocResult result = Core.minMaxLoc(mat);
            return new double[] { result.minVal, result.maxVal };
        }
    }

    private double[] getFirstN(Mat mat, int n) {
        float[] data = new float[Math.min(n, mat.cols() * mat.rows() * mat.channels())];
        mat.get(0, 0, data);
        double[] result = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = data[i];
        }
        return result;
    }

    private double[] getFirstN(double[] array, int n) {
        double[] result = new double[Math.min(n, array.length)];
        System.arraycopy(array, 0, result, 0, result.length);
        return result;
    }

    private long computeHash(byte[] data) {
        long hash = 0;
        for (byte b : data) {
            hash = 31 * hash + b;
        }
        return hash;
    }

}







