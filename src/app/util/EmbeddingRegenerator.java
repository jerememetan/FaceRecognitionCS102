package app.util;

import app.service.FaceEmbeddingGenerator;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import ConfigurationAndLogging.AppConfig;
import ConfigurationAndLogging.AppLogger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Utility tool to regenerate all embeddings with proper L2 normalization.
 * 
 * IMPORTANT: Run this tool after upgrading FaceEmbeddingGenerator to include L2
 * normalization.
 * This will regenerate all .emb files in the database with L2-normalized
 * embeddings.
 * 
 * Usage:
 * java -cp ... app.util.EmbeddingRegenerator [--backup] [--force]
 * 
 * Options:
 * --backup : Create .emb.backup files before regenerating
 * --force : Regenerate even if embeddings appear to be normalized
 */
public class EmbeddingRegenerator {

    static {
        System.load(new File("lib/opencv_java480.dll").getAbsolutePath());
    }

    private static boolean createBackups = false;
    private static boolean forceRegeneration = false;

    public static void main(String[] args) {
        parseArguments(args);

        System.out.println("===========================================");
        System.out.println("  Face Embedding Regeneration Tool");
        System.out.println("  L2 Normalization Update");
        System.out.println("===========================================\n");

        if (createBackups) {
            System.out.println("✓ Backup mode: .emb.backup files will be created");
        }
        if (forceRegeneration) {
            System.out.println("✓ Force mode: All embeddings will be regenerated");
        }
        System.out.println();

        String dbPath = AppConfig.getInstance().getDatabaseStoragePath();
        File dbDir = new File(dbPath);

        if (!dbDir.exists() || !dbDir.isDirectory()) {
            System.err.println("❌ Database path not found: " + dbPath);
            System.err.println("Please check app.properties configuration.");
            return;
        }

        System.out.println("Database path: " + dbPath);
        System.out.println();

        ImageProcessor processor = new ImageProcessor();
        FaceEmbeddingGenerator embGen = new FaceEmbeddingGenerator();

        if (!embGen.isDeepLearningAvailable()) {
            System.err.println("❌ Deep learning model not available!");
            System.err.println("Cannot regenerate embeddings without OpenFace model.");
            System.err.println("Please ensure openface.nn4.small2.v1.t7 is in data/resources/");
            return;
        }

        File[] personDirs = dbDir.listFiles(File::isDirectory);
        if (personDirs == null || personDirs.length == 0) {
            System.out.println("No person directories found in " + dbPath);
            return;
        }

        int totalProcessed = 0;
        int totalRegenerated = 0;
        int totalSkipped = 0;
        int totalErrors = 0;

        for (File personDir : personDirs) {
            System.out.println("\n[" + personDir.getName() + "]");

            File[] imageFiles = personDir.listFiles(
                    (dir, name) -> name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".jpg"));

            if (imageFiles == null || imageFiles.length == 0) {
                System.out.println("  No images found");
                continue;
            }

            for (File imgFile : imageFiles) {
                totalProcessed++;
                String embPath = imgFile.getAbsolutePath().replaceAll("\\.[^.]+$", ".emb");
                File embFile = new File(embPath);

                try {
                    // Load existing embedding if it exists
                    boolean embExists = embFile.exists();
                    byte[] oldEmb = null;

                    if (embExists) {
                        oldEmb = Files.readAllBytes(Paths.get(embPath));

                        // Check if embedding is already normalized (unless force mode)
                        if (!forceRegeneration && isAlreadyNormalized(oldEmb)) {
                            System.out.println("  ✓ Already normalized: " + imgFile.getName());
                            totalSkipped++;
                            continue;
                        }
                    }

                    // Load and preprocess image
                    Mat img = Imgcodecs.imread(imgFile.getAbsolutePath());
                    if (img.empty()) {
                        System.out.println("  ✗ Failed to load: " + imgFile.getName());
                        totalErrors++;
                        continue;
                    }

                    // Apply preprocessing (embeddings should be from preprocessed images)
                    Mat processed;
                    if (isRawImage(img)) {
                        // Raw image needs full preprocessing
                        Mat aligned = processor.correctFaceOrientation(img);
                        Mat denoised = processor.reduceNoise(aligned);
                        processed = processor.preprocessFaceImage(denoised);
                        aligned.release();
                        denoised.release();
                    } else {
                        // Already preprocessed
                        processed = img.clone();
                    }

                    // Generate new L2-normalized embedding
                    byte[] newEmb = embGen.generateEmbedding(processed);

                    if (newEmb == null || newEmb.length == 0) {
                        System.out.println("  ✗ Embedding generation failed: " + imgFile.getName());
                        totalErrors++;
                        img.release();
                        processed.release();
                        continue;
                    }

                    // Backup old embedding if requested
                    if (createBackups && embExists) {
                        String backupPath = embPath + ".backup";
                        Files.write(Paths.get(backupPath), oldEmb);
                    }

                    // Write new embedding
                    Files.write(Paths.get(embPath), newEmb);

                    // Verify normalization
                    if (isAlreadyNormalized(newEmb)) {
                        System.out.println("  ✓ Regenerated: " + imgFile.getName());
                        totalRegenerated++;
                    } else {
                        System.out.println("  ⚠ Regenerated but verification failed: " + imgFile.getName());
                        totalRegenerated++;
                    }

                    img.release();
                    processed.release();

                } catch (Exception e) {
                    System.out.println("  ✗ Error processing: " + imgFile.getName() + " - " + e.getMessage());
                    totalErrors++;
                }
            }
        }

