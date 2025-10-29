package app.service.recognition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import ConfigurationAndLogging.AppConfig;
import app.service.FaceEmbeddingGenerator;

/**
 * Tracks short-term recognition history for temporal smoothing and consistency
 * checks.
 */
final class RecognitionHistory {

    private static final int CONSISTENCY_WINDOW = 7;
    private static final int CONSISTENCY_MIN_COUNT = 5;
    private static final int Q_EMB_WINDOW = 5;

    private final Deque<Integer> recentPredictions = new ArrayDeque<>(CONSISTENCY_WINDOW);
    private final Deque<byte[]> recentEmbeddings = new ArrayDeque<>(Q_EMB_WINDOW);

    void reset() {
        recentPredictions.clear();
        recentEmbeddings.clear();
    }

    void recordEmbedding(byte[] embedding) {
        if (embedding == null) {
            return;
        }
        if (recentEmbeddings.size() == Q_EMB_WINDOW) {
            recentEmbeddings.pollFirst();
        }
        recentEmbeddings.offerLast(embedding);
    }

    byte[] buildSmoothedEmbedding(FaceEmbeddingGenerator embeddingGenerator) {
        if (recentEmbeddings.size() < 2) {
            return null;
        }

        int targetSize = Math.max(1, AppConfig.getInstance().getEmbeddingSize());
        double[] sum = new double[targetSize];
        double totalWeight = 0.0;

        List<byte[]> snapshot = new ArrayList<>(recentEmbeddings);
        int size = snapshot.size();

        for (int idx = 0; idx < size; idx++) {
            byte[] bytes = snapshot.get(idx);
            double[] vector = RecognitionEmbeddingUtils.decodeToDouble(bytes);
            if (vector == null) {
                continue;
            }

            double weight = (double) (idx + 1) / size;
            int copyLength = Math.min(targetSize, vector.length);
            for (int i = 0; i < copyLength; i++) {
                sum[i] += vector[i] * weight;
            }
            totalWeight += weight;
        }

        if (totalWeight == 0.0) {
            return null;
        }

        for (int i = 0; i < sum.length; i++) {
            sum[i] /= totalWeight;
        }

        RecognitionEmbeddingUtils.normalizeL2InPlace(sum);
        return RecognitionEmbeddingUtils.encodeFromDouble(sum, embeddingGenerator.isDeepLearningAvailable());
    }

    void recordPrediction(int predictionIndex) {
        if (recentPredictions.size() == CONSISTENCY_WINDOW) {
            recentPredictions.pollFirst();
        }
        recentPredictions.offerLast(predictionIndex);
    }

    boolean isConsistent(int index) {
        if (index < 0) {
            return false;
        }
        int count = 0;
        for (Integer prediction : recentPredictions) {
            if (prediction != null && prediction == index) {
                count++;
            }
        }
        return count >= CONSISTENCY_MIN_COUNT;
    }

    int countMatches(int index) {
        if (index < 0) {
            return 0;
        }
        int count = 0;
        for (Integer prediction : recentPredictions) {
            if (prediction != null && prediction == index) {
                count++;
            }
        }
        return count;
    }

    int consistencyWindowSize() {
        return CONSISTENCY_WINDOW;
    }

    int minimumConsistencyCount() {
        return CONSISTENCY_MIN_COUNT;
    }
}
