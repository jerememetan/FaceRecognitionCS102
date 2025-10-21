package app.test;

import org.opencv.core.Core;
import app.service.FaceEmbeddingGenerator;
import app.util.ImageProcessor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Batch regenerates embeddings for ALL student folders using the current
 * preprocessing
 * pipeline with all the latest improvements.
 */
public class BatchRegenerateEmbeddings {

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    public static void main(String[] args) {
        System.out.println("=================================================================================");
        System.out.println("BATCH EMBEDDING REGENERATION - ALL STUDENTS");
        System.out.println("=================================================================================");
        System.out.println("Using latest face recognition pipeline:");
        System.out.println("• Increased ROI padding (0.25) for better eye detection context");
        System.out.println("• Improved face alignment with geometric validation");
        System.out.println("• Separate left/right eye cascades with constrained detection");
        System.out.println("• Heuristic fallback alignment for edge cases");
        System.out.println("• Relaxed embedding quality thresholds (abs ≥0.6, dev ≥0.5)");
        System.out.println("• Enhanced preprocessing with minimal denoising");
        System.out.println();

        String basePath = "data/facedata";
        File baseDir = new File(basePath);

        if (!baseDir.exists() || !baseDir.isDirectory()) {
            System.err.println("ERROR: Face data directory not found: " + basePath);
            return;
        }

        File[] studentFolders = baseDir.listFiles(File::isDirectory);
        if (studentFolders == null || studentFolders.length == 0) {
            System.err.println("ERROR: No student folders found in " + basePath);
            return;
        }

        System.out.println("Found " + studentFolders.length + " student folder(s):");
        for (File folder : studentFolders) {
            System.out.println("  • " + folder.getName());
        }
        System.out.println();

        FaceEmbeddingGenerator generator = new FaceEmbeddingGenerator();
        ImageProcessor imageProcessor = new ImageProcessor();

        int totalStudents = 0;
        int totalImages = 0;
        int totalProcessed = 0;
        int totalOutliersRemoved = 0;
        int totalWeakRemoved = 0;

        for (File studentFolder : studentFolders) {
            String studentName = studentFolder.getName();
            System.out.println("=".repeat(80));
            System.out.println("PROCESSING STUDENT: " + studentName);
            System.out.println("=".repeat(80));

            // Get all .jpg files
            File[] imageFiles = studentFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".jpg"));
            if (imageFiles == null || imageFiles.length == 0) {
                System.out.println("No .jpg images found in " + studentFolder.getName() + ", skipping...");
                System.out.println();
                continue;
            }

            System.out.println("Found " + imageFiles.length + " images");
            totalImages += imageFiles.length;

            // Delete old embeddings
            File[] oldEmbeddings = studentFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".emb"));
            if (oldEmbeddings != null && oldEmbeddings.length > 0) {
                System.out.println("Deleting " + oldEmbeddings.length + " old embeddings...");
                for (File emb : oldEmbeddings) {
                    emb.delete();
                }
            }

            // Collect image paths
            List<String> imagePaths = new ArrayList<>();
            for (File imageFile : imageFiles) {
                imagePaths.add(imageFile.getAbsolutePath());
            }

            // Generate new embeddings using the latest pipeline
            System.out.println("Generating new embeddings with latest preprocessing pipeline...");
            System.out.println("(Face detection → Alignment → Quality validation → Embedding generation)");
            System.out.println();

            FaceEmbeddingGenerator.BatchProcessingResult result = generator.processCapturedImages(imagePaths,
                    null, imageProcessor, null);

            System.out.println();
            System.out.println("RESULT: " + result.getMessage());
            System.out.println("Processed: " + result.getProcessedCount() + " embeddings");
            System.out.println("Removed (outliers): " + result.getRemovedOutlierCount() + " embeddings");
            System.out.println("Removed (weak): " + result.getRemovedWeakCount() + " embeddings");
            System.out.println("Total removed: " + result.getTotalRemovedCount() + " embeddings");

            // Count final embeddings
            File[] finalEmbeddings = studentFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".emb"));
            int finalCount = (finalEmbeddings != null) ? finalEmbeddings.length : 0;
            System.out.println("Final embedding count: " + finalCount);

            if (finalCount > 0) {
                System.out.println("✓ Embeddings regenerated successfully for " + studentName);
            } else {
                System.out.println("⚠ No embeddings generated for " + studentName);
            }

            totalStudents++;
            totalProcessed += result.getProcessedCount();
            totalOutliersRemoved += result.getRemovedOutlierCount();
            totalWeakRemoved += result.getRemovedWeakCount();

            System.out.println();
        }

        // Final summary
        System.out.println("=================================================================================");
        System.out.println("BATCH REGENERATION COMPLETE");
        System.out.println("=================================================================================");
        System.out.println("Students processed: " + totalStudents);
        System.out.println("Total images found: " + totalImages);
        System.out.println("Total embeddings generated: " + totalProcessed);
        System.out.println("Total outliers removed: " + totalOutliersRemoved);
        System.out.println("Total weak embeddings removed: " + totalWeakRemoved);
        System.out.println("Total embeddings removed: " + (totalOutliersRemoved + totalWeakRemoved));
        System.out.println();

        if (totalProcessed > 0) {
            System.out.println("✓ All embeddings have been regenerated with the latest pipeline!");
            System.out.println("  Run EmbeddingQualityTest to verify embedding quality and tightness scores.");
            System.out.println("  Restart the recognition application to use the new embeddings.");
        } else {
            System.out.println("⚠ No embeddings were generated. Check the logs for errors.");
        }
    }
}