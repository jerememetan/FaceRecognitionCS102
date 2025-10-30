package service.embedding;

import config.AppConfig;

/**
 * Validates embedding vectors for downstream use.
 */
public class EmbeddingValidator {

    private final int embeddingSize;

    public EmbeddingValidator() {
        this.embeddingSize = AppConfig.getInstance().getEmbeddingSize();
    }

    public boolean isValid(byte[] embedding, boolean deepLearningActive) {
        if (embedding == null || embedding.length == 0) {
            return false;
        }

        int expectedSize = deepLearningActive ? embeddingSize * 4 : embeddingSize * 8;
        if (embedding.length != expectedSize) {
            System.err.println("Invalid embedding size: " + embedding.length + ", expected: " + expectedSize);
            return false;
        }

        try {
            double magnitude = 0.0;
            int validCount = 0;

            if (deepLearningActive) {
                float[] floats = EmbeddingVectorUtils.toFloatArray(embedding, expectedSize);
                if (floats == null) {
                    return false;
                }
                for (float f : floats) {
                    if (Float.isNaN(f) || Float.isInfinite(f)) {
                        System.err.println("Invalid embedding: contains NaN or Inf");
                        return false;
                    }
                    magnitude += f * f;
                    if (Math.abs(f) > 1e-12) {
                        validCount++;
                    }
                }
            } else {
                double[] doubles = EmbeddingVectorUtils.toDoubleArray(embedding);
                if (doubles == null) {
                    return false;
                }
                for (double d : doubles) {
                    if (Double.isNaN(d) || Double.isInfinite(d)) {
                        System.err.println("Invalid embedding: contains NaN or Inf");
                        return false;
                    }
                    magnitude += d * d;
                    if (Math.abs(d) > 1e-12) {
                        validCount++;
                    }
                }
            }

            magnitude = Math.sqrt(magnitude);
            if (magnitude < 1e-10) {
                System.err.println("Invalid embedding: zero magnitude");
                return false;
            }

            double nonZeroRatio = (double) validCount / embeddingSize;
            if (nonZeroRatio < 0.05) {
                System.err.println("Invalid embedding: too sparse (" + String.format("%.1f%%", nonZeroRatio * 100) + " non-zero)");
                return false;
            }

            return true;
        } catch (Exception e) {
            System.err.println("Embedding validation failed: " + e.getMessage());
            return false;
        }
    }
}







