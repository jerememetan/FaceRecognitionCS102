package app.test;

import org.opencv.core.*;
import app.service.FaceEmbeddingGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;


public class CheckStoredEmbeddings {
    public static void main(String[] args) throws IOException {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        FaceEmbeddingGenerator embGen = new FaceEmbeddingGenerator();

       
        String folder = "data\\facedata\\S13234_Jin_Rae";
        java.io.File dir = new java.io.File(folder);
        java.io.File[] embFiles = dir.listFiles((d, name) -> name.endsWith(".emb"));

        if (embFiles == null || embFiles.length == 0) {
            System.err.println("No embedding files found");
            return;
        }

        System.out.println("Found " + embFiles.length + " embedding files");

        // Load all embeddings
        List<byte[]> embeddings = new ArrayList<>();
        for (java.io.File f : embFiles) {
            byte[] emb = Files.readAllBytes(Paths.get(f.getAbsolutePath()));
            embeddings.add(emb);
        }

        // Calculate similarity matrix
        System.out.println("\n=== Similarity Matrix (first 5x5) ===");
        for (int i = 0; i < Math.min(5, embeddings.size()); i++) {
            for (int j = 0; j < Math.min(5, embeddings.size()); j++) {
                double sim = embGen.calculateSimilarity(embeddings.get(i), embeddings.get(j));
                System.out.printf("%.3f ", sim);
            }
            System.out.println();
        }

        double sumSim = 0.0;
        int count = 0;
        double minSim = 1.0;
        double maxSim = 0.0;

        for (int i = 0; i < embeddings.size(); i++) {
            for (int j = i + 1; j < embeddings.size(); j++) {
                double sim = embGen.calculateSimilarity(embeddings.get(i), embeddings.get(j));
                sumSim += sim;
                count++;
                minSim = Math.min(minSim, sim);
                maxSim = Math.max(maxSim, sim);
            }
        }

        double avgSim = sumSim / count;

        System.out.println("\n=== Statistics ===");
        System.out.println("Average similarity: " + String.format("%.4f", avgSim));
        System.out.println("Min similarity: " + String.format("%.4f", minSim));
        System.out.println("Max similarity: " + String.format("%.4f", maxSim));
        System.out.println("Expected (tightness): 0.9465");

        System.out.println("\n=== Analysis ===");
        if (Math.abs(avgSim - 0.9465) < 0.01) {
            System.out.println("✅ Stored embeddings match reported tightness (" + String.format("%.4f", avgSim) + ")");
        } else {
            System.out.println("⚠️ WARNING: Stored embeddings don't match reported tightness!");
            System.out.println("   Expected: 0.9465, Got: " + String.format("%.4f", avgSim));
        }

     
        String testImage = "data\\facedata\\S13234_Jin_Rae\\S13234_008.jpg";
        Mat image = org.opencv.imgcodecs.Imgcodecs.imread(testImage);
        if (!image.empty()) {
            Mat preprocessed = embGen.preprocessFaceForLiveRecognition(image);
            byte[] newEmb = embGen.generateEmbedding(preprocessed);
            preprocessed.release();
            image.release();

            // Compare against all stored embeddings
            double sumTestSim = 0.0;
            double minTestSim = 1.0;
            double maxTestSim = 0.0;

            for (byte[] storedEmb : embeddings) {
                double sim = embGen.calculateSimilarity(newEmb, storedEmb);
                sumTestSim += sim;
                minTestSim = Math.min(minTestSim, sim);
                maxTestSim = Math.max(maxTestSim, sim);
            }

            double avgTestSim = sumTestSim / embeddings.size();

            System.out.println("\n=== New Embedding vs Stored ===");
            System.out.println("Average: " + String.format("%.4f", avgTestSim));
            System.out.println("Min: " + String.format("%.4f", minTestSim));
            System.out.println("Max: " + String.format("%.4f", maxTestSim));

            if (avgTestSim < 0.85) {
                System.out.println("⚠️ FOUND THE ISSUE: New embedding scores " + String.format("%.4f", avgTestSim)
                        + " vs stored!");
                System.out.println("   This explains live recognition scores of 0.70-0.78");
                System.out.println("   Stored embeddings might have been generated with different preprocessing!");
            } else {
                System.out.println("✅ New embedding matches stored well (" + String.format("%.4f", avgTestSim) + ")");
            }
        }
    }
}
