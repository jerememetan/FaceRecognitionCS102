package service.embedding;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;
import util.ImageProcessor;
import config.*;

/**
 * Coordinates batch processing of captured face images into embeddings.
 */
public class EmbeddingBatchProcessor {

    private final FaceEmbeddingGenerator embeddingGenerator;
    private final EmbeddingValidator embeddingValidator;
    private final EmbeddingQualityAnalyzer qualityAnalyzer;

    public EmbeddingBatchProcessor(FaceEmbeddingGenerator embeddingGenerator,
                                   EmbeddingValidator embeddingValidator,
                                   EmbeddingQualityAnalyzer qualityAnalyzer) {
        this.embeddingGenerator = embeddingGenerator;
        this.embeddingValidator = embeddingValidator;
        this.qualityAnalyzer = qualityAnalyzer;
    }

    public FaceEmbeddingGenerator.BatchProcessingResult processCapturedImages(
            List<String> imagePaths,
            List<Rect> faceRects,
            ImageProcessor imageProcessor,
            FaceEmbeddingGenerator.ProgressCallback progressCallback) {

        if (imagePaths == null || imagePaths.isEmpty()) {
            return new FaceEmbeddingGenerator.BatchProcessingResult(0, 0, 0, "No images to process");
        }

        if (progressCallback != null) {
            progressCallback.onProgress("Processing captured images...");
        }

        int processedCount = 0;
        List<byte[]> generatedEmbeddings = new ArrayList<>();
        List<String> embeddingPaths = new ArrayList<>();
        List<String> successfulImagePaths = new ArrayList<>();

        for (int i = 0; i < imagePaths.size(); i++) {
            String imagePath = imagePaths.get(i);
            Rect faceRect = (faceRects != null && i < faceRects.size()) ? faceRects.get(i) : null;

            try {
                Mat image = Imgcodecs.imread(imagePath);
                if (image.empty()) {
                    System.err.println("❌ Failed to read captured image: " + imagePath);
                    continue;
                }

                Mat faceROI = extractFaceRegion(image, faceRect);
                image.release();

                byte[] embedding = embeddingGenerator.generateEmbedding(faceROI);
                faceROI.release();

                if (embedding != null && embeddingValidator.isValid(embedding, embeddingGenerator.isDeepLearningAvailable())) {
                    String embPath = imagePath.replace(".png", ".emb");
                    try {
                        Files.write(Path.of(embPath), embedding);
                        generatedEmbeddings.add(embedding);
                        embeddingPaths.add(embPath);
                        successfulImagePaths.add(imagePath);
                        processedCount++;
                        AppLogger.info("✓ Saved embedding: " + new File(embPath).getName());
                    } catch (Exception e) {
                        System.err.println("❌ Failed to save embedding for " + imagePath + ": " + e.getMessage());
                    }
                } else {
                    System.err.println("❌ Invalid embedding generated for: " + imagePath);
                }
            } catch (Exception e) {
                System.err.println("❌ Error processing image " + imagePath + ": " + e.getMessage());
            }
        }

        AppLogger.info("Embedding generation complete: " + processedCount + "/" + imagePaths.size() + " successful");

        if (processedCount < imagePaths.size() && progressCallback != null) {
            progressCallback.onProgress(
                    "Warning: Only " + processedCount + "/" + imagePaths.size() + " embeddings generated successfully");
        }

        int outlierRemoved = 0;
        int weakRemoved = 0;

        if (generatedEmbeddings.size() >= 5) {
            if (progressCallback != null) {
                progressCallback.onProgress("Analyzing embedding quality...");
            }
            outlierRemoved = qualityAnalyzer.removeOutliers(generatedEmbeddings, embeddingPaths,
                    successfulImagePaths, progressCallback);
            if (outlierRemoved > 0) {
                AppLogger.info("✓ Auto-removed " + outlierRemoved + " outlier embedding(s)");
            } else {
                AppLogger.info("✓ All embeddings passed outlier check");
            }

            if (generatedEmbeddings.size() >= 3) {
                weakRemoved = qualityAnalyzer.removeWeakEmbeddings(generatedEmbeddings, embeddingPaths,
                        successfulImagePaths, progressCallback);
                if (weakRemoved > 0) {
                    AppLogger.info("✓ Auto-removed " + weakRemoved + " weak embedding(s)");
                } else {
                    AppLogger.info("✓ All embeddings passed weakness check");
                }
            }
        }

        String message = processedCount > 0
                ? "Successfully processed " + processedCount + " embeddings"
                : "Failed to process embeddings";

        return new FaceEmbeddingGenerator.BatchProcessingResult(processedCount, outlierRemoved, weakRemoved, message);
    }

    private Mat extractFaceRegion(Mat sourceImage, Rect faceRect) {
        if (faceRect != null && isValidRect(faceRect, sourceImage.width(), sourceImage.height())) {
            return new Mat(sourceImage, faceRect);
        }
        return sourceImage.clone();
    }

    private boolean isValidRect(Rect rect, int imageWidth, int imageHeight) {
        return rect.x >= 0 && rect.y >= 0
                && rect.width > 0 && rect.height > 0
                && rect.x + rect.width <= imageWidth
                && rect.y + rect.height <= imageHeight;
    }
}







