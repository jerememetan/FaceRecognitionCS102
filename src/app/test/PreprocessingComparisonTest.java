package app.test;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import app.util.ImageProcessor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Test to compare embedding quality with different preprocessing pipelines.
 * Goal: Determine if excessive preprocessing is preventing 0.9+ tightness
 * scores.
 */
public class PreprocessingComparisonTest {

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    // Test different preprocessing strategies
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java app.test.PreprocessingComparisonTest <path_to_student_folder>");
            System.out.println("Example: java app.test.PreprocessingComparisonTest \"data/facedata/S13234_Jin_Rae\"");
            return;
        }

        String studentFolder = args[0];
        File folder = new File(studentFolder);

        if (!folder.exists() || !folder.isDirectory()) {
            System.err.println("Invalid folder: " + studentFolder);
            return;
        }

        // Get all .jpg files
        File[] imageFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".jpg"));
        if (imageFiles == null || imageFiles.length == 0) {
            System.err.println("No .jpg images found in " + studentFolder);
            return;
        }

        System.out.println("=".repeat(80));
        System.out.println("PREPROCESSING COMPARISON TEST");
        System.out.println("=".repeat(80));
        System.out.println("Testing " + imageFiles.length + " images from: " + folder.getName());
        System.out.println();

        // Test 4 different preprocessing strategies
        testStrategy("CURRENT (Full Pipeline)", studentFolder, PreprocessingStrategy.FULL);
        testStrategy("MINIMAL (Grayscale + Resize)", studentFolder, PreprocessingStrategy.MINIMAL);
        testStrategy("NO_ROTATION (Skip Orientation)", studentFolder, PreprocessingStrategy.NO_ROTATION);
        testStrategy("NO_DENOISE (Skip Noise Reduction)", studentFolder, PreprocessingStrategy.NO_DENOISE);
    }

    private static void testStrategy(String strategyName, String studentFolder, PreprocessingStrategy strategy) {
        System.out.println("-".repeat(80));
        System.out.println("Testing: " + strategyName);
        System.out.println("-".repeat(80));

        File folder = new File(studentFolder);
        File[] imageFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".jpg"));

        if (imageFiles == null)
            return;

        List<byte[]> embeddings = new ArrayList<>();
        ImageProcessor imageProcessor = new ImageProcessor();

        // Generate embeddings with this strategy
        for (File imageFile : imageFiles) {
            Mat image = Imgcodecs.imread(imageFile.getAbsolutePath());
            if (image.empty())
                continue;

            Mat processed = applyPreprocessing(image, imageProcessor, strategy);
            byte[] embedding = generateTestEmbedding(processed);

            image.release();
            processed.release();

            if (embedding != null && embedding.length == 512) {
                embeddings.add(embedding);
            }
        }

        // Calculate quality metrics
        if (embeddings.size() >= 2) {
            double avgSimilarity = calculateAverageSimilarity(embeddings);
            double tightness = calculateTightness(embeddings);

            System.out.printf("Embeddings: %d\n", embeddings.size());
            System.out.printf("Avg Similarity: %.4f\n", avgSimilarity);
            System.out.printf("Tightness: %.4f (%s)\n", tightness, getQualityRating(tightness));
            System.out.println();
        } else {
            System.out.println("Not enough embeddings generated.\n");
        }
    }

    private static Mat applyPreprocessing(Mat image, ImageProcessor processor, PreprocessingStrategy strategy) {
        switch (strategy) {
            case FULL:
                // Current full pipeline: orientation → denoise → preprocess
                Mat aligned = processor.correctFaceOrientation(image);
                Mat denoised = processor.reduceNoise(aligned);
                Mat processed = processor.preprocessFaceImage(denoised);
                aligned.release();
                denoised.release();
                return processed;

            case MINIMAL:
                // Minimal: just grayscale + resize (highest quality)
                Mat gray = new Mat();
                if (image.channels() > 1) {
                    Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
                } else {
                    gray = image.clone();
                }
                Mat resized = new Mat();
                Imgproc.resize(gray, resized, new Size(200, 200), 0, 0, Imgproc.INTER_CUBIC);
                gray.release();
                return resized;

            case NO_ROTATION:
                // Skip orientation correction (may help if rotation introduces artifacts)
                Mat denoised2 = processor.reduceNoise(image);
                Mat processed2 = processor.preprocessFaceImage(denoised2);
                denoised2.release();
                return processed2;

            case NO_DENOISE:
                // Skip noise reduction (may help preserve detail)
                Mat aligned3 = processor.correctFaceOrientation(image);
                Mat processed3 = processor.preprocessFaceImage(aligned3);
                aligned3.release();
                return processed3;

            default:
                return image.clone();
        }
    }

    // Simplified embedding generation for testing (using dot product as feature
    // vector)
    private static byte[] generateTestEmbedding(Mat processedFace) {
        try {
            // Resize to 96x96 (OpenFace input size)
            Mat resized = new Mat();
            Imgproc.resize(processedFace, resized, new Size(96, 96), 0, 0, Imgproc.INTER_CUBIC);

            // Normalize to 0-1 range
            Mat normalized = new Mat();
            resized.convertTo(normalized, CvType.CV_32F, 1.0 / 255.0);

            // Create a simple embedding from image statistics (for comparison purposes)
            // In production, this would use the actual OpenFace model
            byte[] embedding = new byte[512];

            // Extract features: mean, stddev, histogram bins, etc.
            MatOfDouble mean = new MatOfDouble();
            MatOfDouble stddev = new MatOfDouble();
            Core.meanStdDev(normalized, mean, stddev);

            // Use image statistics as a proxy for embedding
            float[] pixels = new float[normalized.rows() * normalized.cols()];
            normalized.get(0, 0, pixels);

            // Create embedding from downsampled pixel values
            for (int i = 0; i < 512 && i < pixels.length; i++) {
                embedding[i] = (byte) (pixels[i * (pixels.length / 512)] * 255);
            }

            resized.release();
            normalized.release();

            return embedding;
        } catch (Exception e) {
            System.err.println("Error generating embedding: " + e.getMessage());
            return null;
        }
    }

    private static double calculateAverageSimilarity(List<byte[]> embeddings) {
        if (embeddings.size() < 2)
            return 0.0;

        double totalSimilarity = 0.0;
        int comparisons = 0;

        for (int i = 0; i < embeddings.size(); i++) {
            for (int j = i + 1; j < embeddings.size(); j++) {
                totalSimilarity += cosineSimilarity(embeddings.get(i), embeddings.get(j));
                comparisons++;
            }
        }

        return comparisons > 0 ? totalSimilarity / comparisons : 0.0;
    }

    private static double calculateTightness(List<byte[]> embeddings) {
        if (embeddings.size() < 2)
            return 0.0;

        double[] avgSimilarities = new double[embeddings.size()];

        for (int i = 0; i < embeddings.size(); i++) {
            double sum = 0.0;
            for (int j = 0; j < embeddings.size(); j++) {
                if (i != j) {
                    sum += cosineSimilarity(embeddings.get(i), embeddings.get(j));
                }
            }
            avgSimilarities[i] = sum / (embeddings.size() - 1);
        }

        double mean = Arrays.stream(avgSimilarities).average().orElse(0.0);
        return mean;
    }

    private static double cosineSimilarity(byte[] a, byte[] b) {
        if (a.length != b.length)
            return 0.0;

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            double valA = (a[i] & 0xFF) / 255.0;
            double valB = (b[i] & 0xFF) / 255.0;
            dotProduct += valA * valB;
            normA += valA * valA;
            normB += valB * valB;
        }

        if (normA == 0 || normB == 0)
            return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static String getQualityRating(double tightness) {
        if (tightness >= 0.90)
            return "EXCELLENT ⭐";
        if (tightness >= 0.85)
            return "EXCELLENT";
        if (tightness >= 0.75)
            return "GOOD";
        if (tightness >= 0.65)
            return "FAIR";
        return "POOR";
    }

    private enum PreprocessingStrategy {
        FULL, // Current: orientation → denoise → preprocess
        MINIMAL, // Just grayscale + resize (no filters)
        NO_ROTATION, // Skip orientation correction
        NO_DENOISE // Skip noise reduction
    }
}