        System.out.println("\n===========================================");
        System.out.println("  Regeneration Summary");
        System.out.println("===========================================");
        System.out.println("Total processed:  " + totalProcessed);
        System.out.println("Regenerated:      " + totalRegenerated);
        System.out.println("Already correct:  " + totalSkipped);
        System.out.println("Errors:           " + totalErrors);
        System.out.println();

        if (totalRegenerated > 0) {
            System.out.println("✓ Embeddings have been regenerated with L2 normalization.");
            System.out.println("  Recognition accuracy should improve.");
            if (createBackups) {
                System.out.println("  Old embeddings backed up with .backup extension.");
            }
        }

        if (totalErrors > 0) {
            System.out.println("\n⚠ Some embeddings failed to regenerate.");
            System.out.println("  You may need to recapture faces for these students.");
        }
    }

    /**
     * Check if embedding is already L2-normalized by computing its L2 norm.
     * A normalized vector should have norm ≈ 1.0
     */
    private static boolean isAlreadyNormalized(byte[] embedding) {
        try {
            // Try to parse as floats (128 floats = 512 bytes)
            if (embedding.length == 512) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(embedding);
                double sumSquares = 0.0;

                for (int i = 0; i < 128; i++) {
                    float v = buffer.getFloat();
                    sumSquares += v * v;
                }

                double norm = Math.sqrt(sumSquares);

                // Check if norm is close to 1.0 (within 1% tolerance)
                return Math.abs(norm - 1.0) < 0.01;
            }

            // Unknown format
            return false;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if image is raw (color) or already preprocessed (grayscale).
     */
    private static boolean isRawImage(Mat img) {
        // Preprocessed images are grayscale
        if (img.channels() == 1) {
            return false;
        }

        // Color images with 3 channels are considered raw
        return img.channels() == 3;
    }

    private static void parseArguments(String[] args) {
        for (String arg : args) {
            if ("--backup".equalsIgnoreCase(arg)) {
                createBackups = true;
            } else if ("--force".equalsIgnoreCase(arg)) {
                forceRegeneration = true;
            } else if ("--help".equalsIgnoreCase(arg) || "-h".equalsIgnoreCase(arg)) {
                printHelp();
                System.exit(0);
            }
        }
    }

    private static void printHelp() {
        System.out.println("Face Embedding Regeneration Tool");
        System.out.println();
        System.out.println("Usage: java -cp ... app.util.EmbeddingRegenerator [OPTIONS]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --backup    Create .emb.backup files before regenerating");
        System.out.println("  --force     Regenerate all embeddings (even if already normalized)");
        System.out.println("  --help      Show this help message");
        System.out.println();
        System.out.println("This tool regenerates all face embeddings with L2 normalization.");
        System.out.println("Run this after upgrading FaceEmbeddingGenerator to include L2 normalization.");
    }
}
