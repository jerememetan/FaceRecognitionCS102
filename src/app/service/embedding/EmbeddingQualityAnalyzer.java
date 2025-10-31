package app.service.embedding;

import app.service.FaceEmbeddingGenerator.ProgressCallback;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Performs quality analysis on generated embeddings by identifying outliers
 * and weak samples.
 */
public class EmbeddingQualityAnalyzer {

    private static final double MIN_OUTLIER_THRESHOLD = 0.50;
    private static final double MIN_WEAK_THRESHOLD = 0.40;
    private static final double ABSOLUTE_WEAK_THRESHOLD = 0.58;
    private static final double RETENTION_FRACTION = 0.70;
    private static final int MIN_RETAIN_COUNT = 6;

    private final SimilarityCalculator similarityCalculator;
    private final boolean debugLogs;

    public EmbeddingQualityAnalyzer(SimilarityCalculator similarityCalculator, boolean debugLogs) {
        this.similarityCalculator = similarityCalculator;
        this.debugLogs = debugLogs;
    }

    public int removeOutliers(List<byte[]> embeddings,
                              List<String> embeddingPaths,
                              List<String> imagePaths,
                              ProgressCallback progressCallback) {
        if (embeddings == null || embeddings.size() < 5) {
            return 0;
        }

        try {
            double[] avgSimilarities = computeAverageSimilarities(embeddings);
            double mean = mean(avgSimilarities);
            double stdDev = standardDeviation(avgSimilarities, mean);
            double threshold = Math.max(MIN_OUTLIER_THRESHOLD, mean - 2.0 * stdDev);
            if (progressCallback != null) {
                progressCallback.onProgress(String.format(
                        "Embedding similarity stats: mean=%.3f, std=%.3f, min=%.3f",
                        mean, stdDev, min(avgSimilarities)));
            }

            List<Integer> outlierIndices = new ArrayList<>();
            for (int i = 0; i < avgSimilarities.length; i++) {
                if (avgSimilarities[i] < threshold) {
                    outlierIndices.add(i);
                    log(String.format("  Outlier detected: index=%d, avgSim=%.4f, threshold=%.4f",
                            i, avgSimilarities[i], threshold));
                }
            }

            int removedCount = 0;
            outlierIndices.sort(Integer::compareTo);
            int minRetained = Math.max(MIN_RETAIN_COUNT,
                    (int) Math.ceil(avgSimilarities.length * RETENTION_FRACTION));
            for (int i = outlierIndices.size() - 1; i >= 0; i--) {
                int idx = outlierIndices.get(i);
                int projectedSize = embeddings.size() - (removedCount + 1);
                if (projectedSize < minRetained) {
                    log(String.format("  Skipping outlier removal at index=%d to preserve minimum retention", idx));
                    continue;
                }
                if (deleteFilesAtIndex(idx, embeddingPaths, imagePaths)) {
                    removedCount++;
                }
                if (embeddings != null && idx >= 0 && idx < embeddings.size()) {
                    embeddings.remove(idx);
                }
                if (embeddingPaths != null && idx >= 0 && idx < embeddingPaths.size()) {
                    embeddingPaths.remove(idx);
                }
                if (imagePaths != null && idx >= 0 && idx < imagePaths.size()) {
                    imagePaths.remove(idx);
                }
            }

            if (removedCount > 0 && progressCallback != null) {
                progressCallback.onProgress("Removed " + removedCount + " outlier image(s) automatically");
            }

            return removedCount;
        } catch (Exception e) {
            System.err.println("Outlier detection failed: " + e.getMessage());
            return 0;
        }
    }

