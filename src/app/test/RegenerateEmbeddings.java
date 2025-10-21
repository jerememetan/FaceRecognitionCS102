package app.test;

import org.opencv.core.Core;
import app.service.FaceEmbeddingGenerator;
import app.util.ImageProcessor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Regenerates embeddings for a student folder using the current preprocessing
 * pipeline.
 */
public class RegenerateEmbeddings {

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java app.test.RegenerateEmbeddings <path_to_student_folder>");
            System.out.println("Example: java app.test.RegenerateEmbeddings \"data/facedata/S13234_Jin_Rae\"");
            return;
        }

        String studentFolder = args[0];
        File folder = new File(studentFolder);

        if (!folder.exists() || !folder.isDirectory()) {
            System.err.println("Invalid folder: " + studentFolder);
            return;
        }

        System.out.println("=".repeat(80));
        System.out.println("REGENERATING EMBEDDINGS");
        System.out.println("=".repeat(80));
        System.out.println("Student folder: " + folder.getName());
        System.out.println();

        // Get all .jpg files
        File[] imageFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".jpg"));
        if (imageFiles == null || imageFiles.length == 0) {
            System.err.println("No .jpg images found in " + studentFolder);
            return;
        }

        System.out.println("Found " + imageFiles.length + " images");

        // Delete old embeddings
        File[] oldEmbeddings = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".emb"));
        if (oldEmbeddings != null && oldEmbeddings.length > 0) {
            System.out.println("Deleting " + oldEmbeddings.length + " old embeddings...");
            for (File emb : oldEmbeddings) {
                emb.delete();
            }
        }

        System.out.println();

        // Collect image paths
        List<String> imagePaths = new ArrayList<>();
        for (File imageFile : imageFiles) {
            imagePaths.add(imageFile.getAbsolutePath());
        }

        // Generate new embeddings
        System.out.println("Generating new embeddings with MINIMAL preprocessing...");
        System.out.println("(Light denoise h=3.0 → Direct resize to 96x96)");
        System.out.println();

        FaceEmbeddingGenerator generator = new FaceEmbeddingGenerator();
        ImageProcessor imageProcessor = new ImageProcessor();

        FaceEmbeddingGenerator.BatchProcessingResult result = generator.processCapturedImages(imagePaths,
                imageProcessor, null);

        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("RESULT: " + result.getMessage());
        System.out.println("=".repeat(80));
        System.out.println("Processed: " + result.getProcessedCount() + " embeddings");
        System.out.println("Removed (outliers): " + result.getRemovedOutlierCount() + " embeddings");
        System.out.println();

        // Count final embeddings
        File[] finalEmbeddings = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".emb"));
        int finalCount = (finalEmbeddings != null) ? finalEmbeddings.length : 0;
        System.out.println("Final embedding count: " + finalCount);

        if (finalCount > 0) {
            System.out.println();
            System.out.println("✓ Embeddings regenerated successfully!");
            System.out.println("  Run EmbeddingQualityTest to check the new tightness score.");
        }
    }
}
