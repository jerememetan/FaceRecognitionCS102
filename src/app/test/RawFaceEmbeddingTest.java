package app.test;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.dnn.Net;
import org.opencv.dnn.Dnn;

import java.io.File;
import java.util.*;

/**
 * Test to compare embedding quality using RAW faces vs PREPROCESSED faces.
 * Goal: Determine if feeding raw 400x400 images directly to OpenFace model
 * improves tightness scores beyond 0.9+
 */
public class RawFaceEmbeddingTest {

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    private static final Size INPUT_SIZE = new Size(96, 96);
    private static Net embeddingNet;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java app.test.RawFaceEmbeddingTest <path_to_student_folder>");
            System.out.println("Example: java app.test.RawFaceEmbeddingTest \"data/facedata/S13234_Jin_Rae\"");
            return;
        }

        String studentFolder = args[0];
        File folder = new File(studentFolder);

        if (!folder.exists() || !folder.isDirectory()) {
            System.err.println("Invalid folder: " + studentFolder);
            return;
        }

        // Initialize OpenFace model
        if (!initializeOpenFaceModel()) {
            System.err.println("Failed to initialize OpenFace model!");
            return;
        }

        // Get all .jpg files
        File[] imageFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".jpg"));
        if (imageFiles == null || imageFiles.length == 0) {
            System.err.println("No .jpg images found in " + studentFolder);
            return;
        }

        System.out.println("=".repeat(80));
        System.out.println("RAW FACE EMBEDDING QUALITY TEST");
        System.out.println("=".repeat(80));
        System.out.println("Testing " + imageFiles.length + " images from: " + folder.getName());
        System.out.println();

        // Test with current preprocessing (full pipeline)
        testEmbeddings("CURRENT (Full Preprocessing Pipeline)", imageFiles, ProcessingMode.FULL);

        // Test with minimal preprocessing (raw faces, just resize)
        testEmbeddings("MINIMAL (Raw → Resize Only)", imageFiles, ProcessingMode.MINIMAL);

        // Test with no preprocessing at all (400x400 direct resize)
        testEmbeddings("RAW (Direct 400x400 → 96x96)", imageFiles, ProcessingMode.RAW);

        System.out.println("=".repeat(80));
    }

    private static boolean initializeOpenFaceModel() {
        try {
            String modelPath = "data/resources/openface.nn4.small2.v1.t7";
            embeddingNet = Dnn.readNetFromTorch(modelPath);

            if (embeddingNet.empty()) {
                System.err.println("Failed to load OpenFace model from: " + modelPath);
                return false;
            }

            System.out.println("✓ OpenFace model loaded successfully");
            System.out.println();
            return true;
        } catch (Exception e) {
            System.err.println("Error loading OpenFace model: " + e.getMessage());
            return false;
        }
    }

    private static void testEmbeddings(String testName, File[] imageFiles, ProcessingMode mode) {
        System.out.println("-".repeat(80));
        System.out.println("Testing: " + testName);
        System.out.println("-".repeat(80));

        List<byte[]> embeddings = new ArrayList<>();
        app.util.ImageProcessor imageProcessor = new app.util.ImageProcessor();

        // Generate embeddings with this processing mode
        for (File imageFile : imageFiles) {
            Mat image = Imgcodecs.imread(imageFile.getAbsolutePath());
            if (image.empty()) {
                System.err.println("Failed to read: " + imageFile.getName());
                continue;
            }

            Mat processed = processImage(image, imageProcessor, mode);
            byte[] embedding = generateOpenFaceEmbedding(processed);

            image.release();
            processed.release();

            if (embedding != null && isEmbeddingValid(embedding)) {
                embeddings.add(embedding);
                System.out.println("  ✓ " + imageFile.getName() + " → embedding generated");
            } else {
                System.err.println("  ✗ " + imageFile.getName() + " → invalid embedding");
            }
        }

        System.out.println();

        // Calculate quality metrics
        if (embeddings.size() >= 2) {
            double[] avgSimilarities = calculateAverageSimilarities(embeddings);
            double tightness = Arrays.stream(avgSimilarities).average().orElse(0.0);
            double stdDev = calculateStdDev(avgSimilarities, tightness);
            double minSimilarity = Arrays.stream(avgSimilarities).min().orElse(0.0);
            double maxSimilarity = Arrays.stream(avgSimilarities).max().orElse(0.0);

            System.out.printf("Results:\n");
            System.out.printf("  Embeddings Generated: %d\n", embeddings.size());
            System.out.printf("  Tightness Score: %.4f (%s)\n", tightness, getQualityRating(tightness));
            System.out.printf("  Std Deviation: %.4f\n", stdDev);
            System.out.printf("  Min Similarity: %.4f\n", minSimilarity);
            System.out.printf("  Max Similarity: %.4f\n", maxSimilarity);

            // Check for outliers
            double outlierThreshold = Math.max(0.70, tightness - 1.5 * stdDev);
            int outliers = 0;
            for (double sim : avgSimilarities) {
                if (sim < outlierThreshold)
                    outliers++;
            }
            System.out.printf("  Outliers (< %.4f): %d\n", outlierThreshold, outliers);
            System.out.println();
        } else {
            System.out.println("Not enough embeddings generated for analysis.\n");
        }
    }

    private static Mat processImage(Mat image, app.util.ImageProcessor processor, ProcessingMode mode) {
        switch (mode) {
            case FULL:
                // Current full pipeline: orientation → denoise → preprocess
                Mat aligned = processor.correctFaceOrientation(image);
                Mat denoised = processor.reduceNoise(aligned);
                Mat processed = processor.preprocessFaceImage(denoised);
                aligned.release();
                denoised.release();
                return processed;

            case MINIMAL:
                // Minimal: Light denoise (h=3.0) + direct resize to 96x96
                Mat minimalDenoised = new Mat();
                if (image.channels() == 1) {
                    org.opencv.photo.Photo.fastNlMeansDenoising(image, minimalDenoised, 3.0f, 7, 21);
                } else {
                    org.opencv.photo.Photo.fastNlMeansDenoisingColored(image, minimalDenoised, 3.0f, 3.0f, 7, 21);
                }

                Mat minimalResized = new Mat();
                Imgproc.resize(minimalDenoised, minimalResized, INPUT_SIZE, 0, 0, Imgproc.INTER_CUBIC);
                minimalDenoised.release();
                return minimalResized;

            case RAW:
                // Raw: Direct resize 400x400 → 96x96 (no preprocessing at all)
                Mat rawResized = new Mat();
                Imgproc.resize(image, rawResized, INPUT_SIZE, 0, 0, Imgproc.INTER_CUBIC);
                return rawResized;

            default:
                return image.clone();
        }
    }

    private static byte[] generateOpenFaceEmbedding(Mat processedFace) {
        try {
            // Ensure image is in BGR format (OpenFace expects BGR)
            Mat colorImage = new Mat();
            if (processedFace.channels() == 1) {
                Imgproc.cvtColor(processedFace, colorImage, Imgproc.COLOR_GRAY2BGR);
            } else {
                colorImage = processedFace.clone();
            }

            // Resize to 96x96 if not already (for FULL mode which outputs 200x200)
            Mat resized = new Mat();
            if (colorImage.width() != 96 || colorImage.height() != 96) {
                Imgproc.resize(colorImage, resized, INPUT_SIZE, 0, 0, Imgproc.INTER_CUBIC);
            } else {
                resized = colorImage.clone();
            }

            // Normalize to 0-1 range
            resized.convertTo(resized, CvType.CV_32F, 1.0 / 255.0);

            // Create blob for OpenFace model
            Mat blob = Dnn.blobFromImage(resized, 1.0, INPUT_SIZE, new Scalar(0, 0, 0), true, false);

            // Forward pass through network
            embeddingNet.setInput(blob);
            Mat embedding = embeddingNet.forward();

            // Convert to byte array
            byte[] embeddingBytes = matToByteArray(embedding);

            // Cleanup
            colorImage.release();
            resized.release();
            blob.release();
            embedding.release();

            return embeddingBytes;

        } catch (Exception e) {
            System.err.println("Error generating OpenFace embedding: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static byte[] matToByteArray(Mat mat) {
        try {
            // OpenFace outputs 128-dimensional float embeddings
            int embeddingSize = (int) mat.total();
            float[] floatArray = new float[embeddingSize];
            mat.get(0, 0, floatArray);

            // Convert float array to byte array
            java.nio.ByteBuffer byteBuffer = java.nio.ByteBuffer.allocate(embeddingSize * 4);
            for (float f : floatArray) {
                byteBuffer.putFloat(f);
            }
            return byteBuffer.array();
        } catch (Exception e) {
            System.err.println("Error converting Mat to byte array: " + e.getMessage());
            return null;
        }
    }

    private static boolean isEmbeddingValid(byte[] embedding) {
        if (embedding == null || embedding.length != 512) {
            return false;
        }

        // Check if embedding is not all zeros
        for (byte b : embedding) {
            if (b != 0)
                return true;
        }
        return false;
    }

    private static double[] calculateAverageSimilarities(List<byte[]> embeddings) {
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

        return avgSimilarities;
    }

    private static double calculateStdDev(double[] values, double mean) {
        double sumSquaredDiff = 0.0;
        for (double value : values) {
            double diff = value - mean;
            sumSquaredDiff += diff * diff;
        }
        return Math.sqrt(sumSquaredDiff / values.length);
    }

    private static double cosineSimilarity(byte[] a, byte[] b) {
        if (a.length != b.length)
            return 0.0;

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        // Convert bytes to floats (assuming IEEE 754 float encoding in 4 bytes)
        for (int i = 0; i < a.length; i += 4) {
            if (i + 3 < a.length) {
                float valA = bytesToFloat(a[i], a[i + 1], a[i + 2], a[i + 3]);
                float valB = bytesToFloat(b[i], b[i + 1], b[i + 2], b[i + 3]);

                dotProduct += valA * valB;
                normA += valA * valA;
                normB += valB * valB;
            }
        }

        if (normA == 0 || normB == 0)
            return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static float bytesToFloat(byte b1, byte b2, byte b3, byte b4) {
        int intBits = (b4 & 0xFF) | ((b3 & 0xFF) << 8) | ((b2 & 0xFF) << 16) | ((b1 & 0xFF) << 24);
        return Float.intBitsToFloat(intBits);
    }

    private static String getQualityRating(double tightness) {
        if (tightness >= 0.90)
            return "EXCELLENT ⭐⭐⭐";
        if (tightness >= 0.85)
            return "EXCELLENT ⭐";
        if (tightness >= 0.75)
            return "GOOD";
        if (tightness >= 0.65)
            return "FAIR";
        return "POOR";
    }

    private enum ProcessingMode {
        FULL, // Current: orientation → denoise → preprocess (grayscale + filters)
        MINIMAL, // Light denoise (h=3.0) + direct resize to 96x96
        RAW // Direct resize 400x400 → 96x96 (no preprocessing)
    }
}
