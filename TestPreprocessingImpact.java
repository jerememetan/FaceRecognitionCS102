import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.CLAHE;
import org.opencv.dnn.Net;
import org.opencv.dnn.Dnn;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Test how different preprocessing parameters affect embedding discrimination.
 * This will help determine if aggressive CLAHE/normalization is causing the
 * cross-person similarity problem.
 */
public class TestPreprocessingImpact {
    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    private static final Size STANDARD_SIZE = new Size(96, 96);

    public static void main(String[] args) {
        // Load embedding model
        String modelPath = "./data/resources/openface.nn4.small2.v1.t7";
        Net embeddingModel = Dnn.readNetFromTorch(modelPath);
        System.out.println("Embedding model loaded");

        // Load one sample image for each person
        String jereme1 = "./data/facedata/S00001_jereme/original_001.png";
        String jinrae1 = "./data/facedata/S13234_Jin_Rae/original_001.png";

        Mat jeremeImg = Imgcodecs.imread(jereme1, Imgcodecs.IMREAD_GRAYSCALE);
        Mat jinraeImg = Imgcodecs.imread(jinrae1, Imgcodecs.IMREAD_GRAYSCALE);

        if (jeremeImg.empty() || jinraeImg.empty()) {
            System.err.println("ERROR: Could not load sample images");
            System.err.println("Looking for:");
            System.err.println("  " + jereme1);
            System.err.println("  " + jinrae1);
            return;
        }

        System.out.println("\nLoaded sample images:");
        System.out.println("  jereme: " + jeremeImg.size());
        System.out.println("  Jin_Rae: " + jinraeImg.size());

        // Test different preprocessing configurations
        System.out.println("\n=== TESTING PREPROCESSING IMPACT ON DISCRIMINATION ===\n");

        // Config 1: Current (AGGRESSIVE) - clipLimit=2.0 + NORM_MINMAX
        System.out.println("CONFIG 1: Current (AGGRESSIVE)");
        System.out.println("  CLAHE clipLimit=2.0, NORM_MINMAX(0,255)");
        testConfig(jeremeImg, jinraeImg, embeddingModel, 2.0, true);

        // Config 2: MODERATE - clipLimit=1.5 + NORM_MINMAX
        System.out.println("\nCONFIG 2: MODERATE");
        System.out.println("  CLAHE clipLimit=1.5, NORM_MINMAX(0,255)");
        testConfig(jeremeImg, jinraeImg, embeddingModel, 1.5, true);

        // Config 3: LIGHT - clipLimit=1.0 + NORM_MINMAX
        System.out.println("\nCONFIG 3: LIGHT");
        System.out.println("  CLAHE clipLimit=1.0, NORM_MINMAX(0,255)");
        testConfig(jeremeImg, jinraeImg, embeddingModel, 1.0, true);

        // Config 4: MINIMAL - clipLimit=1.0 + NO normalization
        System.out.println("\nCONFIG 4: MINIMAL");
        System.out.println("  CLAHE clipLimit=1.0, NO NORM_MINMAX");
        testConfig(jeremeImg, jinraeImg, embeddingModel, 1.0, false);

        // Config 5: BASELINE - NO CLAHE + NO normalization (just resize)
        System.out.println("\nCONFIG 5: BASELINE");
        System.out.println("  NO CLAHE, NO NORM_MINMAX (just bilateral + resize)");
        testConfig(jeremeImg, jinraeImg, embeddingModel, 0.0, false);

        System.out.println("\n=== ANALYSIS ===");
        System.out.println("Compare the cross-person similarities above:");
        System.out.println("  - If similarity DECREASES with lighter preprocessing → Preprocessing is the problem");
        System.out.println("  - If similarity stays HIGH (~0.75+) → Model has low discriminative power");
        System.out.println("  - Target: Cross-similarity should be <0.60 for good discrimination");
    }

    private static void testConfig(Mat img1, Mat img2, Net model, double clipLimit, boolean useNormalize) {
        // Preprocess both images with this config
        Mat proc1 = preprocessWithConfig(img1, clipLimit, useNormalize);
        Mat proc2 = preprocessWithConfig(img2, clipLimit, useNormalize);

        // Generate embeddings
        Mat emb1 = generateEmbedding(proc1, model);
        Mat emb2 = generateEmbedding(proc2, model);

        // Calculate similarity
        double similarity = cosineSimilarity(emb1, emb2);

        System.out.println("  Cross-person similarity: " + String.format("%.4f", similarity));

        if (similarity < 0.60) {
            System.out.println("  ✅ GOOD - Can discriminate!");
        } else if (similarity < 0.70) {
            System.out.println("  ⚠️ WEAK - Marginal discrimination");
        } else {
            System.out.println("  ❌ POOR - Cannot discriminate");
        }

        proc1.release();
        proc2.release();
        emb1.release();
        emb2.release();
    }

    private static Mat preprocessWithConfig(Mat image, double clipLimit, boolean useNormalize) {
        Mat processed = image.clone();

        // 1. Bilateral filter
        Mat filtered = new Mat();
        Imgproc.bilateralFilter(processed, filtered, 5, 50, 50);
        processed.release();

        // 2. CLAHE (if enabled)
        Mat claheResult = new Mat();
        if (clipLimit > 0.0) {
            CLAHE clahe = Imgproc.createCLAHE(clipLimit, new Size(8, 8));
            clahe.apply(filtered, claheResult);
            filtered.release();
        } else {
            claheResult = filtered; // Skip CLAHE
        }

        // 3. Resize
        Mat resized = new Mat();
        Imgproc.resize(claheResult, resized, STANDARD_SIZE, 0, 0, Imgproc.INTER_CUBIC);
        claheResult.release();

        // 4. Normalize (if enabled)
        Mat final_result = new Mat();
        if (useNormalize) {
            Core.normalize(resized, final_result, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);
            resized.release();
        } else {
            final_result = resized; // Skip normalization
        }

        return final_result;
    }

    private static Mat generateEmbedding(Mat preprocessedFace, Net model) {
        Mat blob = Dnn.blobFromImage(preprocessedFace, 1.0 / 255.0,
                new Size(96, 96), new Scalar(0), true, false);
        model.setInput(blob);
        Mat embedding = model.forward();
        blob.release();

        // Normalize to unit length
        double norm = Core.norm(embedding);
        if (norm > 0) {
            Core.divide(embedding, new Scalar(norm), embedding);
        }

        return embedding;
    }

    private static double cosineSimilarity(Mat emb1, Mat emb2) {
        if (emb1.empty() || emb2.empty())
            return 0.0;

        double dot = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < emb1.total(); i++) {
            double v1 = emb1.get(0, i)[0];
            double v2 = emb2.get(0, i)[0];
            dot += v1 * v2;
            norm1 += v1 * v1;
            norm2 += v2 * v2;
        }

        if (norm1 == 0 || norm2 == 0)
            return 0.0;
        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
