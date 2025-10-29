package app.test;

import org.opencv.core.*;
import app.service.FaceEmbeddingGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.ByteBuffer;
import java.util.*;
import java.lang.reflect.Method;

/**
 * Test the actual cosineSimilarity function from NewFaceRecognitionDemo
 */
public class TestActualCosineSimilarity {
    public static void main(String[] args) throws Exception {
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

        // Calculate centroid (same as NewFaceRecognitionDemo)
        double[] centroid = computeCentroid(embeddings);

        // Generate test embedding
        String testImage = "data\\facedata\\S13234_Jin_Rae\\S13234_008.jpg";
        Mat image = org.opencv.imgcodecs.Imgcodecs.imread(testImage);
        Mat preprocessed = embGen.preprocessFaceForLiveRecognition(image);
        byte[] testEmb = embGen.generateEmbedding(preprocessed);
        preprocessed.release();
        image.release();

        // Call cosineSimilarity (same logic as NewFaceRecognitionDemo)
        double centroidSim = cosineSimilarity(testEmb, centroid);

        // Calculate individual scores
        double[] sims = new double[embeddings.size()];
        for (int i = 0; i < embeddings.size(); i++) {
            sims[i] = embGen.calculateSimilarity(testEmb, embeddings.get(i));
        }
        Arrays.sort(sims);

        double maxSim = sims[sims.length - 1];
        double avgTop3 = (sims[sims.length - 1] + sims[sims.length - 2] + sims[sims.length - 3]) / 3.0;
        double exemplarScore = 0.70 * maxSim + 0.30 * avgTop3;
        double fusionScore = 0.60 * centroidSim + 0.40 * exemplarScore;

        System.out.println("=== Results ===");
        System.out.println("Centroid similarity: " + String.format("%.4f", centroidSim));
        System.out.println("Max individual: " + String.format("%.4f", maxSim));
        System.out.println("Avg top-3: " + String.format("%.4f", avgTop3));
        System.out.println("Exemplar score: " + String.format("%.4f", exemplarScore));
        System.out.println("Fusion score: " + String.format("%.4f", fusionScore));

        System.out.println("\n=== Analysis ===");
        if (centroidSim >= 0.95) {
            System.out.println("✅ Centroid score is EXCELLENT (" + String.format("%.4f", centroidSim) + ")");
            System.out.println("✅ Fusion score should be HIGH (" + String.format("%.4f", fusionScore) + ")");
        } else if (centroidSim >= 0.80) {
            System.out.println(
                    "⚠️ Centroid score is GOOD but not excellent (" + String.format("%.4f", centroidSim) + ")");
            System.out.println("   Fusion score: " + String.format("%.4f", fusionScore));
        } else {
            System.out.println("❌ Centroid score is TOO LOW (" + String.format("%.4f", centroidSim) + ")");
            System.out.println("   This will drag down fusion score to: " + String.format("%.4f", fusionScore));
        }
    }

    // Same as NewFaceRecognitionDemo
    private static double[] computeCentroid(List<byte[]> embList) {
        if (embList == null || embList.isEmpty())
            return null;
        double[] sum = null;
        int count = 0;
        for (byte[] b : embList) {
            double[] v = decodeEmbeddingToDouble(b);
            if (v == null)
                continue;

            if (sum == null) {
                sum = Arrays.copyOf(v, v.length);
            } else {
                for (int i = 0; i < v.length; i++)
                    sum[i] += v[i];
            }
            count++;
        }
        if (sum == null || count == 0)
            return null;

        for (int i = 0; i < sum.length; i++)
            sum[i] /= count;

        normalizeL2InPlace(sum);
        return sum;
    }

    // Same as NewFaceRecognitionDemo
    private static double cosineSimilarity(byte[] queryEmb, double[] centroid) {
        double[] q = decodeEmbeddingToDouble(queryEmb);
        if (q == null || centroid == null || q.length != centroid.length)
            return 0.0;

        // ✅ CRITICAL: Normalize q after decoding!
        normalizeL2InPlace(q);

        double dot = 0.0;
        for (int i = 0; i < q.length; i++) {
            dot += q[i] * centroid[i];
        }

        return Math.max(-1.0, Math.min(1.0, dot));
    }

    // Same as NewFaceRecognitionDemo
    private static double[] decodeEmbeddingToDouble(byte[] emb) {
        if (emb == null)
            return null;
        try {
            if (emb.length == 128 * 4) {
                float[] fv = new float[128];
                ByteBuffer bb = ByteBuffer.wrap(emb);
                for (int i = 0; i < 128; i++)
                    fv[i] = bb.getFloat();
                double[] dv = new double[128];
                for (int i = 0; i < 128; i++)
                    dv[i] = fv[i];
                return dv;
            } else if (emb.length == 128 * 8) {
                double[] dv = new double[128];
                ByteBuffer bb = ByteBuffer.wrap(emb);
                for (int i = 0; i < 128; i++)
                    dv[i] = bb.getDouble();
                return dv;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static void normalizeL2InPlace(double[] vec) {
        double sumSq = 0.0;
        for (double v : vec)
            sumSq += v * v;
        double norm = Math.sqrt(sumSq);
        if (norm > 1e-12) {
            for (int i = 0; i < vec.length; i++)
                vec[i] /= norm;
        }
    }
}
