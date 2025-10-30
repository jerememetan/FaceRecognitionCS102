package service.embedding;

import config.AppConfig;
import java.util.Arrays;

/**
 * Calculates similarity scores between embeddings.
 */
public class SimilarityCalculator {

    private final int embeddingSize;
    private final boolean debugLogs;

    public SimilarityCalculator(boolean debugLogs) {
        this.embeddingSize = AppConfig.getInstance().getEmbeddingSize();
        this.debugLogs = debugLogs;
    }

    public double calculate(byte[] embedding1, byte[] embedding2) {
        if (embedding1 == null || embedding2 == null || embedding1.length != embedding2.length) {
            return 0.0;
        }
        try {
            if (embedding1.length == embeddingSize * 4) {
                return calculateCosineFloat(embedding1, embedding2);
            } else if (embedding1.length == embeddingSize * 8) {
                return calculateCosineDouble(embedding1, embedding2);
            }
        } catch (Exception e) {
            System.err.println("Similarity calculation failed: " + e.getMessage());
        }
        return 0.0;
    }

    private double calculateCosineFloat(byte[] emb1, byte[] emb2) {
        float[] vec1 = EmbeddingVectorUtils.toFloatArray(emb1, embeddingSize * 4);
        float[] vec2 = EmbeddingVectorUtils.toFloatArray(emb2, embeddingSize * 4);
        if (vec1 == null || vec2 == null) {
            return 0.0;
        }

        double mag1 = EmbeddingVectorUtils.magnitude(vec1);
        double mag2 = EmbeddingVectorUtils.magnitude(vec2);
        double dot = EmbeddingVectorUtils.dot(vec1, vec2);

        double similarity = (mag1 > 0 && mag2 > 0) ? dot / (mag1 * mag2) : 0.0;
        similarity = clamp(similarity);

        if (debugLogs) {
            debugSimilarity(vec1, vec2, dot, mag1, mag2, similarity);
        }
        return similarity;
    }

    private double calculateCosineDouble(byte[] emb1, byte[] emb2) {
        double[] vec1 = EmbeddingVectorUtils.toDoubleArray(emb1);
        double[] vec2 = EmbeddingVectorUtils.toDoubleArray(emb2);
        if (vec1 == null || vec2 == null) {
            return 0.0;
        }

        double mag1 = EmbeddingVectorUtils.magnitude(vec1);
        double mag2 = EmbeddingVectorUtils.magnitude(vec2);
        double dot = EmbeddingVectorUtils.dot(vec1, vec2);

        double similarity = (mag1 > 0 && mag2 > 0) ? dot / (mag1 * mag2) : 0.0;
        similarity = clamp(similarity);

        if (debugLogs) {
            debugSimilarity(vec1, vec2, dot, mag1, mag2, similarity);
        }
        return similarity;
    }

    private void debugSimilarity(float[] vec1, float[] vec2, double dot, double mag1, double mag2, double similarity) {
        System.out.println("=== STAGE 5: Similarity Calculation (float) ===");
        System.out.println("Vector 1: magnitude=" + mag1 + " first10=" + Arrays.toString(firstN(vec1, 10)));
        System.out.println("Vector 2: magnitude=" + mag2 + " first10=" + Arrays.toString(firstN(vec2, 10)));
        System.out.println("Dot Product: " + dot);
        System.out.println("Calculated Similarity: " + similarity);
        flagHighSimilarity(similarity, vec1, vec2);
    }

    private void debugSimilarity(double[] vec1, double[] vec2, double dot, double mag1, double mag2, double similarity) {
        System.out.println("=== STAGE 5: Similarity Calculation (double) ===");
        System.out.println("Vector 1: magnitude=" + mag1 + " first10=" + Arrays.toString(firstN(vec1, 10)));
        System.out.println("Vector 2: magnitude=" + mag2 + " first10=" + Arrays.toString(firstN(vec2, 10)));
        System.out.println("Dot Product: " + dot);
        System.out.println("Calculated Similarity: " + similarity);
        flagHighSimilarity(similarity, vec1, vec2);
    }

    private void flagHighSimilarity(double similarity, float[] vec1, float[] vec2) {
        if (similarity <= 0.97) {
            return;
        }
    double vec1Mean = mean(vec1);
    double vec2Mean = mean(vec2);
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

    private void flagHighSimilarity(double similarity, double[] vec1, double[] vec2) {
        if (similarity <= 0.97) {
            return;
        }
        double vec1Mean = Arrays.stream(vec1).average().orElse(0.0);
        double vec2Mean = Arrays.stream(vec2).average().orElse(0.0);
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

    private double clamp(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }

    private double[] firstN(float[] vec, int n) {
        int length = Math.min(n, vec.length);
        double[] result = new double[length];
        for (int i = 0; i < length; i++) {
            result[i] = vec[i];
        }
        return result;
    }

    private double[] firstN(double[] vec, int n) {
        return Arrays.copyOf(vec, Math.min(n, vec.length));
    }

    private double mean(float[] vec) {
        if (vec.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (float v : vec) {
            sum += v;
        }
        return sum / vec.length;
    }
}







