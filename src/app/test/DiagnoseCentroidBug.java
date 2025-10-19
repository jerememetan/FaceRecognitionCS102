package app.test;

import org.opencv.core.*;
import app.service.FaceEmbeddingGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Deep diagnostic of centroid similarity calculation
 */
public class DiagnoseCentroidBug {
    public static void main(String[] args) throws IOException {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        FaceEmbeddingGenerator embGen = new FaceEmbeddingGenerator();

        // Load all Jin_Rae embeddings
        String folder = "data\\facedata\\S13234_Jin_Rae";
        java.io.File dir = new java.io.File(folder);
        java.io.File[] embFiles = dir.listFiles((d, name) -> name.endsWith(".emb"));

        List<byte[]> embeddings = new ArrayList<>();
        for (java.io.File f : embFiles) {
            byte[] emb = Files.readAllBytes(Paths.get(f.getAbsolutePath()));
            embeddings.add(emb);
        }

        System.out.println("Loaded " + embeddings.size() + " embeddings\n");

        // Calculate centroid
        double[] centroid = calculateCentroid(embeddings);

        // Generate test embedding
        String testImage = "data\\facedata\\S13234_Jin_Rae\\S13234_008.jpg";
        Mat image = org.opencv.imgcodecs.Imgcodecs.imread(testImage);
        Mat preprocessed = embGen.preprocessFaceForLiveRecognition(image);
        byte[] testEmb = embGen.generateEmbedding(preprocessed);
        preprocessed.release();
        image.release();

        // Decode test embedding
        double[] testVec = decodeEmbeddingToDouble(testEmb);

        System.out.println("=== STEP 1: Check raw vectors ===");
        System.out.println("Test vector length: " + testVec.length);
        System.out.println("Centroid length: " + centroid.length);
        System.out.println("Test vector[0-5]: " + Arrays.toString(Arrays.copyOfRange(testVec, 0, 5)));
        System.out.println("Centroid[0-5]: " + Arrays.toString(Arrays.copyOfRange(centroid, 0, 5)));

        // Check norms BEFORE normalization
        double testNormBefore = calculateNorm(testVec);
        double centroidNormBefore = calculateNorm(centroid);
        System.out.println("\n=== STEP 2: Check norms BEFORE normalization ===");
        System.out.println("Test vector norm: " + String.format("%.6f", testNormBefore));
        System.out.println("Centroid norm: " + String.format("%.6f", centroidNormBefore));
        System.out.println("Centroid is L2-normalized: " + (Math.abs(centroidNormBefore - 1.0) < 0.001));
        System.out.println("Test vector is L2-normalized: " + (Math.abs(testNormBefore - 1.0) < 0.001));

        // Normalize test vector
        normalizeL2InPlace(testVec);

        double testNormAfter = calculateNorm(testVec);
        System.out.println("\n=== STEP 3: Check norms AFTER normalizing test vector ===");
        System.out.println("Test vector norm: " + String.format("%.6f", testNormAfter));
        System.out.println("Test vector is L2-normalized: " + (Math.abs(testNormAfter - 1.0) < 0.001));

        // Calculate dot product
        double dot = 0.0;
        for (int i = 0; i < testVec.length; i++) {
            dot += testVec[i] * centroid[i];
        }

        System.out.println("\n=== STEP 4: Calculate cosine similarity ===");
        System.out.println("Dot product: " + String.format("%.6f", dot));
        System.out.println("Cosine similarity (dot product of normalized vectors): " + String.format("%.4f", dot));

        // Compare with individual embeddings
        System.out.println("\n=== STEP 5: Compare with individual embeddings ===");
        double[] individualSims = new double[embeddings.size()];
        for (int i = 0; i < embeddings.size(); i++) {
            individualSims[i] = embGen.calculateSimilarity(testEmb, embeddings.get(i));
        }
        Arrays.sort(individualSims);
        System.out.println(
                "Max individual similarity: " + String.format("%.4f", individualSims[individualSims.length - 1]));
        System.out.println("Avg top-3 similarity: " + String.format("%.4f",
                (individualSims[individualSims.length - 1] +
                        individualSims[individualSims.length - 2] +
                        individualSims[individualSims.length - 3]) / 3.0));

        System.out.println("\n=== DIAGNOSIS ===");
        if (dot < 0.80) {
            System.out.println("❌ PROBLEM: Centroid similarity is LOW (" + String.format("%.4f", dot) + ")");
            System.out.println("   But individual similarities are HIGH (" +
                    String.format("%.4f", individualSims[individualSims.length - 1]) + ")");
            System.out.println("\n   Possible causes:");
            System.out.println("   1. Test vector not normalized before calculation");
            System.out.println("   2. Centroid not normalized properly");
            System.out.println("   3. Byte decoding loses normalization");
            System.out.println("   4. Different preprocessing between training and live");
        } else {
            System.out.println("✅ Centroid similarity is GOOD (" + String.format("%.4f", dot) + ")");
        }
    }

    private static double[] calculateCentroid(List<byte[]> embeddings) {
        int dim = 128;
        double[] sum = new double[dim];

        for (byte[] emb : embeddings) {
            double[] vec = decodeEmbeddingToDouble(emb);
            for (int i = 0; i < dim; i++) {
                sum[i] += vec[i];
            }
        }

        for (int i = 0; i < dim; i++) {
            sum[i] /= embeddings.size();
        }

        // L2 normalize
        normalizeL2InPlace(sum);
        return sum;
    }

    private static double[] decodeEmbeddingToDouble(byte[] embedding) {
        // ✅ Use ByteBuffer properly - respects Java's byte order
        if (embedding.length == 128 * 4) {
            // 512 bytes = float embeddings
            float[] fv = new float[128];
            ByteBuffer bb = ByteBuffer.wrap(embedding);
            for (int i = 0; i < 128; i++) {
                fv[i] = bb.getFloat();
            }
            double[] dv = new double[128];
            for (int i = 0; i < 128; i++) {
                dv[i] = fv[i];
            }
            return dv;
        } else if (embedding.length == 128 * 8) {
            // 1024 bytes = double embeddings
            double[] dv = new double[128];
            ByteBuffer bb = ByteBuffer.wrap(embedding);
            for (int i = 0; i < 128; i++) {
                dv[i] = bb.getDouble();
            }
            return dv;
        }
        return null;
    }

    private static double calculateNorm(double[] vec) {
        double sum = 0.0;
        for (double v : vec) {
            sum += v * v;
        }
        return Math.sqrt(sum);
    }

    private static void normalizeL2InPlace(double[] vec) {
        double norm = calculateNorm(vec);
        if (norm > 0) {
            for (int i = 0; i < vec.length; i++) {
                vec[i] /= norm;
            }
        }
    }
}
