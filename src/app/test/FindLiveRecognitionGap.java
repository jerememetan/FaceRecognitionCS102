package app.test;

import org.opencv.core.*;
import app.service.FaceEmbeddingGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Find the EXACT difference between offline test (0.9856) and live recognition
 * (0.65-0.82)
 */
public class FindLiveRecognitionGap {
    public static void main(String[] args) throws Exception {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        FaceEmbeddingGenerator embGen = new FaceEmbeddingGenerator();

        // Load all Jin_Rae embeddings
        String folder = "data\\facedata\\S13234_Jin_Rae";
        java.io.File dir = new java.io.File(folder);
        java.io.File[] embFiles = dir.listFiles((d, name) -> name.endsWith(".emb"));

        List<byte[]> storedEmbeddings = new ArrayList<>();
        for (java.io.File f : embFiles) {
            byte[] emb = Files.readAllBytes(Paths.get(f.getAbsolutePath()));
            storedEmbeddings.add(emb);
        }

        System.out.println("=== TESTING DIFFERENT SCENARIOS ===\n");

        // Test 1: Use a STORED embedding (simulating perfect match)
        System.out.println("TEST 1: Query = Stored Embedding (S13234_008.emb)");
        byte[] storedEmb = Files.readAllBytes(Paths.get("data\\facedata\\S13234_Jin_Rae\\S13234_008.emb"));
        double[] scores1 = calculateAllScores(storedEmb, storedEmbeddings, embGen);
        printScores("Stored embedding", scores1);

        // Test 2: Generate embedding from TRAINING image WITH ImageProcessor (training
        // path)
        System.out.println("\nTEST 2: Query = Training Path (ImageProcessor + generateEmbedding)");
        String trainingImage = "data\\facedata\\S13234_Jin_Rae\\S13234_008.jpg";
        Mat trainImg = org.opencv.imgcodecs.Imgcodecs.imread(trainingImage);
        System.out.println("Training image size: " + trainImg.width() + "x" + trainImg.height());

        // Use TRAINING path (ImageProcessor.preprocessFaceImage)
        app.util.ImageProcessor imgProc = new app.util.ImageProcessor();
        Mat trainPrep = imgProc.preprocessFaceImage(trainImg);
        byte[] trainEmb = embGen.generateEmbedding(trainPrep);
        trainPrep.release();
        trainImg.release();

        double[] scores2 = calculateAllScores(trainEmb, storedEmbeddings, embGen);
        printScores("Training path (with ImageProcessor)", scores2);

        // Test 3: Generate embedding from LIVE preprocessing (upscale + denoise +
        // resize)
        System.out.println("\nTEST 3: Query = Live Preprocessing (upscale 400x400 + denoise + resize)");
        Mat liveImg = org.opencv.imgcodecs.Imgcodecs.imread(trainingImage);
        Mat livePrep = embGen.preprocessFaceForLiveRecognition(liveImg);
        byte[] liveEmb = embGen.generateEmbedding(livePrep);
        livePrep.release();
        liveImg.release();

        double[] scores3 = calculateAllScores(liveEmb, storedEmbeddings, embGen);
        printScores("Live preprocessing", scores3);

        // Test 4: Simulate LIVE camera (smaller ROI, then upscale)
        System.out.println("\nTEST 4: Query = Simulated Camera ROI (200x200 → upscale 400x400)");
        Mat cameraImg = org.opencv.imgcodecs.Imgcodecs.imread(trainingImage);
        // Crop center 200x200 to simulate camera ROI
        int centerX = cameraImg.width() / 2;
        int centerY = cameraImg.height() / 2;
        Rect roi = new Rect(centerX - 100, centerY - 100, 200, 200);
        Mat cropped = new Mat(cameraImg, roi);

        // Now process like live recognition (upscale to 400x400)
        Mat cameraPrep = embGen.preprocessFaceForLiveRecognition(cropped);
        byte[] cameraEmb = embGen.generateEmbedding(cameraPrep);
        cameraPrep.release();
        cropped.release();
        cameraImg.release();

        double[] scores4 = calculateAllScores(cameraEmb, storedEmbeddings, embGen);
        printScores("Simulated camera ROI", scores4);

        // Compare embeddings directly
        System.out.println("\n=== EMBEDDING SIMILARITY COMPARISON ===");
        System.out.println("Training vs Stored: " + String.format("%.4f",
                embGen.calculateSimilarity(trainEmb, storedEmb)));
        System.out.println("Live vs Stored: " + String.format("%.4f",
                embGen.calculateSimilarity(liveEmb, storedEmb)));
        System.out.println("Camera vs Stored: " + String.format("%.4f",
                embGen.calculateSimilarity(cameraEmb, storedEmb)));
        System.out.println("Live vs Training: " + String.format("%.4f",
                embGen.calculateSimilarity(liveEmb, trainEmb)));

        System.out.println("\n=== ROOT CAUSE ANALYSIS ===");
        double fusionDrop = scores2[2] - scores3[2]; // Training vs Live fusion drop
        if (fusionDrop > 0.05) {
            System.out.println("❌ FOUND GAP: Live preprocessing reduces fusion score by " +
                    String.format("%.4f", fusionDrop));
            System.out.println("   Training fusion: " + String.format("%.4f", scores2[2]));
            System.out.println("   Live fusion: " + String.format("%.4f", scores3[2]));
            System.out.println("   This " + String.format("%.1f", fusionDrop * 100) +
                    "% drop explains why live scores are 0.65-0.82 instead of 0.85+");
        } else {
            System.out.println("✅ Preprocessing is consistent");
            System.out.println("   The problem must be elsewhere (different face detection, lighting, etc.)");
        }
    }

