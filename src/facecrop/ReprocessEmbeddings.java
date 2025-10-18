package facecrop;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import app.service.FaceEmbeddingGenerator;
import app.util.ImageProcessor;
import ConfigurationAndLogging.AppLogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ReprocessEmbeddings {

    static {
        System.load(new File("lib/opencv_java480.dll").getAbsolutePath());
    }

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("Embedding Regeneration Tool");
        System.out.println("Using CLAHE=1.0 for consistent preprocessing");
        System.out.println("========================================\n");

        String basePath = "data/facedata";
        File baseDir = new File(basePath);

        if (!baseDir.exists() || !baseDir.isDirectory()) {
            System.err.println("ERROR: Face data directory not found: " + basePath);
            return;
        }

        FaceEmbeddingGenerator embGen = new FaceEmbeddingGenerator();
        ImageProcessor imgProc = new ImageProcessor();

        int totalProcessed = 0;
        int totalFailed = 0;

        File[] personFolders = baseDir.listFiles(File::isDirectory);
        if (personFolders == null || personFolders.length == 0) {
            System.err.println("ERROR: No person folders found in " + basePath);
            return;
        }

        for (File personFolder : personFolders) {
            String personName = personFolder.getName();
            System.out.println("\nProcessing: " + personName);
            System.out.println("----------------------------------------");

            File[] imageFiles = personFolder.listFiles(
                    (dir, name) -> name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".png"));

            if (imageFiles == null || imageFiles.length == 0) {
                System.out.println("  No images found, skipping...");
                continue;
            }

            int personProcessed = 0;
            int personFailed = 0;

            for (File imageFile : imageFiles) {
                try {
                    // Load image
                    Mat image = Imgcodecs.imread(imageFile.getAbsolutePath());
                    if (image.empty()) {
                        System.err.println("  FAILED: Could not load " + imageFile.getName());
                        personFailed++;
                        continue;
                    }

                    // Preprocess with CLAHE=1.0 (now hardcoded in ImageProcessor)
                    Mat processed = imgProc.preprocessFaceImage(image);

                    // Generate embedding
                    byte[] embedding = embGen.generateEmbedding(processed);

                    if (embedding == null || embedding.length == 0) {
                        System.err.println("  FAILED: Could not generate embedding for " + imageFile.getName());
                        personFailed++;
                        image.release();
                        processed.release();
                        continue;
                    }

                    // Save embedding (replace .jpg with .emb)
                    String embFilename = imageFile.getName().replaceAll("\\.(jpg|png)$", ".emb");
                    Path embPath = Paths.get(personFolder.getAbsolutePath(), embFilename);
                    Files.write(embPath, embedding);

                    personProcessed++;
                    System.out.print(".");
                    if (personProcessed % 50 == 0) {
                        System.out.println(" [" + personProcessed + "]");
                    }

                    // Cleanup
                    image.release();
                    processed.release();

                } catch (Exception e) {
                    System.err.println("\n  ERROR processing " + imageFile.getName() + ": " + e.getMessage());
                    personFailed++;
                }
            }

            System.out.println("\n  Processed: " + personProcessed + " / " + imageFiles.length);
            if (personFailed > 0) {
                System.out.println("  Failed: " + personFailed);
            }

            totalProcessed += personProcessed;
            totalFailed += personFailed;
        }

        System.out.println("\n========================================");
        System.out.println("SUMMARY");
        System.out.println("========================================");
        System.out.println("Total embeddings regenerated: " + totalProcessed);
        if (totalFailed > 0) {
            System.out.println("Total failures: " + totalFailed);
        }
        System.out.println("\nAll embeddings now use CLAHE=1.0!");
        System.out.println("Restart the recognition app to use new embeddings.");
    }
}
