package app.test;

import org.opencv.core.*;
import app.service.FaceEmbeddingGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Check if there's a bug in the actual NewFaceRecognitionDemo scoring
 */
public class CheckActualScoring {
    public static void main(String[] args) throws Exception {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        FaceEmbeddingGenerator embGen = new FaceEmbeddingGenerator();

        // Load all Jin_Rae embeddings (the stored training embeddings)
        String folder = "data\\facedata\\S13234_Jin_Rae";
        java.io.File dir = new java.io.File(folder);
        java.io.File[] embFiles = dir.listFiles((d, name) -> name.endsWith(".emb"));

        List<byte[]> storedEmbeddings = new ArrayList<>();
        for (java.io.File f : embFiles) {
            byte[] emb = Files.readAllBytes(Paths.get(f.getAbsolutePath()));
            storedEmbeddings.add(emb);
        }

        System.out.println("Loaded " + storedEmbeddings.size() + " stored training embeddings");
        System.out.println("These embeddings were created using: ImageProcessor + generateEmbedding");
        System.out.println("(bilateral filter + CLAHE + resize 200x200 + denoise + resize 96x96)\n");

        // Calculate centroid (same way NewFaceRecognitionDemo does)
        double[] centroid = computeCentroid(storedEmbeddings);

        // Generate test embedding using LIVE PREPROCESSING
        String testImage = "data\\facedata\\S13234_Jin_Rae\\S13234_008.jpg";
        Mat image = org.opencv.imgcodecs.Imgcodecs.imread(testImage);

        // Use the LIVE preprocessing path
        Mat preprocessed = embGen.preprocessFaceForLiveRecognition(image);
        byte[] queryEmb = embGen.generateEmbedding(preprocessed);
        preprocessed.release();
        image.release();

        System.out.println("=== CHECKING FUSION SCORE CALCULATION ===\n");

        // Calculate centroid score
        double centroidScore = cosineSimilarity(queryEmb, centroid);
        System.out.println("Step 1 - Centroid Score: " + String.format("%.4f", centroidScore));

        // Calculate individual similarities
        double[] sims = new double[storedEmbeddings.size()];
        for (int i = 0; i < storedEmbeddings.size(); i++) {
            sims[i] = embGen.calculateSimilarity(queryEmb, storedEmbeddings.get(i));
        }
        Arrays.sort(sims);

        // Top-3 scoring
        int k = Math.min(3, sims.length);
        double sumTopK = 0.0;
        for (int i = 0; i < k; i++) {
            sumTopK += sims[sims.length - 1 - i];
        }
        double avgTopK = sumTopK / k;
        double maxTopK = sims[sims.length - 1];

        System.out.println("Step 2 - Individual Similarities:");
        System.out.println("  Max: " + String.format("%.4f", maxTopK));
        System.out.println("  Avg top-3: " + String.format("%.4f", avgTopK));

        // Exemplar score
        double exemplarScore = 0.70 * maxTopK + 0.30 * avgTopK;
        System.out.println("\nStep 3 - Exemplar Score:");
        System.out
                .println("  0.70 * " + String.format("%.4f", maxTopK) + " + 0.30 * " + String.format("%.4f", avgTopK));
        System.out.println("  = " + String.format("%.4f", exemplarScore));

        // Fusion score (EXACTLY as NewFaceRecognitionDemo calculates it)
        double fusionScore = 0.60 * centroidScore + 0.40 * exemplarScore;
        System.out.println("\nStep 4 - Fusion Score:");
        System.out.println("  0.60 * " + String.format("%.4f", centroidScore) + " + 0.40 * "
                + String.format("%.4f", exemplarScore));
        System.out.println("  = " + String.format("%.4f", fusionScore));

        System.out.println("\n=== COMPARING WITH STORED EMBEDDING ===\n");

        // Now test using a STORED embedding (not generated live)
        byte[] storedQuery = Files.readAllBytes(Paths.get("data\\facedata\\S13234_Jin_Rae\\S13234_008.emb"));

        double centroidScore2 = cosineSimilarity(storedQuery, centroid);
        double[] sims2 = new double[storedEmbeddings.size()];
        for (int i = 0; i < storedEmbeddings.size(); i++) {
            sims2[i] = embGen.calculateSimilarity(storedQuery, storedEmbeddings.get(i));
        }
        Arrays.sort(sims2);

        double sumTopK2 = 0.0;
        for (int i = 0; i < k; i++) {
            sumTopK2 += sims2[sims2.length - 1 - i];
        }
        double avgTopK2 = sumTopK2 / k;
        double maxTopK2 = sims2[sims2.length - 1];
        double exemplarScore2 = 0.70 * maxTopK2 + 0.30 * avgTopK2;
        double fusionScore2 = 0.60 * centroidScore2 + 0.40 * exemplarScore2;

        System.out.println("Stored embedding fusion score: " + String.format("%.4f", fusionScore2));
        System.out.println("(This should be the BEST possible score - perfect match)");

        System.out.println("\n=== DIAGNOSIS ===\n");

        if (fusionScore < 0.70) {
            System.out.println("❌ CRITICAL: Live fusion score is " + String.format("%.4f", fusionScore));
            System.out.println("   This matches what we see in logs (0.59-0.73)");
            System.out.println("\n   Problem breakdown:");
            System.out.println("   - Centroid score: " + String.format("%.4f", centroidScore) +
                    (centroidScore < 0.80 ? " ❌ TOO LOW" : " ✅"));
            System.out.println("   - Exemplar score: " + String.format("%.4f", exemplarScore) +
                    (exemplarScore < 0.80 ? " ❌ TOO LOW" : " ✅"));
            System.out.println("\n   Root cause: Live preprocessing doesn't match training preprocessing!");
            System.out.println("   Expected: Both should produce ~0.87 fusion score");
            System.out.println("   Actual: Only " + String.format("%.4f", fusionScore));
        } else if (fusionScore < 0.85) {
            System.out.println("⚠️ Live fusion score is " + String.format("%.4f", fusionScore));
            System.out.println("   This is OK but not great - preprocessing matches but quality is moderate");
        } else {
            System.out.println("✅ Live fusion score is " + String.format("%.4f", fusionScore));
            System.out.println("   Preprocessing is working correctly!");
        }
    }

    // Same implementations as NewFaceRecognitionDemo
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

    private static double cosineSimilarity(byte[] queryEmb, double[] centroid) {
        double[] q = decodeEmbeddingToDouble(queryEmb);
        if (q == null || centroid == null || q.length != centroid.length)
            return 0.0;

        normalizeL2InPlace(q);

        double dot = 0.0;
        for (int i = 0; i < q.length; i++) {
            dot += q[i] * centroid[i];
        }

        return Math.max(-1.0, Math.min(1.0, dot));
    }

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
