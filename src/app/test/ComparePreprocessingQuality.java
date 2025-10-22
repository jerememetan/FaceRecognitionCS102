package app.test;

import org.opencv.core.*;
import app.service.FaceEmbeddingGenerator;
import app.util.ImageProcessor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Compare THREE preprocessing approaches:
 * 1. Current (bilateral + CLAHE + denoise + resize)
 * 2. Minimal (just denoise + resize)
 * 3. Ultra-minimal (just resize)
 */
public class ComparePreprocessingQuality {
    public static void main(String[] args) throws Exception {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        FaceEmbeddingGenerator embGen = new FaceEmbeddingGenerator();
        ImageProcessor imgProc = new ImageProcessor();

        // Load Jin_Rae images
        String folder = "data\\facedata\\S13234_Jin_Rae";
        java.io.File dir = new java.io.File(folder);
        java.io.File[] imageFiles = dir.listFiles((d, name) -> name.endsWith(".jpg"));

        if (imageFiles == null || imageFiles.length == 0) {
            System.out.println("No images found!");
            return;
        }

        System.out.println("Testing " + imageFiles.length + " images\n");
        System.out.println("=".repeat(80));

        // Test each preprocessing approach
        System.out.println("\nAPPROACH 1: CURRENT (bilateral + CLAHE + denoise + resize)");
        List<byte[]> embeddingsCurrent = new ArrayList<>();
        for (java.io.File f : imageFiles) {
            Mat img = org.opencv.imgcodecs.Imgcodecs.imread(f.getAbsolutePath());
            Mat processed = imgProc.preprocessFaceImage(img); // Bilateral + CLAHE
            byte[] emb = embGen.generateEmbedding(processed); // Denoise + resize
            embeddingsCurrent.add(emb);
            processed.release();
            img.release();
        }
        printQualityMetrics("CURRENT (bilateral+CLAHE)", embeddingsCurrent, embGen);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("\nAPPROACH 2: MINIMAL (denoise + resize only)");
        List<byte[]> embeddingsMinimal = new ArrayList<>();
        for (java.io.File f : imageFiles) {
            Mat img = org.opencv.imgcodecs.Imgcodecs.imread(f.getAbsolutePath());

            // Minimal preprocessing: skip bilateral + CLAHE, go directly to denoise +
            // resize
            Mat processed = preprocessMinimal(img);
            byte[] emb = embGen.generateEmbedding(processed);
            embeddingsMinimal.add(emb);
            processed.release();
            img.release();
        }
        printQualityMetrics("MINIMAL (denoise only)", embeddingsMinimal, embGen);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("\nAPPROACH 3: ULTRA-MINIMAL (resize only, no preprocessing)");
        List<byte[]> embeddingsUltra = new ArrayList<>();
        for (java.io.File f : imageFiles) {
            Mat img = org.opencv.imgcodecs.Imgcodecs.imread(f.getAbsolutePath());

            // Ultra-minimal: just resize to 400x400, let generateEmbedding handle the rest
            Mat resized = new Mat();
            org.opencv.imgproc.Imgproc.resize(img, resized, new Size(400, 400), 0, 0,
                    org.opencv.imgproc.Imgproc.INTER_CUBIC);
            byte[] emb = embGen.generateEmbedding(resized);
            embeddingsUltra.add(emb);
            resized.release();
            img.release();
        }
        printQualityMetrics("ULTRA-MINIMAL (resize only)", embeddingsUltra, embGen);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("\n=== RECOMMENDATION ===");

        // Compare tightness values
        double tightnessCurrent = calculateTightness(embeddingsCurrent, embGen);
        double tightnessMinimal = calculateTightness(embeddingsMinimal, embGen);
        double tightnessUltra = calculateTightness(embeddingsUltra, embGen);

        if (tightnessUltra > tightnessMinimal && tightnessUltra > tightnessCurrent) {
            System.out.println("✅ BEST: Ultra-minimal (resize only)");
            System.out.println("   Recommendation: Remove ALL preprocessing except resize!");
        } else if (tightnessMinimal > tightnessCurrent) {
            System.out.println("✅ BEST: Minimal (denoise only)");
            System.out.println("   Recommendation: Remove bilateral + CLAHE, keep denoise!");
        } else {
            System.out.println("✅ BEST: Current (bilateral + CLAHE)");
            System.out.println("   Recommendation: Keep current preprocessing");
        }
    }

    private static Mat preprocessMinimal(Mat img) {
        // Convert to grayscale if needed
        Mat gray = new Mat();
        if (img.channels() > 1) {
            org.opencv.imgproc.Imgproc.cvtColor(img, gray, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY);
        } else {
            gray = img.clone();
        }

        // Resize to standard 200x200 (to match ImageProcessor output)
        Mat resized = new Mat();
        org.opencv.imgproc.Imgproc.resize(gray, resized, new Size(200, 200), 0, 0,
                org.opencv.imgproc.Imgproc.INTER_CUBIC);
        gray.release();

        // Normalize
        Mat normalized = new Mat();
        org.opencv.core.Core.normalize(resized, normalized, 0, 255, org.opencv.core.Core.NORM_MINMAX,
                org.opencv.core.CvType.CV_8U);
        resized.release();

        return normalized;
    }

    private static void printQualityMetrics(String label, List<byte[]> embeddings,
            FaceEmbeddingGenerator embGen) {
        double tightness = calculateTightness(embeddings, embGen);
        double minSim = 1.0;
        double maxSim = 0.0;
        double sumSim = 0.0;
        int count = 0;

        for (int i = 0; i < embeddings.size(); i++) {
            for (int j = i + 1; j < embeddings.size(); j++) {
                double sim = embGen.calculateSimilarity(embeddings.get(i), embeddings.get(j));
                minSim = Math.min(minSim, sim);
                maxSim = Math.max(maxSim, sim);
                sumSim += sim;
                count++;
            }
        }

        double avgSim = count > 0 ? sumSim / count : 0;

        System.out.println("  Tightness (avg similarity): " + String.format("%.4f", tightness));
        System.out.println("  Min similarity: " + String.format("%.4f", minSim));
        System.out.println("  Max similarity: " + String.format("%.4f", maxSim));
        System.out.println("  Avg similarity: " + String.format("%.4f", avgSim));
        System.out.println("  Number of embeddings: " + embeddings.size());

        // Quality rating
        if (tightness >= 0.95) {
            System.out.println("  Quality: ⭐⭐⭐ EXCELLENT");
        } else if (tightness >= 0.90) {
            System.out.println("  Quality: ⭐⭐ VERY GOOD");
        } else if (tightness >= 0.85) {
            System.out.println("  Quality: ⭐ GOOD");
        } else {
            System.out.println("  Quality: ❌ POOR");
        }
    }

    private static double calculateTightness(List<byte[]> embeddings, FaceEmbeddingGenerator embGen) {
        if (embeddings == null || embeddings.size() < 2)
            return 0.0;

        double sum = 0.0;
        int count = 0;
        for (int i = 0; i < embeddings.size(); i++) {
            for (int j = i + 1; j < embeddings.size(); j++) {
                double sim = embGen.calculateSimilarity(embeddings.get(i), embeddings.get(j));
                sum += sim;
                count++;
            }
        }
        return count > 0 ? sum / count : 0.0;
    }
}
