package app.util;

import app.service.FaceEmbeddingGenerator;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import ConfigurationAndLogging.AppConfig;
import ConfigurationAndLogging.AppLogger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Diagnostic tool to check embedding consistency and regenerate if needed.
 * Run this if you suspect embeddings were generated from preprocessed images.
 */
public class EmbeddingConsistencyChecker {
    
    static {
        System.load(new File("lib/opencv_java480.dll").getAbsolutePath());
    }
    
    public static void main(String[] args) {
        System.out.println("===========================================");
        System.out.println("  Embedding Consistency Checker");
        System.out.println("===========================================\n");
        
        String dbPath = AppConfig.getInstance().getDatabaseStoragePath();
        File dbDir = new File(dbPath);
        
        if (!dbDir.exists() || !dbDir.isDirectory()) {
            System.err.println("Database path not found: " + dbPath);
            return;
        }
        
        ImageProcessor processor = new ImageProcessor();
        FaceEmbeddingGenerator embGen = new FaceEmbeddingGenerator();
        
        File[] personDirs = dbDir.listFiles(File::isDirectory);
        if (personDirs == null || personDirs.length == 0) {
            System.out.println("No person directories found in " + dbPath);
            return;
        }
        
        int totalChecked = 0;
        int totalInconsistent = 0;
        int totalRegenerated = 0;
        
        for (File personDir : personDirs) {
            System.out.println("\n[" + personDir.getName() + "]");
            File[] imageFiles = personDir.listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".jpg"));
            
            if (imageFiles == null || imageFiles.length == 0) {
                System.out.println("  No images found");
                continue;
            }
            
            for (File imgFile : imageFiles) {
                totalChecked++;
                String embPath = imgFile.getAbsolutePath().replaceAll("\\.[^.]+$", ".emb");
                File embFile = new File(embPath);
                
                if (!embFile.exists()) {
                    System.out.println("  ⚠ Missing: " + imgFile.getName() + " (no .emb file)");
                    continue;
                }
                
                try {
                    // Load saved embedding
                    byte[] savedEmb = Files.readAllBytes(Paths.get(embPath));
                    
                    // Load image and check if it's already preprocessed
                    Mat img = Imgcodecs.imread(imgFile.getAbsolutePath());
                    if (img.empty()) {
                        System.out.println("  ✗ Failed to load: " + imgFile.getName());
                        continue;
                    }
                    
                    boolean isPreprocessed = checkIfPreprocessed(img);
                    
                    // Regenerate embedding correctly
                    Mat processed = isPreprocessed 
                        ? img.clone()  // Already preprocessed, use as-is
                        : preprocessCorrectly(img, processor);  // Raw image, preprocess now
                    
                    byte[] correctEmb = embGen.generateEmbedding(processed);
                    
                    // Compare embeddings
                    double similarity = embGen.calculateSimilarity(savedEmb, correctEmb);
                    
                    if (similarity < 0.98) {
                        totalInconsistent++;
                        System.out.printf("  ⚠ Inconsistent: %s (similarity: %.3f, preprocessed: %s)%n", 
                            imgFile.getName(), similarity, isPreprocessed);
                        
                        // Ask user if they want to regenerate
                        if (args.length > 0 && args[0].equals("--fix")) {
                            // Save ORIGINAL raw image if it was preprocessed
                            if (isPreprocessed) {
                                System.out.println("    → Cannot restore original (image was preprocessed)");
                                System.out.println("    → Recommend recapturing this person's faces");
                            } else {
                                // Regenerate embedding from raw image
                                Files.write(Paths.get(embPath), correctEmb);
                                totalRegenerated++;
                                System.out.println("    ✓ Regenerated embedding");
                            }
                        }
                    } else {
                        System.out.printf("  ✓ OK: %s (similarity: %.3f)%n", imgFile.getName(), similarity);
                    }
                    
                    img.release();
                    processed.release();
                    
                } catch (Exception e) {
                    System.out.println("  ✗ Error checking: " + imgFile.getName() + " - " + e.getMessage());
                }
            }
        }
        
        System.out.println("\n===========================================");
        System.out.println("  Summary");
        System.out.println("===========================================");
        System.out.println("Total checked: " + totalChecked);
        System.out.println("Inconsistent: " + totalInconsistent);
        if (args.length > 0 && args[0].equals("--fix")) {
            System.out.println("Regenerated: " + totalRegenerated);
        } else if (totalInconsistent > 0) {
            System.out.println("\nRun with --fix to regenerate embeddings for raw images.");
            System.out.println("For preprocessed images, you must recapture the person's faces.");
        }
    }
    
    private static boolean checkIfPreprocessed(Mat img) {
        // Check if image is grayscale (preprocessed images are converted to grayscale)
        if (img.channels() == 1) {
            return true;
        }
        
        // Check if image has unusually flat histogram (sign of CLAHE preprocessing)
        Mat gray = new Mat();
        org.opencv.imgproc.Imgproc.cvtColor(img, gray, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY);
        
        Mat hist = new Mat();
        org.opencv.imgproc.Imgproc.calcHist(
            Arrays.asList(gray), 
            new MatOfInt(0), 
            new Mat(), 
            hist, 
            new MatOfInt(256), 
            new MatOfFloat(0f, 256f)
        );
        
        // Count non-zero bins
        int nonZeroBins = 0;
        for (int i = 0; i < hist.rows(); i++) {
            if (hist.get(i, 0)[0] > 0) nonZeroBins++;
        }
        
        gray.release();
        hist.release();
        
        // Preprocessed images typically use more of the histogram
        return (nonZeroBins > 200);  // Threshold for CLAHE-processed images
    }
    
    private static Mat preprocessCorrectly(Mat img, ImageProcessor processor) {
        Mat aligned = processor.correctFaceOrientation(img);
        Mat processed = processor.preprocessFaceImage(aligned);
        if (aligned != img) {
            aligned.release();
        }
        return processed;
    }
}
