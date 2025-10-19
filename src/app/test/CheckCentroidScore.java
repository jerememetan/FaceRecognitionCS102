package app.test;

import org.opencv.core.*;
import app.service.FaceEmbeddingGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Check centroid score vs individual embedding scores
 */
public class CheckCentroidScore {
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

        System.out.println("Loaded " + embeddings.size() + " embeddings");

        // Calculate centroid
        double[] centroid = calculateCentroid(embeddings);
        byte[] centroidBytes = encodeEmbeddingFromDouble(centroid);

        // Generate new embedding from test image
        String testImage = "data\\facedata\\S13234_Jin_Rae\\S13234_008.jpg";
        Mat image = org.opencv.imgcodecs.Imgcodecs.imread(testImage);
        Mat preprocessed = embGen.preprocessFaceForLiveRecognition(image);
        byte[] testEmb = embGen.generateEmbedding(preprocessed);
        preprocessed.release();
        image.release();

        // Compare: test embedding vs individual embeddings
        System.out.println("\n=== Individual Embedding Scores ===");
        double[] sims = new double[embeddings.size()];
        for (int i = 0; i < embeddings.size(); i++) {
            sims[i] = embGen.calculateSimilarity(testEmb, embeddings.get(i));
            System.out.println("Embedding " + (i + 1) + ": " + String.format("%.4f", sims[i]));
        }
        Arrays.sort(sims);

        double maxSim = sims[sims.length - 1];
        double avgTop3 = (sims[sims.length - 1] + sims[sims.length - 2] + sims[sims.length - 3]) / 3.0;

        // Compare: test embedding vs centroid
        double centroidSim = cosineSimilarity(testEmb, centroidBytes, embGen);

        System.out.println("\n=== Scores ===");
        System.out.println("Max individual: " + String.format("%.4f", maxSim));
        System.out.println("Avg top-3: " + String.format("%.4f", avgTop3));
        System.out.println("Centroid: " + String.format("%.4f", centroidSim));

        // Calculate fusion score
        double exemplarScore = 0.70 * maxSim + 0.30 * avgTop3;
        double fusionScore = 0.60 * centroidSim + 0.40 * exemplarScore;

        System.out.println("\n=== Fusion Calculation ===");
        System.out.println("Exemplar score (70% max + 30% avg): " + String.format("%.4f", exemplarScore));
        System.out.println("Fusion score (60% centroid + 40% exemplar): " + String.format("%.4f", fusionScore));

        System.out.println("\n=== Analysis ===");
        if (centroidSim < 0.80) {
            System.out
                    .println("⚠️ FOUND THE ISSUE: Centroid score is LOW (" + String.format("%.4f", centroidSim) + ")");
            System.out.println("   Individual embeddings: " + String.format("%.4f", maxSim) + " (excellent!)");
            System.out.println("   But centroid pulls down fusion score to: " + String.format("%.4f", fusionScore));
            System.out.println("   This explains live recognition scores of 0.70-0.78!");
        } else {
            System.out.println("✅ Centroid score is good (" + String.format("%.4f", centroidSim) + ")");
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
        double norm = 0.0;
        for (double v : sum) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);

        for (int i = 0; i < dim; i++) {
            sum[i] /= norm;
        }

        return sum;
    }

    private static double[] decodeEmbeddingToDouble(byte[] embedding) {
        // ✅ Use ByteBuffer properly - respects Java's byte order
        if (embedding.length == 128 * 4) {
            // 512 bytes = float embeddings
            float[] fv = new float[128];
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(embedding);
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
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(embedding);
            for (int i = 0; i < 128; i++) {
                dv[i] = bb.getDouble();
            }
            return dv;
        }
        return null;
    }

    private static byte[] encodeEmbeddingFromDouble(double[] vec) {
        byte[] result = new byte[vec.length * 4];

        for (int i = 0; i < vec.length; i++) {
            int bits = Float.floatToIntBits((float) vec[i]);
            result[i * 4] = (byte) (bits & 0xFF);
            result[i * 4 + 1] = (byte) ((bits >> 8) & 0xFF);
            result[i * 4 + 2] = (byte) ((bits >> 16) & 0xFF);
            result[i * 4 + 3] = (byte) ((bits >> 24) & 0xFF);
        }

        return result;
    }

    private static double cosineSimilarity(byte[] a, byte[] b, FaceEmbeddingGenerator embGen) {
        return embGen.calculateSimilarity(a, b);
    }
}
