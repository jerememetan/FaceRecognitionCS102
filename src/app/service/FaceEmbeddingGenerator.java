package app.service;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.opencv.dnn.Net;
import org.opencv.dnn.Dnn;
import org.opencv.photo.Photo;
import org.opencv.core.MatOfInt;
import app.util.FaceAligner;
import ConfigurationAndLogging.*;

public class FaceEmbeddingGenerator {

    private Net embeddingNet;
    private boolean isInitialized = false;
    private FaceAligner aligner;

    private static final boolean DEBUG_LOGS = false;

    private static final int EMBEDDING_SIZE = AppConfig.getInstance().getEmbeddingSize();
    private static final int INPUT_S = AppConfig.getInstance().getEmbeddingInputSize();
    private static final Size INPUT_SIZE = new Size(INPUT_S, INPUT_S);

    public FaceEmbeddingGenerator() {
        initializeEmbeddingNet();
        this.aligner = new FaceAligner();
    }

    private void initializeEmbeddingNet() {
        try {

            String modelPath = AppConfig.getInstance().getEmbeddingModelPath();

            if (new java.io.File(modelPath).exists()) {
                embeddingNet = Dnn.readNetFromONNX(modelPath);
                isInitialized = true;
                System.out.println("✅ ArcFace ResNet100 model loaded successfully");
            } else {
                System.out.println("⚠️ ArcFace model not found, using feature-based embeddings");
                isInitialized = false;
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to load face embedding model: " + e.getMessage());
            isInitialized = false;
        }
    }

    public byte[] generateEmbedding(Mat faceImage) {
        if (isInitialized) {
            return generateDeepEmbedding(faceImage);
        } else {
            return generateFeatureBasedEmbedding(faceImage);
        }
    }

    public byte[] generateEmbeddingFromBlob(Mat preprocessedBlob) {
        if (isInitialized && preprocessedBlob != null && !preprocessedBlob.empty()) {
            return generateDeepEmbeddingFromBlob(preprocessedBlob);
        } else {
            // Fallback to feature-based if blob is invalid or model not available
            System.err.println("⚠️ Invalid blob or model not available, cannot generate embedding from blob");
            return null;
        }
    }

    private void debugEmbedding(byte[] emb, String label) {
        if (!DEBUG_LOGS) {
            return;
        }
        if (emb == null) {
            System.err.println("DEBUG: " + label + " is NULL!");
            return;
        }

        float[] floatEmb = byteArrayToFloatArray(emb);
        if (floatEmb == null) {
            System.err.println("DEBUG: " + label + " decoding failed");
            return;
        }

        System.out.print("DEBUG " + label + " first 10 values: ");
        for (int i = 0; i < Math.min(10, floatEmb.length); i++) {
            System.out.print(String.format("%.6f ", floatEmb[i]));
        }
        System.out.println();

        double norm = 0;
        for (float f : floatEmb) {
            norm += f * f;
        }
        norm = Math.sqrt(norm);
        System.out.println("| Magnitude: " + String.format("%.6f", norm));
    }

    private byte[] generateDeepEmbedding(Mat faceImage) {
        try {

            Mat processed;
            if (faceImage.channels() != 3) {
                Mat temp = new Mat();
                if (faceImage.channels() == 1) {
                    Imgproc.cvtColor(faceImage, temp, Imgproc.COLOR_GRAY2BGR);
                } else {
                    temp = faceImage.clone();
                }
                processed = temp;
            } else {
                processed = faceImage.clone();
            }

            // ✅ FIX: For already-cropped faces, skip alignment or use heuristic fallback
            // Since face is already cropped and centered, alignment may not be necessary
            Mat aligned = aligner.align(processed, null); // Pass null since we don't have face rect

            if (aligned == null || aligned.empty()) {
                System.err.println("⚠️ Alignment failed, using processed face directly");
                aligned = processed.clone();
            }

            processed.release();

            if (DEBUG_LOGS) {
                System.out.println("=== STAGE 2: Blob Creation ===");
                System.out.println("Aligned face stats:");
                System.out.println("  Resolution: " + aligned.size());
                System.out.println("  Mean: " + Core.mean(aligned));
                double[] minMax = getMinMax(aligned);
                System.out.println("  Min/Max: " + minMax[0] + " / " + minMax[1]);
            }

            // ✅ ArcFace standard preprocessing: (pixel - 127.5) / 128.0
            // This maps [0, 255] → [-1, 1] which is what ArcFace expects
            // IMPORTANT: OpenCV subtracts the mean BEFORE applying the scale factor.
            // Therefore the mean must remain in the original pixel range [0, 255].
            Scalar arcFaceMean = new Scalar(127.5, 127.5, 127.5);
            Mat blobNCHW = Dnn.blobFromImage(aligned, 1.0 / 128.0, INPUT_SIZE,
                    arcFaceMean, true, false);
            printBlobStats(blobNCHW, "Aligned face blob (NCHW)");
            aligned.release();

            // New ArcFace ONNX expects NHWC layout, so permute before forwarding
            Mat blobNHWC = new Mat();
            MatOfInt nchwToNhwc = new MatOfInt(0, 2, 3, 1);
            Core.transposeND(blobNCHW, nchwToNhwc, blobNHWC);
            nchwToNhwc.release();
            blobNCHW.release();

            if (DEBUG_LOGS) {
                System.out.println("=== STAGE 3: Model Forward Pass ===");
                System.out.println("Blob input stats:");
                System.out.println("  Shape: " + java.util.Arrays.toString(getShape(blobNHWC)));
                System.out.println("  Mean: " + Core.mean(blobNHWC));
                System.out.println("  Std: " + getStdDev(blobNHWC));
            }

            // After forward pass
            embeddingNet.setInput(blobNHWC);
            Mat embedding = embeddingNet.forward();
            blobNHWC.release();
            if (DEBUG_LOGS) {
                System.out.println("Model output stats:");
                System.out.println("  Shape: " + embedding.size());
                System.out.println("  Mean: " + Core.mean(embedding));
                double[] embMinMax = getMinMax(embedding);
                System.out.println("  Min/Max: " + embMinMax[0] + " / " + embMinMax[1]);
                double[] first10 = getFirstN(embedding, 10);
                System.out.println("  First 10 values: " + java.util.Arrays.toString(first10));
            }

            // Convert to byte array for storage
            byte[] result = matToByteArray(embedding);
            embedding.release();

            // DEBUG: Add debug output
            debugEmbedding(result, "Generated embedding");

            return result;

        } catch (Exception e) {
            System.err.println("Deep embedding generation failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private byte[] generateDeepEmbeddingFromBlob(Mat preprocessedBlob) {
        try {
            // ✅ Use the preprocessed blob directly (no preprocessing needed)
            // Forward pass through network
            printBlobStats(preprocessedBlob, "External blob input (NCHW)");

            Mat convertedBlob = new Mat();
            MatOfInt nchwToNhwc = new MatOfInt(0, 2, 3, 1);
            Core.transposeND(preprocessedBlob, nchwToNhwc, convertedBlob);
            nchwToNhwc.release();
            preprocessedBlob.release();

            embeddingNet.setInput(convertedBlob);
            Mat embedding = embeddingNet.forward();
            convertedBlob.release();

            // Convert to byte array for storage
            byte[] result = matToByteArray(embedding);
            embedding.release();

            // DEBUG: Add debug output
            debugEmbedding(result, "Generated embedding from blob");

            return result;

        } catch (Exception e) {
            System.err.println("Deep embedding generation from blob failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private byte[] generateFeatureBasedEmbedding(Mat faceImage) {
        try {
            double[] features = new double[EMBEDDING_SIZE];

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

            byte[] result = doubleArrayToByteArray(features);

            // DEBUG: Add debug output for feature-based embeddings
            debugEmbedding(result, "Feature-based embedding");

            return result;

        } catch (Exception e) {
            System.err.println("❌ Feature-based embedding generation failed: " + e.getMessage());
            e.printStackTrace();
            return null; // ✅ Return null to signal failure
        }
    }

    private void extractHistogramFeatures(Mat image, double[] features, int offset) {
        Mat hist = new Mat();
        MatOfInt channels = new MatOfInt(0);
        MatOfInt histSize = new MatOfInt(32);
        MatOfFloat ranges = new MatOfFloat(0f, 256f);

        Imgproc.calcHist(java.util.Arrays.asList(image), channels, new Mat(),
                hist, histSize, ranges);
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
        Mat gradX = new Mat(), gradY = new Mat();
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
        Mat gradX = new Mat(), gradY = new Mat();
        Imgproc.Sobel(image, gradX, CvType.CV_64F, 1, 0, 3);
        Imgproc.Sobel(image, gradY, CvType.CV_64F, 0, 1, 3);

        Mat magnitude = new Mat(), angle = new Mat();
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

        double sum = java.util.Arrays.stream(hogFeatures).sum();
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

    private byte[] matToByteArray(Mat mat) {
        try {
            float[] floatArray = new float[EMBEDDING_SIZE];
            mat.get(0, 0, floatArray);

            if (DEBUG_LOGS) {
                System.out.println("=== STAGE 4: L2 Normalization ===");
                System.out.println("Before normalization:");
                double[] floatArrayDouble = new double[floatArray.length];
                for (int i = 0; i < floatArray.length; i++) {
                    floatArrayDouble[i] = floatArray[i];
                }
                System.out.println("  Magnitude: " + calculateMagnitude(floatArrayDouble));
                double[] first10Before = getFirstN(floatArrayDouble, 10);
                System.out.println("  First 10 values: " + java.util.Arrays.toString(first10Before));
            }

            // ✅ CRITICAL: Validate neural network output BEFORE normalization
            boolean hasInvalid = false;
            for (float f : floatArray) {
                if (Float.isNaN(f) || Float.isInfinite(f)) {
                    System.err.println("❌ Neural network produced invalid output (NaN/Inf detected)");
                    hasInvalid = true;
                    break;
                }
            }

            if (hasInvalid) {
                return null; // Signal failure instead of corrupting data
            }

            // ✅ L2 NORMALIZATION - MUST HAPPEN HERE
            double norm = 0.0;
            for (float f : floatArray) {
                norm += f * f;
            }
            norm = Math.sqrt(Math.max(norm, 1e-12));

            for (int i = 0; i < floatArray.length; i++) {
                floatArray[i] /= (float) norm; // ← THIS MUST DIVIDE BY NORM!
            }

            if (DEBUG_LOGS) {
                System.out.println("After normalization:");
                double[] floatArrayDoubleAfter = new double[floatArray.length];
                for (int i = 0; i < floatArray.length; i++) {
                    floatArrayDoubleAfter[i] = floatArray[i];
                }
                System.out.println("  Magnitude: " + calculateMagnitude(floatArrayDoubleAfter));
                double[] first10After = getFirstN(floatArrayDoubleAfter, 10);
                System.out.println("  First 10 values: " + java.util.Arrays.toString(first10After));
            }

            // Check if normalization produces identical outputs
            java.nio.ByteBuffer byteBuffer = java.nio.ByteBuffer.allocate(EMBEDDING_SIZE * 4);
            for (float f : floatArray) {
                byteBuffer.putFloat(f);
            }
            byte[] result = byteBuffer.array();

            if (DEBUG_LOGS) {
                long hash = computeHash(result);
                System.out.println("  Normalized hash: " + hash);
                double checkNorm = 0;
                for (float f : floatArray) {
                    checkNorm += f * f;
                }
                checkNorm = Math.sqrt(checkNorm);
                System.out.println("DEBUG After norm in matToByteArray: Magnitude = " + checkNorm);
            }
            // Should print ~1.0, not 4.97!

            return result;

        } catch (Exception e) {
            System.err.println("❌ Mat to byte array conversion failed: " + e.getMessage());
            e.printStackTrace();
            return null; // ✅ Return null instead of corrupt data
        }
    }

    private byte[] doubleArrayToByteArray(double[] array) {
        // ✅ L2 NORMALIZATION - MUST HAPPEN HERE
        double norm = 0.0;
        for (double d : array) {
            norm += d * d;
        }
        norm = Math.sqrt(Math.max(norm, 1e-12));

        for (int i = 0; i < array.length; i++) {
            array[i] /= norm;
        }

        if (DEBUG_LOGS) {
            double checkNorm = 0;
            for (double d : array) {
                checkNorm += d * d;
            }
            checkNorm = Math.sqrt(checkNorm);
            System.out.println("DEBUG After norm in doubleArrayToByteArray: Magnitude = " + checkNorm);
        }

        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(array.length * 8);
        for (double d : array) {
            buffer.putDouble(d);
        }
        return buffer.array();
    }

    public double calculateSimilarity(byte[] embedding1, byte[] embedding2) {
        if (embedding1 == null || embedding2 == null ||
                embedding1.length != embedding2.length) {
            return 0.0;
        }

        try {
            // Always use cosine similarity for embeddings (both are unit vectors after
            // normalization)
            if (embedding1.length == EMBEDDING_SIZE * 4) { // Float32 embeddings (ArcFace)
                return calculateCosineSimilarity(embedding1, embedding2);
            } else if (embedding1.length == EMBEDDING_SIZE * 8) { // Float64 embeddings (Legacy)
                return calculateCosineSimilarityDouble(embedding1, embedding2);
            } else {
                return 0.0;
            }
        } catch (Exception e) {
            System.err.println("Similarity calculation failed: " + e.getMessage());
            return 0.0;
        }
    }

    private double calculateCosineSimilarity(byte[] emb1, byte[] emb2) {
        float[] vec1 = byteArrayToFloatArray(emb1);
        float[] vec2 = byteArrayToFloatArray(emb2);

        // Calculate magnitudes
        double mag1 = 0, mag2 = 0;
        for (float f : vec1)
            mag1 += f * f;
        for (float f : vec2)
            mag2 += f * f;
        mag1 = Math.sqrt(mag1);
        mag2 = Math.sqrt(mag2);

        // ✅ PROPER COSINE SIMILARITY FORMULA
        double dotProduct = 0.0;
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
        }

        // ✅ DIVIDE BY MAGNITUDES!
        double similarity;
        if (mag1 > 0 && mag2 > 0) {
            similarity = dotProduct / (mag1 * mag2);
        } else {
            similarity = 0.0;
        }
        if (DEBUG_LOGS) {
            System.out.println("=== STAGE 5: Similarity Calculation ===");

            double[] vec1Double = new double[vec1.length];
            double[] vec2Double = new double[vec2.length];
            for (int i = 0; i < vec1.length; i++) {
                vec1Double[i] = vec1[i];
            }
            for (int i = 0; i < vec2.length; i++) {
                vec2Double[i] = vec2[i];
            }

            System.out.println("Vector 1:");
            System.out.println("  Magnitude: " + mag1);
            System.out.println("  First 10: " + java.util.Arrays.toString(getFirstN(vec1Double, 10)));

            System.out.println("Vector 2:");
            System.out.println("  Magnitude: " + mag2);
            System.out.println("  First 10: " + java.util.Arrays.toString(getFirstN(vec2Double, 10)));

            System.out.println("Dot Product: " + dotProduct);
            System.out.println("L2 Norms: " + mag1 + ", " + mag2);
            System.out.println("Calculated Similarity: " + similarity);

            if (similarity > 0.97) {
                double vec1Mean = 0;
                for (float v : vec1) {
                    vec1Mean += v;
                }
                vec1Mean /= vec1.length;

                double vec2Mean = 0;
                for (float v : vec2) {
                    vec2Mean += v;
                }
                vec2Mean /= vec2.length;

                System.err.println("⚠️⚠️⚠️ CRITICAL: 0.97+ similarity between different people!");
                System.err.println("   Vec1 mean: " + vec1Mean);
                System.err.println("   Vec2 mean: " + vec2Mean);

                double maxDeviation = 0;
                for (int i = 0; i < Math.min(vec1.length, vec2.length); i++) {
                    maxDeviation = Math.max(maxDeviation, Math.abs(vec1[i] - vec2[i]));
                }
                System.err.println("   Max deviation: " + maxDeviation);

                if (maxDeviation < 0.05) {
                    System.err.println("   ❌ VECTORS ARE NEARLY IDENTICAL - INPUT DATA IS IDENTICAL!");
                }
            }
        }

        // Clamp to [-1, 1] range to handle floating point precision issues
        return Math.max(-1.0, Math.min(1.0, similarity));
    }

    private double calculateCosineSimilarityDouble(byte[] emb1, byte[] emb2) {
        double[] vec1 = byteArrayToDoubleArray(emb1);
        double[] vec2 = byteArrayToDoubleArray(emb2);

        // Calculate magnitudes
        double mag1 = 0, mag2 = 0;
        for (double d : vec1)
            mag1 += d * d;
        for (double d : vec2)
            mag2 += d * d;
        mag1 = Math.sqrt(mag1);
        mag2 = Math.sqrt(mag2);

        // ✅ PROPER COSINE SIMILARITY FORMULA
        double dotProduct = 0.0;
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
        }

        // ✅ DIVIDE BY MAGNITUDES!
        double similarity;
        if (mag1 > 0 && mag2 > 0) {
            similarity = dotProduct / (mag1 * mag2);
        } else {
            similarity = 0.0;
        }

        // If similarity is > 0.97 between different people:
        if (similarity > 0.97) {
            // Calculate means manually for double arrays
            double vec1Mean = 0;
            for (double v : vec1)
                vec1Mean += v;
            vec1Mean /= vec1.length;

            double vec2Mean = 0;
            for (double v : vec2)
                vec2Mean += v;
            vec2Mean /= vec2.length;

            System.err.println("⚠️⚠️⚠️ CRITICAL: 0.97+ similarity between different people!");
            System.err.println("   Vec1 mean: " + vec1Mean);
            System.err.println("   Vec2 mean: " + vec2Mean);

            // Check if vectors are too similar in direction
            double maxDeviation = 0;
            for (int i = 0; i < Math.min(vec1.length, vec2.length); i++) {
                maxDeviation = Math.max(maxDeviation, Math.abs(vec1[i] - vec2[i]));
            }
            System.err.println("   Max deviation: " + maxDeviation);

            if (maxDeviation < 0.05) {
                System.err.println("   ❌ VECTORS ARE NEARLY IDENTICAL - INPUT DATA IS IDENTICAL!");
            }
        }

        // Clamp to [-1, 1] range to handle floating point precision issues
        return Math.max(-1.0, Math.min(1.0, similarity));
    }

    private float[] byteArrayToFloatArray(byte[] data) {
        if (data == null || data.length != EMBEDDING_SIZE * 4) {
            System.err.println("❌ ERROR: Invalid embedding byte length: " + (data == null ? "null" : data.length));
            return null;
        }

        float[] floatArray = new float[EMBEDDING_SIZE];
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(data);
        buffer.asFloatBuffer().get(floatArray);

        if (DEBUG_LOGS) {
            StringBuilder sb = new StringBuilder("DEBUG byteArrayToFloatArray: [");
            for (int i = 0; i < Math.min(5, floatArray.length); i++) {
                sb.append(String.format("%.6f ", floatArray[i]));
            }
            sb.append("....");
            for (int i = Math.max(0, floatArray.length - 5); i < floatArray.length; i++) {
                sb.append(String.format("%.6f ", floatArray[i]));
            }
            sb.append("]");

            long hash = 0;
            for (float f : floatArray) {
                hash = hash * 31 + Float.hashCode(f);
            }
            sb.append(" HASH=").append(hash);

            System.out.println(sb.toString());
        }

        return floatArray;
    }

    private double[] byteArrayToDoubleArray(byte[] bytes) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
        double[] doubles = new double[bytes.length / 8];
        for (int i = 0; i < doubles.length; i++) {
            doubles[i] = buffer.getDouble();
        }
        return doubles;
    }

    public boolean isDeepLearningAvailable() {
        return isInitialized;
    }

    public boolean isEmbeddingValid(byte[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return false;
        }

        int expectedSize = isInitialized ? (EMBEDDING_SIZE * 4) : (EMBEDDING_SIZE * 8);
        if (embedding.length != expectedSize) {
            System.err.println("Invalid embedding size: " + embedding.length + ", expected: " + expectedSize);
            return false;
        }

        try {
            double magnitude = 0.0;
            int validCount = 0;

            if (isInitialized) {
                float[] floats = byteArrayToFloatArray(embedding);
                for (float f : floats) {
                    if (Float.isNaN(f) || Float.isInfinite(f)) {
                        System.err.println("Invalid embedding: contains NaN or Inf");
                        return false;
                    }
                    magnitude += f * f;
                    if (Math.abs(f) > 1e-12) // ✅ Relaxed - counts values > 0.000000000001
                        validCount++;
                }
            } else {
                double[] doubles = byteArrayToDoubleArray(embedding);
                for (double d : doubles) {
                    if (Double.isNaN(d) || Double.isInfinite(d)) {
                        System.err.println("Invalid embedding: contains NaN or Inf");
                        return false;
                    }
                    magnitude += d * d;
                    if (Math.abs(d) > 1e-12) // ✅ Relaxed - counts values > 0.000000000001
                        validCount++;
                }
            }

            magnitude = Math.sqrt(magnitude);
            if (magnitude < 1e-10) { // ✅ Relaxed threshold
                System.err.println("Invalid embedding: zero magnitude");
                return false;
            }

            double nonZeroRatio = (double) validCount / EMBEDDING_SIZE;
            if (nonZeroRatio < 0.05) { // ✅ FURTHER RELAXED for ArcFace (5% non-zero is OK)
                System.err.println("Invalid embedding: too sparse (" +
                        String.format("%.1f%%", nonZeroRatio * 100) + " non-zero)");
                return false;
            }

            return true;
        } catch (Exception e) {
            System.err.println("Embedding validation failed: " + e.getMessage());
            return false;
        }
    }

    public BatchProcessingResult processCapturedImages(
            java.util.List<String> imagePaths,
            java.util.List<org.opencv.core.Rect> faceRects,
            app.util.ImageProcessor imageProcessor,
            ProgressCallback progressCallback) {

        if (imagePaths == null || imagePaths.isEmpty()) {
            return new BatchProcessingResult(0, 0, 0, "No images to process");
        }

        if (progressCallback != null) {
            progressCallback.onProgress("Processing captured images...");
        }

        int processedCount = 0;
        java.util.List<byte[]> generatedEmbeddings = new java.util.ArrayList<>();
        java.util.List<String> embeddingPaths = new java.util.ArrayList<>();
        java.util.List<String> successfulImagePaths = new java.util.ArrayList<>();

        for (int i = 0; i < imagePaths.size(); i++) {
            String imagePath = imagePaths.get(i);
            org.opencv.core.Rect faceRect = (faceRects != null && i < faceRects.size()) ? faceRects.get(i) : null;

            try {
                Mat image = org.opencv.imgcodecs.Imgcodecs.imread(imagePath);
                if (image.empty()) {
                    System.err.println("❌ Failed to read captured image: " + imagePath);
                    continue;
                }

                Mat faceROI;
                if (faceRect != null) {
                    // ✅ Validate rect is within image bounds
                    if (faceRect.x >= 0 && faceRect.y >= 0 &&
                            faceRect.x + faceRect.width <= image.width() &&
                            faceRect.y + faceRect.height <= image.height() &&
                            faceRect.width > 0 && faceRect.height > 0) {
                        faceROI = new Mat(image, faceRect);
                    } else {
                        System.err.println("⚠️ Invalid faceRect, using whole image");
                        faceRect = new Rect(0, 0, image.width(), image.height());
                        faceROI = image.clone();
                    }
                } else {
                    // Saved image IS ALREADY the cropped face
                    Rect wholeFaceRect = new Rect(0, 0, image.width(), image.height());
                    faceROI = image.clone();
                    faceRect = null; // ✅ FIX: Pass null to indicate entire image is the face
                }
                image.release();

                byte[] embedding = generateEmbedding(faceROI);
                faceROI.release();

                if (embedding != null && isEmbeddingValid(embedding)) {
                    String embPath = imagePath.replace(".png", ".emb");
                    try {
                        java.nio.file.Files.write(java.nio.file.Paths.get(embPath), embedding);
                        generatedEmbeddings.add(embedding);
                        embeddingPaths.add(embPath);
                        successfulImagePaths.add(imagePath);
                        processedCount++;
                        System.out.println("✓ Saved embedding: " + new java.io.File(embPath).getName());
                    } catch (Exception e) {
                        System.err.println("❌ Failed to save embedding for " + imagePath + ": " + e.getMessage());
                    }
                } else {
                    System.err.println("❌ Invalid embedding generated for: " + imagePath);
                }

            } catch (Exception e) {
                System.err.println("❌ Error processing image " + imagePath + ": " + e.getMessage());
            }
        }

        System.out
                .println("Embedding generation complete: " + processedCount + "/" + imagePaths.size() + " successful");

        if (processedCount < imagePaths.size() && progressCallback != null) {
            progressCallback.onProgress(
                    "Warning: Only " + processedCount + "/" + imagePaths.size() + " embeddings generated successfully");
        }

        int removedCount = 0;
        int weakRemovedCount = 0;
        if (generatedEmbeddings.size() >= 5) {
            if (progressCallback != null) {
                progressCallback.onProgress("Analyzing embedding quality...");
            }
            removedCount = detectAndRemoveOutliers(generatedEmbeddings, embeddingPaths, successfulImagePaths);
            if (removedCount > 0) {
                System.out.println("✓ Auto-removed " + removedCount + " outlier embedding(s)");
                if (progressCallback != null) {
                    progressCallback.onProgress("Removed " + removedCount + " outlier image(s) automatically");
                }
            } else {
                System.out.println("✓ All embeddings passed outlier check");
            }

            // Now check for weak embeddings among remaining ones
            if (generatedEmbeddings.size() >= 3) { // Need at least 3 for meaningful quality analysis
                weakRemovedCount = detectAndRemoveWeakEmbeddings(generatedEmbeddings, embeddingPaths,
                        successfulImagePaths);
                if (weakRemovedCount > 0) {
                    System.out.println("✓ Auto-removed " + weakRemovedCount + " weak embedding(s)");
                    if (progressCallback != null) {
                        progressCallback.onProgress("Removed " + weakRemovedCount + " weak image(s) automatically");
                    }
                } else {
                    System.out.println("✓ All embeddings passed weakness check");
                }
            }
        }

        String message = processedCount > 0
                ? "Successfully processed " + processedCount + " embeddings"
                : "Failed to process embeddings";

        return new BatchProcessingResult(processedCount, removedCount, weakRemovedCount, message);
    }

    private int detectAndRemoveOutliers(java.util.List<byte[]> embeddings,
            java.util.List<String> embeddingPaths,
            java.util.List<String> imagePaths) {
        if (embeddings == null || embeddings.size() < 5) {
            return 0;
        }

        try {
            double[] avgSimilarities = new double[embeddings.size()];

            for (int i = 0; i < embeddings.size(); i++) {
                double sum = 0;
                int count = 0;
                for (int j = 0; j < embeddings.size(); j++) {
                    if (i != j) {
                        sum += calculateSimilarity(embeddings.get(i), embeddings.get(j));
                        count++;
                    }
                }
                avgSimilarities[i] = sum / count;
            }

            double mean = 0;
            for (double sim : avgSimilarities) {
                mean += sim;
            }
            mean /= avgSimilarities.length;

            double variance = 0;
            for (double sim : avgSimilarities) {
                variance += Math.pow(sim - mean, 2);
            }
            double stdDev = Math.sqrt(variance / avgSimilarities.length);

            double outlierThreshold = Math.max(0.70, mean - 1.5 * stdDev);

            java.util.List<Integer> outlierIndices = new java.util.ArrayList<>();
            for (int i = 0; i < avgSimilarities.length; i++) {
                if (avgSimilarities[i] < outlierThreshold) {
                    outlierIndices.add(i);
                    System.out.println(String.format("  Outlier detected: index=%d, avgSim=%.4f, threshold=%.4f",
                            i, avgSimilarities[i], outlierThreshold));
                }
            }

            int removedCount = 0;
            for (int idx : outlierIndices) {
                try {
                    java.io.File embFile = new java.io.File(embeddingPaths.get(idx));
                    if (embFile.exists() && embFile.delete()) {
                        System.out.println("  ✗ Deleted outlier embedding: " + embFile.getName());
                        removedCount++;
                    }

                    java.io.File imgFile = new java.io.File(imagePaths.get(idx));
                    if (imgFile.exists() && imgFile.delete()) {
                        System.out.println("  ✗ Deleted outlier image: " + imgFile.getName());
                    }
                } catch (Exception e) {
                    System.err.println("Failed to delete outlier files at index " + idx + ": " + e.getMessage());
                }
            }

            return removedCount;

        } catch (Exception e) {
            System.err.println("Outlier detection failed: " + e.getMessage());
            return 0;
        }
    }

    private int detectAndRemoveWeakEmbeddings(java.util.List<byte[]> embeddings,
            java.util.List<String> embeddingPaths,
            java.util.List<String> imagePaths) {
        if (embeddings == null || embeddings.size() < 3) {
            return 0;
        }

        try {
            // Calculate average similarity scores for each embedding
            double[] avgSimilarities = new double[embeddings.size()];

            for (int i = 0; i < embeddings.size(); i++) {
                double sum = 0;
                int count = 0;
                for (int j = 0; j < embeddings.size(); j++) {
                    if (i != j) {
                        sum += calculateSimilarity(embeddings.get(i), embeddings.get(j));
                        count++;
                    }
                }
                avgSimilarities[i] = sum / count;
            }

            // Calculate mean and standard deviation
            double mean = 0;
            for (double sim : avgSimilarities) {
                mean += sim;
            }
            mean /= avgSimilarities.length;

            double variance = 0;
            for (double sim : avgSimilarities) {
                variance += Math.pow(sim - mean, 2);
            }
            double stdDev = Math.sqrt(variance / avgSimilarities.length);

            double weakThreshold = Math.max(0.5, mean - 1.0 * stdDev);
            double absoluteWeakThreshold = 0.6;

            java.util.List<Integer> weakIndices = new java.util.ArrayList<>();
            for (int i = 0; i < avgSimilarities.length; i++) {
                boolean isWeakByDeviation = avgSimilarities[i] < weakThreshold;
                boolean isWeakByAbsolute = avgSimilarities[i] < absoluteWeakThreshold;

                if (isWeakByDeviation || isWeakByAbsolute) {
                    weakIndices.add(i);
                    System.out.println(String.format(
                            "  Weak embedding detected: index=%d, avgSim=%.4f, threshold=%.4f, absThresh=%.4f",
                            i, avgSimilarities[i], weakThreshold, absoluteWeakThreshold));
                }
            }

            int removedCount = 0;
            // Remove in reverse order to maintain correct indices
            for (int i = weakIndices.size() - 1; i >= 0; i--) {
                int idx = weakIndices.get(i);
                try {
                    java.io.File embFile = new java.io.File(embeddingPaths.get(idx));
                    if (embFile.exists() && embFile.delete()) {
                        System.out.println("  ✗ Deleted weak embedding: " + embFile.getName());
                        removedCount++;
                    }

                    java.io.File imgFile = new java.io.File(imagePaths.get(idx));
                    if (imgFile.exists() && imgFile.delete()) {
                        System.out.println("  ✗ Deleted weak image: " + imgFile.getName());
                    }

                    // Remove from lists to maintain consistency
                    embeddings.remove(idx);
                    embeddingPaths.remove(idx);
                    imagePaths.remove(idx);

                } catch (Exception e) {
                    System.err.println("Failed to delete weak files at index " + idx + ": " + e.getMessage());
                }
            }

            return removedCount;

        } catch (Exception e) {
            System.err.println("Weak embedding detection failed: " + e.getMessage());
            return 0;
        }
    }

    public void release() {
        if (aligner != null) {
            aligner.release();
        }
    }
    // Inner classes

    public static class BatchProcessingResult {
        private final int processedCount;
        private final int removedOutlierCount;
        private final int removedWeakCount;
        private final String message;

        public BatchProcessingResult(int processedCount, int removedOutlierCount, int removedWeakCount,
                String message) {
            this.processedCount = processedCount;
            this.removedOutlierCount = removedOutlierCount;
            this.removedWeakCount = removedWeakCount;
            this.message = message;
        }

        public int getProcessedCount() {
            return processedCount;
        }

        public int getRemovedOutlierCount() {
            return removedOutlierCount;
        }

        public int getRemovedWeakCount() {
            return removedWeakCount;
        }

        public int getTotalRemovedCount() {
            return removedOutlierCount + removedWeakCount;
        }

        public String getMessage() {
            return message;
        }

        public boolean isSuccess() {
            return processedCount > 0;
        }
    }

    public interface ProgressCallback {
        void onProgress(String message);
    }

    // === SYSTEMATIC DEBUG METHODS ===

    private double calculateStdDev(Mat mat) {
        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stddev = new MatOfDouble();
        Core.meanStdDev(mat, mean, stddev);
        return stddev.get(0, 0)[0];
    }

    private double[] getMinMax(Mat mat) {
        if (mat.dims() > 2) {
            // Handle 4D tensors/blobs by reshaping to 2D
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

    private double calculateMagnitude(double[] vec) {
        double sum = 0;
        for (double v : vec) {
            sum += v * v;
        }
        return Math.sqrt(sum);
    }

    private double[] getFirstN(double[] array, int n) {
        double[] result = new double[Math.min(n, array.length)];
        System.arraycopy(array, 0, result, 0, result.length);
        return result;
    }

    private int[] getShape(Mat mat) {
        int dims = mat.dims();
        int[] shape = new int[dims];
        for (int i = 0; i < dims; i++) {
            shape[i] = mat.size(i);
        }
        return shape;
    }

    private long computeHash(byte[] data) {
        long hash = 0;
        for (byte b : data) {
            hash = 31 * hash + b;
        }
        return hash;
    }

    private void printBlobStats(Mat blob, String label) {
        if (!DEBUG_LOGS) {
            return;
        }
        if (blob == null || blob.empty()) {
            System.out.println(label + ": blob is null or empty");
            return;
        }

        System.out.println(label + ":");
        System.out.println("  Dims: " + blob.dims());
        System.out.println("  Size: " + blob.size());

        int totalElements = (int) blob.total();
        if (totalElements <= 0) {
            System.out.println("  No data to analyze (totalElements=" + totalElements + ")");
            return;
        }

        float[] data = new float[totalElements];
        blob.get(0, 0, data);

        int spatialSize = INPUT_S * INPUT_S;
        int channels = (spatialSize > 0) ? Math.max(totalElements / spatialSize, 1) : 3;

        System.out.println("  Total elements: " + totalElements + ", Channels (estimated): " + channels);

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
                if (val < min)
                    min = val;
                if (val > max)
                    max = val;
                count++;
            }

            if (count == 0) {
                System.out.println("    Channel " + c + ": no data");
                continue;
            }

            double mean = sum / count;
            double variance = Math.max((sumSq / count) - (mean * mean), 0.0);
            double std = Math.sqrt(variance);

            System.out.printf("    Channel %d: mean=%.4f std=%.4f min=%.4f max=%.4f%n",
                    c, mean, std, min, max);
        }
    }

    private double compareHistograms(Mat img1, Mat img2) {
        Mat hist1 = new Mat();
        Mat hist2 = new Mat();

        Imgproc.calcHist(java.util.Arrays.asList(img1), new MatOfInt(0), new Mat(),
                hist1, new MatOfInt(256), new MatOfFloat(0, 256));
        Imgproc.calcHist(java.util.Arrays.asList(img2), new MatOfInt(0), new Mat(),
                hist2, new MatOfInt(256), new MatOfFloat(0, 256));

        return Imgproc.compareHist(hist1, hist2, Imgproc.CV_COMP_CORREL);
    }

    private double compareVectors(Mat vec1, Mat vec2) {
        double dot = 0;
        float[] data1 = new float[vec1.cols() * vec1.rows() * vec1.channels()];
        float[] data2 = new float[vec2.cols() * vec2.rows() * vec2.channels()];
        vec1.get(0, 0, data1);
        vec2.get(0, 0, data2);

        for (int i = 0; i < Math.min(data1.length, data2.length); i++) {
            dot += data1[i] * data2[i];
        }
        return dot;
    }

    private double getStdDev(Mat mat) {
        return calculateStdDev(mat);
    }
}