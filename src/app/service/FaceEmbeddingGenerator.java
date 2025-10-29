package app.service;

import org.opencv.core.Mat;
import app.service.embedding.DeepEmbeddingGenerator;
import app.service.embedding.FeatureEmbeddingGenerator;
import app.service.embedding.SimilarityCalculator;
import app.service.embedding.EmbeddingValidator;
import app.service.embedding.EmbeddingQualityAnalyzer;
import app.service.embedding.EmbeddingBatchProcessor;

public class FaceEmbeddingGenerator {

    private final DeepEmbeddingGenerator deepEmbeddingGenerator;
    private final FeatureEmbeddingGenerator featureEmbeddingGenerator;
    private final SimilarityCalculator similarityCalculator;
    private final EmbeddingValidator embeddingValidator;
    private final EmbeddingQualityAnalyzer embeddingQualityAnalyzer;
    private final EmbeddingBatchProcessor embeddingBatchProcessor;
    private boolean isInitialized = false;

    private static final boolean DEBUG_LOGS = false;

    public FaceEmbeddingGenerator() {
        this.deepEmbeddingGenerator = new DeepEmbeddingGenerator(DEBUG_LOGS);
        this.featureEmbeddingGenerator = new FeatureEmbeddingGenerator(DEBUG_LOGS);
        this.similarityCalculator = new SimilarityCalculator(DEBUG_LOGS);
        this.embeddingValidator = new EmbeddingValidator();
        this.embeddingQualityAnalyzer = new EmbeddingQualityAnalyzer(similarityCalculator, DEBUG_LOGS);
        this.embeddingBatchProcessor = new EmbeddingBatchProcessor(this, embeddingValidator, embeddingQualityAnalyzer);
        this.isInitialized = deepEmbeddingGenerator.isAvailable();
    }

    public byte[] generateEmbedding(Mat faceImage) {
        if (isDeepLearningAvailable()) {
            return generateDeepEmbedding(faceImage);
        } else {
            return generateFeatureBasedEmbedding(faceImage);
        }
    }

    public byte[] generateEmbeddingFromBlob(Mat preprocessedBlob) {
        if (isDeepLearningAvailable() && preprocessedBlob != null && !preprocessedBlob.empty()) {
            return generateDeepEmbeddingFromBlob(preprocessedBlob);
        } else {
            // Fallback to feature-based if blob is invalid or model not available
            System.err.println("⚠️ Invalid blob or model not available, cannot generate embedding from blob");
            return null;
        }
    }

    private byte[] generateDeepEmbedding(Mat faceImage) {
        return deepEmbeddingGenerator.generate(faceImage);
    }

    private byte[] generateDeepEmbeddingFromBlob(Mat preprocessedBlob) {
        return deepEmbeddingGenerator.generateFromBlob(preprocessedBlob);
    }

    private byte[] generateFeatureBasedEmbedding(Mat faceImage) {
        return featureEmbeddingGenerator.generate(faceImage);
    }

    public double calculateSimilarity(byte[] embedding1, byte[] embedding2) {
        return similarityCalculator.calculate(embedding1, embedding2);
    }

    public boolean isDeepLearningAvailable() {
        this.isInitialized = deepEmbeddingGenerator.isAvailable();
        return isInitialized;
    }

    public boolean isEmbeddingValid(byte[] embedding) {
        return embeddingValidator.isValid(embedding, isDeepLearningAvailable());
    }

    public BatchProcessingResult processCapturedImages(
            java.util.List<String> imagePaths,
            java.util.List<org.opencv.core.Rect> faceRects,
            app.util.ImageProcessor imageProcessor,
            ProgressCallback progressCallback) {

        return embeddingBatchProcessor.processCapturedImages(imagePaths, faceRects, imageProcessor, progressCallback);
    }

    public void release() {
        deepEmbeddingGenerator.release();
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

}