    private static double[] calculateAllScores(byte[] queryEmb, List<byte[]> storedEmbeddings,
            FaceEmbeddingGenerator embGen) {
        // Calculate centroid
        double[] centroid = computeCentroid(storedEmbeddings);
        double centroidScore = cosineSimilarity(queryEmb, centroid);

        // Calculate individual scores
        double[] sims = new double[storedEmbeddings.size()];
        for (int i = 0; i < storedEmbeddings.size(); i++) {
            sims[i] = embGen.calculateSimilarity(queryEmb, storedEmbeddings.get(i));
        }
        Arrays.sort(sims);

        double maxSim = sims[sims.length - 1];
        double avgTop3 = (sims[sims.length - 1] + sims[sims.length - 2] + sims[sims.length - 3]) / 3.0;
        double exemplarScore = 0.70 * maxSim + 0.30 * avgTop3;
        double fusionScore = 0.60 * centroidScore + 0.40 * exemplarScore;

        return new double[] { centroidScore, exemplarScore, fusionScore, maxSim, avgTop3 };
    }

    private static void printScores(String label, double[] scores) {
        System.out.println("  Centroid: " + String.format("%.4f", scores[0]));
        System.out.println("  Exemplar: " + String.format("%.4f", scores[1]));
        System.out.println("  Fusion:   " + String.format("%.4f", scores[2]));
        System.out.println("  (Max: " + String.format("%.4f", scores[3]) +
                ", Avg top-3: " + String.format("%.4f", scores[4]) + ")");
    }

    private static double[] computeCentroid(List<byte[]> embList) {
        if (embList == null || embList.isEmpty())
            return null;
        double[] sum = null;
        int count = 0;
        for (byte[] b : embList) {
            double[] v = decodeEmbeddingToDouble(b);
            if (v == null)
                continue;

            if (sum == null) {
                sum = Arrays.copyOf(v, v.length);
            } else {
                for (int i = 0; i < v.length; i++)
                    sum[i] += v[i];
            }
            count++;
        }
        if (sum == null || count == 0)
            return null;

        for (int i = 0; i < sum.length; i++)
            sum[i] /= count;

        normalizeL2InPlace(sum);
        return sum;
    }

    private static double cosineSimilarity(byte[] queryEmb, double[] centroid) {
        double[] q = decodeEmbeddingToDouble(queryEmb);
        if (q == null || centroid == null || q.length != centroid.length)
            return 0.0;

        normalizeL2InPlace(q);

        double dot = 0.0;
        for (int i = 0; i < q.length; i++) {
            dot += q[i] * centroid[i];
        }

        return Math.max(-1.0, Math.min(1.0, dot));
    }

    private static double[] decodeEmbeddingToDouble(byte[] emb) {
        if (emb == null)
            return null;
        try {
            if (emb.length == 128 * 4) {
                float[] fv = new float[128];
                ByteBuffer bb = ByteBuffer.wrap(emb);
                for (int i = 0; i < 128; i++)
                    fv[i] = bb.getFloat();
                double[] dv = new double[128];
                for (int i = 0; i < 128; i++)
                    dv[i] = fv[i];
                return dv;
            } else if (emb.length == 128 * 8) {
                double[] dv = new double[128];
                ByteBuffer bb = ByteBuffer.wrap(emb);
                for (int i = 0; i < 128; i++)
                    dv[i] = bb.getDouble();
                return dv;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static void normalizeL2InPlace(double[] vec) {
        double sumSq = 0.0;
        for (double v : vec)
            sumSq += v * v;
        double norm = Math.sqrt(sumSq);
        if (norm > 1e-12) {
            for (int i = 0; i < vec.length; i++)
                vec[i] /= norm;
        }
    }
}
