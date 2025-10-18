import app.service.FaceEmbeddingGenerator;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Test cross-person similarity to verify embeddings can discriminate between
 * different people
 */
public class TestCrossSimilarity {

    public static void main(String[] args) {
        System.loadLibrary("opencv_java480");

        String person1Folder = "data/facedata/S00001_jereme";
        String person2Folder = "data/facedata/S13234_Jin_Rae";

        FaceEmbeddingGenerator embGen = new FaceEmbeddingGenerator();

        // Load person 1 embeddings
        List<byte[]> person1Embs = loadEmbeddings(person1Folder);
        System.out.println("Loaded " + person1Embs.size() + " embeddings for S00001_jereme");

        // Load person 2 embeddings
        List<byte[]> person2Embs = loadEmbeddings(person2Folder);
        System.out.println("Loaded " + person2Embs.size() + " embeddings for S13234_Jin_Rae");

        // Test 1: Within-person similarity (should be HIGH: 0.85-0.95)
        System.out.println("\n=== WITHIN-PERSON SIMILARITY (Should be HIGH) ===");
        double person1Tightness = calculateAvgPairwise(person1Embs, embGen);
        System.out.printf("S00001_jereme within-person avg: %.4f (expected: 0.85-0.95)%n", person1Tightness);

        double person2Tightness = calculateAvgPairwise(person2Embs, embGen);
        System.out.printf("S13234_Jin_Rae within-person avg: %.4f (expected: 0.85-0.95)%n", person2Tightness);

        // Test 2: Cross-person similarity (should be LOW: 0.30-0.60 for different
        // people)
        System.out.println("\n=== CROSS-PERSON SIMILARITY (Should be LOW) ===");
        double crossSimilarity = calculateCrossSimilarity(person1Embs, person2Embs, embGen);
        System.out.printf("S00001_jereme vs S13234_Jin_Rae avg: %.4f (expected: 0.30-0.60 for different people)%n",
                crossSimilarity);

        // Test 3: Show distribution
        System.out.println("\n=== CROSS-SIMILARITY DISTRIBUTION ===");
        showCrossDistribution(person1Embs, person2Embs, embGen);

        // Verdict
        System.out.println("\n=== VERDICT ===");
        if (crossSimilarity > 0.70) {
            System.out
                    .println("❌ CRITICAL: Cross-similarity TOO HIGH (" + String.format("%.4f", crossSimilarity) + ")");
            System.out.println("   These embeddings CANNOT discriminate between different people!");
            System.out.println("   Expected: <0.60, Actual: " + String.format("%.4f", crossSimilarity));
            System.out.println("   POSSIBLE CAUSES:");
            System.out.println("   1. Embeddings are not actually different (same person?)");
            System.out.println("   2. Embedding model is too coarse (low discriminative power)");
            System.out.println("   3. Preprocessing mismatch during capture");
        } else if (crossSimilarity > 0.60) {
            System.out
                    .println("⚠️  WARNING: Cross-similarity MARGINAL (" + String.format("%.4f", crossSimilarity) + ")");
            System.out.println("   Discrimination is weak but possible");
            System.out.println("   May struggle with 40 people");
        } else {
            System.out.println("✅ GOOD: Cross-similarity is LOW (" + String.format("%.4f", crossSimilarity) + ")");
            System.out.println("   Embeddings can discriminate between different people");
        }

        double margin = person2Tightness - crossSimilarity;
        System.out.printf("%nDiscrimination margin: %.4f (higher is better)%n", margin);
        if (margin < 0.15) {
            System.out.println("❌ Margin TOO SMALL - Cannot reliably discriminate!");
        } else if (margin < 0.25) {
            System.out.println("⚠️  Margin WEAK - May struggle with multiple people");
        } else {
            System.out.println("✅ Margin GOOD - Can discriminate reliably");
        }
    }

    private static List<byte[]> loadEmbeddings(String folderPath) {
        List<byte[]> embeddings = new ArrayList<>();
        File dir = new File(folderPath);
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".emb"));

        if (files != null) {
            for (File f : files) {
                try {
                    byte[] emb = Files.readAllBytes(Paths.get(f.getAbsolutePath()));
                    embeddings.add(emb);
                } catch (Exception e) {
                    System.err.println("Failed to load: " + f.getName());
                }
            }
        }

        return embeddings;
    }

    private static double calculateAvgPairwise(List<byte[]> embeddings, FaceEmbeddingGenerator embGen) {
        if (embeddings.size() < 2)
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

    private static double calculateCrossSimilarity(List<byte[]> person1, List<byte[]> person2,
            FaceEmbeddingGenerator embGen) {
        double sum = 0.0;
        int count = 0;

        for (byte[] emb1 : person1) {
            for (byte[] emb2 : person2) {
                double sim = embGen.calculateSimilarity(emb1, emb2);
                sum += sim;
                count++;
            }
        }

        return count > 0 ? sum / count : 0.0;
    }

    private static void showCrossDistribution(List<byte[]> person1, List<byte[]> person2,
            FaceEmbeddingGenerator embGen) {
        List<Double> sims = new ArrayList<>();

        for (byte[] emb1 : person1) {
            for (byte[] emb2 : person2) {
                sims.add(embGen.calculateSimilarity(emb1, emb2));
            }
        }

        sims.sort(Double::compareTo);

        double min = sims.get(0);
        double max = sims.get(sims.size() - 1);
        double median = sims.get(sims.size() / 2);

        System.out.printf("Min: %.4f, Median: %.4f, Max: %.4f%n", min, median, max);
        System.out.printf("Range: %.4f (%.1f%% spread)%n", max - min, (max - min) * 100);

        // Show top 5 highest (most concerning)
        System.out.println("\nTop 5 HIGHEST cross-similarities (most concerning):");
        for (int i = Math.max(0, sims.size() - 5); i < sims.size(); i++) {
            System.out.printf("  %.4f%n", sims.get(i));
        }
    }
}