    public int removeWeakEmbeddings(List<byte[]> embeddings,
                                    List<String> embeddingPaths,
                                    List<String> imagePaths,
                                    ProgressCallback progressCallback) {
        if (embeddings == null || embeddings.size() < 3) {
            return 0;
        }

        try {
            double[] avgSimilarities = computeAverageSimilarities(embeddings);
            double mean = mean(avgSimilarities);
            double stdDev = standardDeviation(avgSimilarities, mean);
            double weakThreshold = Math.max(MIN_WEAK_THRESHOLD, mean - 0.5 * stdDev);
            if (progressCallback != null) {
                progressCallback.onProgress(String.format(
                        "Weak check threshold: %.3f (absolute %.3f)",
                        weakThreshold, ABSOLUTE_WEAK_THRESHOLD));
            }

            List<Integer> weakIndices = new ArrayList<>();
            for (int i = 0; i < avgSimilarities.length; i++) {
                boolean isWeak = avgSimilarities[i] < weakThreshold || avgSimilarities[i] < ABSOLUTE_WEAK_THRESHOLD;
                if (isWeak) {
                    weakIndices.add(i);
                    log(String.format(
                            "  Weak embedding detected: index=%d, avgSim=%.4f, threshold=%.4f, absThresh=%.4f",
                            i, avgSimilarities[i], weakThreshold, ABSOLUTE_WEAK_THRESHOLD));
                }
            }

            int removedCount = 0;
            Collections.sort(weakIndices);
            for (int i = weakIndices.size() - 1; i >= 0; i--) {
                int idx = weakIndices.get(i);
                int projectedSize = embeddings.size() - (removedCount + 1);
                if (projectedSize < Math.max(MIN_RETAIN_COUNT,
                        (int) Math.ceil(avgSimilarities.length * RETENTION_FRACTION))) {
                    log(String.format("  Skipping weak removal at index=%d to preserve minimum retention", idx));
                    continue;
                }
                if (deleteFilesAtIndex(idx, embeddingPaths, imagePaths)) {
                    removedCount++;
                }
                embeddings.remove(idx);
                embeddingPaths.remove(idx);
                imagePaths.remove(idx);
            }

            if (removedCount > 0 && progressCallback != null) {
                progressCallback.onProgress("Removed " + removedCount + " weak image(s) automatically");
            }

            return removedCount;
        } catch (Exception e) {
            System.err.println("Weak embedding detection failed: " + e.getMessage());
            return 0;
        }
    }

    private double[] computeAverageSimilarities(List<byte[]> embeddings) {
        double[] averages = new double[embeddings.size()];
        for (int i = 0; i < embeddings.size(); i++) {
            double sum = 0.0;
            int count = 0;
            for (int j = 0; j < embeddings.size(); j++) {
                if (i == j) {
                    continue;
                }
                sum += similarityCalculator.calculate(embeddings.get(i), embeddings.get(j));
                count++;
            }
            averages[i] = (count > 0) ? sum / count : 0.0;
        }
        return averages;
    }

    private double mean(double[] values) {
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return values.length > 0 ? sum / values.length : 0.0;
    }

    private double standardDeviation(double[] values, double mean) {
        double variance = 0.0;
        for (double v : values) {
            variance += Math.pow(v - mean, 2);
        }
        return values.length > 0 ? Math.sqrt(variance / values.length) : 0.0;
    }

    private double min(double[] values) {
        double result = Double.POSITIVE_INFINITY;
        for (double v : values) {
            if (v < result) {
                result = v;
            }
        }
        return values.length > 0 ? result : 0.0;
    }

    private boolean deleteFilesAtIndex(int index,
                                       List<String> embeddingPaths,
                                       List<String> imagePaths) {
        boolean deletedEmbedding = deleteFile(embeddingPaths, index, "embedding");
        deleteFile(imagePaths, index, "image");
        return deletedEmbedding;
    }

    private boolean deleteFile(List<String> paths, int index, String label) {
        if (paths == null || index < 0 || index >= paths.size()) {
            return false;
        }
        Path path = Path.of(paths.get(index));
        try {
            if (Files.exists(path)) {
                Files.delete(path);
                log("  ✗ Deleted " + label + ": " + path.getFileName());
                return true;
            }
        } catch (IOException e) {
            System.err.println("Failed to delete " + label + " at " + path + ": " + e.getMessage());
        }
        return false;
    }

    private void log(String message) {
        if (debugLogs) {
            System.out.println(message);
        }
    }
}
