package service.recognition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import service.embedding.FaceEmbeddingGenerator;

/**
 * Calculates similarity scores between the live embedding and each stored
 * profile. Also computes derived metrics required for decision making.
 */
final class RecognitionScorer {

    private static final double PENALTY_WEIGHT = 0.20;
    private static final double CENTROID_PREFILTER_THRESHOLD = 0.0;

    private final RecognitionDatasetRepository datasetRepository;
    private final FaceEmbeddingGenerator embeddingGenerator;

    RecognitionScorer(RecognitionDatasetRepository datasetRepository, FaceEmbeddingGenerator embeddingGenerator) {
        this.datasetRepository = datasetRepository;
        this.embeddingGenerator = embeddingGenerator;
    }

    ScoreResult score(byte[] queryEmbedding, byte[] smoothedEmbedding) {
        List<ProfileScore> scores = new ArrayList<>();
        int prefilterSkipped = 0;

        for (RecognitionProfile profile : datasetRepository.profiles()) {
            if (!profile.hasEmbeddings()) {
                scores.add(new ProfileScore(profile, 0.0));
                continue;
            }

            if (profile.centroid() != null) {
                double centroidScore = RecognitionEmbeddingUtils.cosineSimilarity(queryEmbedding, profile.centroid());
                if (centroidScore < CENTROID_PREFILTER_THRESHOLD) {
                    scores.add(new ProfileScore(profile, 0.0));
                    prefilterSkipped++;
                    continue;
                }
            }

            double fusedScore = computeFusedScore(queryEmbedding, profile);
            if (smoothedEmbedding != null) {
                double smoothedScore = computeFusedScore(smoothedEmbedding, profile);
                fusedScore = Math.max(fusedScore, smoothedScore);
            }

            scores.add(new ProfileScore(profile, fusedScore));
        }

        if (scores.isEmpty()) {
            return ScoreResult.empty();
        }

        int bestIndex = findBestIndex(scores);
        double bestScore = scores.get(bestIndex).score();
        double secondBest = findSecondBest(scores, bestIndex);

        double avgNegative = computeAverageNegative(scores, bestIndex);
        double discriminativeScore = bestScore - (PENALTY_WEIGHT * avgNegative);

        return new ScoreResult(scores, prefilterSkipped, bestIndex, bestScore, secondBest, avgNegative,
                discriminativeScore);
    }

    private double computeFusedScore(byte[] queryEmbedding, RecognitionProfile profile) {
        if (profile == null || !profile.hasEmbeddings()) {
            return 0.0;
        }

        double centroidScore = 0.0;
        if (profile.centroid() != null) {
            centroidScore = RecognitionEmbeddingUtils.cosineSimilarity(queryEmbedding, profile.centroid());
        }

        List<byte[]> embeddings = profile.embeddings();
        double[] similarities = new double[embeddings.size()];
        for (int i = 0; i < embeddings.size(); i++) {
            similarities[i] = embeddingGenerator.calculateSimilarity(queryEmbedding, embeddings.get(i));
        }
        Arrays.sort(similarities);

        int k = Math.min(Math.max(5, similarities.length / 3), similarities.length);
        double sumTopK = 0.0;
        for (int i = 0; i < k; i++) {
            sumTopK += similarities[similarities.length - 1 - i];
        }
        double maxTopK = similarities[similarities.length - 1];
        double avgTopK = sumTopK / k;
        double exemplarScore = 0.75 * maxTopK + 0.25 * avgTopK;

        double fused = (profile.centroid() != null)
                ? (0.25 * centroidScore + 0.75 * exemplarScore)
                : exemplarScore;

        return Math.min(1.0, Math.max(0.0, fused));
    }

    private int findBestIndex(List<ProfileScore> scores) {
        int best = 0;
        for (int i = 1; i < scores.size(); i++) {
            if (scores.get(i).score() > scores.get(best).score()) {
                best = i;
            }
        }
        return best;
    }

    private double findSecondBest(List<ProfileScore> scores, int bestIndex) {
        double second = 0.0;
        for (int i = 0; i < scores.size(); i++) {
            if (i == bestIndex) {
                continue;
            }
            second = Math.max(second, scores.get(i).score());
        }
        return second;
    }

    private double computeAverageNegative(List<ProfileScore> scores, int bestIndex) {
        double sum = 0.0;
        int count = 0;
        for (int i = 0; i < scores.size(); i++) {
            if (i == bestIndex) {
                continue;
            }
            sum += scores.get(i).score();
            count++;
        }
        return count > 0 ? sum / count : 0.0;
    }

    record ProfileScore(RecognitionProfile profile, double score) {
    }

    static final class ScoreResult {
        private static final ScoreResult EMPTY = new ScoreResult(List.of(), 0, -1, 0.0, 0.0, 0.0, 0.0);

        private final List<ProfileScore> scores;
        private final int prefilterSkipped;
        private final int bestIndex;
        private final double bestScore;
        private final double secondBestScore;
        private final double averageNegativeScore;
        private final double discriminativeScore;

        ScoreResult(List<ProfileScore> scores, int prefilterSkipped, int bestIndex, double bestScore,
                double secondBestScore, double averageNegativeScore, double discriminativeScore) {
            this.scores = List.copyOf(scores);
            this.prefilterSkipped = prefilterSkipped;
            this.bestIndex = bestIndex;
            this.bestScore = bestScore;
            this.secondBestScore = secondBestScore;
            this.averageNegativeScore = averageNegativeScore;
            this.discriminativeScore = discriminativeScore;
        }

        static ScoreResult empty() {
            return EMPTY;
        }

        List<ProfileScore> scores() {
            return scores;
        }

        int prefilterSkipped() {
            return prefilterSkipped;
        }

        int bestIndex() {
            return bestIndex;
        }

        double bestScore() {
            return bestScore;
        }

        double secondBestScore() {
            return secondBestScore;
        }

        double averageNegativeScore() {
            return averageNegativeScore;
        }

        double discriminativeScore() {
            return discriminativeScore;
        }

        boolean isEmpty() {
            return scores.isEmpty();
        }
    }
}







