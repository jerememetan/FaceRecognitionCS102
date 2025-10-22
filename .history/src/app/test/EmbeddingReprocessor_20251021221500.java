package app.test;

import app.service.FaceEmbeddingGenerator;
import app.util.ImageProcessor;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility to regenerate embeddings from existing captured images.
 * Useful after algorithm improvements to update embedding quality.
 */
public class EmbeddingReprocessor {

    public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        String basePath = "data/facedata";

        if (args.length > 0) {
            // Reprocess specific student folder
            String studentFolder = args[0];
            reprocessStudent(basePath, studentFolder);
        } else {
            // Reprocess all students
            reprocessAllStudents(basePath);
        }
    }

    private static void reprocessAllStudents(String basePath) {
        System.out.println("=== REPROCESSING ALL STUDENTS ===\n");

        File baseDir = new File(basePath);
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            System.err.println("Base directory not found: " + basePath);
            return;
        }

        File[] studentFolders = baseDir.listFiles(File::isDirectory);
        if (studentFolders == null || studentFolders.length == 0) {
            System.out.println("No student folders found in " + basePath);
            return;
        }

        int totalStudents = studentFolders.length;
        int processedStudents = 0;

        for (File studentFolder : studentFolders) {
            System.out.println("\n" + "=".repeat(80));
            System.out.println("Processing: " + studentFolder.getName());
            System.out.println("=".repeat(80));

            if (reprocessStudentFolder(studentFolder)) {
                processedStudents++;
            }
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("REPROCESSING COMPLETE");
        System.out.println("=".repeat(80));
        System.out.println("Students processed: " + processedStudents + "/" + totalStudents);
    }

    private static void reprocessStudent(String basePath, String studentFolderName) {
        System.out.println("=== REPROCESSING STUDENT: " + studentFolderName + " ===\n");

        File studentFolder = new File(basePath, studentFolderName);
        if (!studentFolder.exists() || !studentFolder.isDirectory()) {
            System.err.println("Student folder not found: " + studentFolder.getAbsolutePath());
            return;
        }

        reprocessStudentFolder(studentFolder);
    }

    private static boolean reprocessStudentFolder(File studentFolder) {
        try {
            // Find all .jpg images in folder
            List<String> imageFiles = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(studentFolder.toPath(), 1)) {
                imageFiles = paths
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().toLowerCase().endsWith(".jpg"))
                        .map(Path::toString)
                        .sorted()
                        .collect(Collectors.toList());
            }

            if (imageFiles.isEmpty()) {
                System.out.println("  No .jpg images found - skipping");
                return false;
            }

            System.out.println("  Found " + imageFiles.size() + " images");

            // Delete all existing .emb files first
            System.out.println("  Deleting old embeddings...");
            int deletedCount = 0;
            try (Stream<Path> paths = Files.walk(studentFolder.toPath(), 1)) {
                List<Path> embFiles = paths
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().toLowerCase().endsWith(".emb"))
                        .collect(Collectors.toList());

                for (Path embFile : embFiles) {
                    Files.delete(embFile);
                    deletedCount++;
                }
            }
            System.out.println("  Deleted " + deletedCount + " old embeddings");

            // Regenerate embeddings with improved algorithm
            System.out.println("  Regenerating embeddings with improved algorithm...");

            FaceEmbeddingGenerator generator = new FaceEmbeddingGenerator();
            ImageProcessor processor = new ImageProcessor();

            FaceEmbeddingGenerator.ProgressCallback callback = new FaceEmbeddingGenerator.ProgressCallback() {
                @Override
                public void onProgress(String message) {
                    System.out.println("  " + message);
                }
            };

            FaceEmbeddingGenerator.BatchProcessingResult result = generator.processCapturedImages(imageFiles, processor,
                    callback);

            System.out.println("\n  Results:");
            System.out.println("  - Generated: " + result.getProcessedCount() + " embeddings");
            System.out.println("  - Removed outliers: " + result.getRemovedOutlierCount());
            System.out.println("  - Removed weak: " + result.getRemovedWeakCount());
            System.out.println("  - Total removed: " + result.getTotalRemovedCount());
            System.out.println("  - Final count: " + (result.getProcessedCount() - result.getTotalRemovedCount()));
            System.out.println("  - Status: " + result.getMessage());

            return result.isSuccess();

        } catch (Exception e) {
            System.err.println("  Error processing " + studentFolder.getName() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
