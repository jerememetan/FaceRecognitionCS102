package app.test;

import app.service.FaceEmbeddingGenerator;
import app.util.ImageProcessor;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Test class to analyze face similarity between stored face data.
 * Tests intra-person similarity (same person) vs inter-person similarity (different people).
 * Helps validate the effectiveness of glasses-aware recognition improvements.
 */
public class FaceSimilarityTest {

    private final ImageProcessor imageProcessor;

    // Test results
    private Map<String, List<byte[]>> personEmbeddings;
    private Map<String, List<Double>> intraPersonSimilarities;
    private Map<String, List<Double>> interPersonSimilarities;
    private Map<String, Double> personStdDevs;
    private boolean hasCorruptedEmbeddings;

    public FaceSimilarityTest() {
        // Don't initialize FaceEmbeddingGenerator to avoid OpenCV loading issues
        // this.embeddingGenerator = new FaceEmbeddingGenerator();
        this.imageProcessor = new ImageProcessor();
        this.personEmbeddings = new HashMap<>();
        this.intraPersonSimilarities = new HashMap<>();
        this.interPersonSimilarities = new HashMap<>();
        this.personStdDevs = new HashMap<>();
        this.hasCorruptedEmbeddings = false;
    }

    /**
     * Main test method - loads all face data and computes similarities
     */
    public void runSimilarityTest() {
        System.out.println("=== FACE SIMILARITY TEST ===");
        System.out.println("Testing intra-person vs inter-person face similarities\n");

        // Load all face embeddings
        loadAllEmbeddings();

        if (personEmbeddings.isEmpty()) {
            System.out.println("ERROR: No face data found!");
            return;
        }

        // Compute intra-person similarities (within same person)
        computeIntraPersonSimilarities();

        // Compute inter-person similarities (between different people)
        computeInterPersonSimilarities();

        // Generate report
        generateSimilarityReport();
    }

    /**
     * Load all embeddings from the facedata directory
     */
    private void loadAllEmbeddings() {
        String faceDataPath = "data/facedata";

        File faceDataDir = new File(faceDataPath);
        if (!faceDataDir.exists() || !faceDataDir.isDirectory()) {
            System.out.println("ERROR: Face data directory not found: " + faceDataPath);
            return;
        }

        File[] personDirs = faceDataDir.listFiles(File::isDirectory);
        if (personDirs == null || personDirs.length == 0) {
            System.out.println("ERROR: No person directories found in " + faceDataPath);
            return;
        }

        System.out.println("Loading embeddings from " + personDirs.length + " persons:");

        for (File personDir : personDirs) {
            String personName = extractPersonName(personDir.getName());
            List<byte[]> embeddings = loadPersonEmbeddings(personDir);

            if (!embeddings.isEmpty()) {
                personEmbeddings.put(personName, embeddings);
                System.out.println("  " + personName + ": " + embeddings.size() + " embeddings");
            }
        }

        System.out.println();
    }

    /**
     * Extract person name from directory name (e.g., "S13234_Jin_Rae" -> "Jin_Rae")
     */
    private String extractPersonName(String dirName) {
        String[] parts = dirName.split("_", 2);
        return parts.length > 1 ? parts[1] : dirName;
    }

    /**
     * Load all .emb files for a person
     */
    private List<byte[]> loadPersonEmbeddings(File personDir) {
        List<byte[]> embeddings = new ArrayList<>();

        File[] embFiles = personDir.listFiles((dir, name) -> name.endsWith(".emb"));
        if (embFiles == null) return embeddings;

        for (File embFile : embFiles) {
            try {
                byte[] embedding = Files.readAllBytes(embFile.toPath());
                if (embedding.length == 128 * 4) { // 128 floats = 512 bytes
                    embeddings.add(embedding);
                }
            } catch (IOException e) {
                System.err.println("Failed to load embedding: " + embFile.getName());
            }
        }

        return embeddings;
    }

    /**
     * Compute similarities between all pairs of embeddings for the same person
     */
    private void computeIntraPersonSimilarities() {
        System.out.println("Computing intra-person similarities...");

        for (Map.Entry<String, List<byte[]>> entry : personEmbeddings.entrySet()) {
            String personName = entry.getKey();
            List<byte[]> embeddings = entry.getValue();

            List<Double> similarities = new ArrayList<>();
            List<double[]> floatEmbeddings = embeddings.stream()
                .map(this::bytesToFloats)
                .collect(Collectors.toList());

            // Compute all pairwise similarities
            for (int i = 0; i < floatEmbeddings.size(); i++) {
                for (int j = i + 1; j < floatEmbeddings.size(); j++) {
                    double similarity = cosineSimilarity(floatEmbeddings.get(i), floatEmbeddings.get(j));
                    similarities.add(similarity);
                }
            }

            intraPersonSimilarities.put(personName, similarities);

            // Compute standard deviation for this person
            if (!similarities.isEmpty()) {
                double mean = similarities.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                double variance = similarities.stream()
                    .mapToDouble(s -> Math.pow(s - mean, 2))
                    .average().orElse(0.0);
                double stdDev = Math.sqrt(variance);
                personStdDevs.put(personName, stdDev);
            }
        }
    }

    /**
     * Compute similarities between embeddings of different people
     */
    private void computeInterPersonSimilarities() {
        System.out.println("Computing inter-person similarities...");

        List<String> personNames = new ArrayList<>(personEmbeddings.keySet());
        List<Double> allInterSimilarities = new ArrayList<>();

        // Compare each person with every other person
        for (int i = 0; i < personNames.size(); i++) {
            for (int j = i + 1; j < personNames.size(); j++) {
                String person1 = personNames.get(i);
                String person2 = personNames.get(j);

                List<byte[]> emb1 = personEmbeddings.get(person1);
                List<byte[]> emb2 = personEmbeddings.get(person2);

                // Compare all embeddings between these two people
                List<double[]> floatEmb1 = emb1.stream().map(this::bytesToFloats).collect(Collectors.toList());
                List<double[]> floatEmb2 = emb2.stream().map(this::bytesToFloats).collect(Collectors.toList());

                for (double[] e1 : floatEmb1) {
                    for (double[] e2 : floatEmb2) {
                        double similarity = cosineSimilarity(e1, e2);
                        allInterSimilarities.add(similarity);
                    }
                }
            }
        }

        // Store inter-person similarities (use a generic key since it's between all pairs)
        interPersonSimilarities.put("all_pairs", allInterSimilarities);
    }

    /**
     * Generate comprehensive similarity report
     */
    private void generateSimilarityReport() {
        System.out.println("\n=== SIMILARITY ANALYSIS REPORT ===");

        // Intra-person statistics
        System.out.println("\nINTRA-PERSON SIMILARITIES (same person):");
        boolean hasCorruptedEmbeddings = false;

        for (Map.Entry<String, List<Double>> entry : intraPersonSimilarities.entrySet()) {
            String personName = entry.getKey();
            List<Double> similarities = entry.getValue();

            if (similarities.isEmpty()) continue;

            // Check for NaN values
            long nanCount = similarities.stream().filter(s -> Double.isNaN(s)).count();
            if (nanCount > 0) {
                hasCorruptedEmbeddings = true;
                System.out.printf("  %s: CORRUPTED (%d/%d similarities are NaN)%n",
                    personName, nanCount, similarities.size());
                continue;
            }

            double min = similarities.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            double max = similarities.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            double avg = similarities.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double stdDev = personStdDevs.getOrDefault(personName, 0.0);

            System.out.printf("  %s: %.3f ± %.3f (min: %.3f, max: %.3f, n=%d)%n",
                personName, avg, stdDev, min, max, similarities.size());

            // Check for glasses indication
            if (stdDev > 0.12) {
                System.out.println("    ⚠️  HIGH VARIATION - Possible glasses wearer (stdDev > 0.12)");
            }
        }

        // Inter-person statistics
        System.out.println("\nINTER-PERSON SIMILARITIES (different people):");
        List<Double> interSims = interPersonSimilarities.get("all_pairs");
        if (!interSims.isEmpty()) {
            // Check for NaN values
            long nanCount = interSims.stream().filter(s -> Double.isNaN(s)).count();
            if (nanCount > 0) {
                hasCorruptedEmbeddings = true;
                System.out.printf("  All pairs: CORRUPTED (%d/%d similarities are NaN)%n",
                    nanCount, interSims.size());
            } else {
                double min = interSims.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
                double max = interSims.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
                double avg = interSims.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

                double variance = interSims.stream()
                    .mapToDouble(s -> Math.pow(s - avg, 2))
                    .average().orElse(0.0);
                double stdDev = Math.sqrt(variance);

                System.out.printf("  All pairs: %.3f ± %.3f (min: %.3f, max: %.3f, n=%d)%n",
                    avg, stdDev, min, max, interSims.size());
            }
        }

        // Discrimination analysis
        System.out.println("\nDISCRIMINATION ANALYSIS:");
        analyzeDiscrimination();

        // Recommendations
        System.out.println("\nRECOMMENDATIONS:");
        generateRecommendations();
    }

    /**
     * Analyze how well the system discriminates between different people
     */
    private void analyzeDiscrimination() {
        // Find the lowest intra-person similarity and highest inter-person similarity
        double minIntraSim = Double.MAX_VALUE;
        double maxInterSim = Double.MIN_VALUE;

        for (List<Double> intraSims : intraPersonSimilarities.values()) {
            for (double sim : intraSims) {
                minIntraSim = Math.min(minIntraSim, sim);
            }
        }

        List<Double> interSims = interPersonSimilarities.get("all_pairs");
        for (double sim : interSims) {
            maxInterSim = Math.max(maxInterSim, sim);
        }

        double discriminationMargin = minIntraSim - maxInterSim;

        System.out.printf("  Minimum intra-person similarity: %.3f%n", minIntraSim);
        System.out.printf("  Maximum inter-person similarity: %.3f%n", maxInterSim);
        System.out.printf("  Discrimination margin: %.3f%n", discriminationMargin);

        if (discriminationMargin > 0.1) {
            System.out.println("  ✅ GOOD: Clear separation between same/different people");
        } else if (discriminationMargin > 0.05) {
            System.out.println("  ⚠️  MODERATE: Some overlap, may need threshold tuning");
        } else {
            System.out.println("  ❌ POOR: Significant overlap, recognition may be unreliable");
        }
    }

    /**
     * Generate recommendations based on analysis
     */
    private void generateRecommendations() {
        if (hasCorruptedEmbeddings) {
            System.out.println("  ❌ CRITICAL: Embeddings are corrupted - regenerate all face data!");
            System.out.println("     Run CleanupFaceData.bat then recapture all faces");
            System.out.println("     The current embeddings were generated with grayscale preprocessing bug");
        }

        boolean hasGlassesWearers = personStdDevs.values().stream()
            .filter(std -> !Double.isNaN(std))
            .anyMatch(std -> std > 0.12);

        if (hasGlassesWearers) {
            System.out.println("  • Glasses-aware thresholds are active - monitor recognition accuracy");
            System.out.println("  • Consider capturing more diverse angles for glasses wearers");
        }

        // Check if any person has very few samples
        boolean hasLowSampleCount = personEmbeddings.values().stream().anyMatch(list -> list.size() < 5);
        if (hasLowSampleCount) {
            System.out.println("  • Some people have few samples - capture more faces for better recognition");
        }

        System.out.println("  • Run this test after any changes to validate improvements");
    }

    /**
     * Convert byte array to float array (embedding) - Little Endian format
     */
    private double[] bytesToFloats(byte[] bytes) {
        double[] floats = new double[128];
        boolean hasValidValues = false;
        boolean hasNaN = false;
        boolean hasInfinite = false;

        for (int i = 0; i < 128; i++) {
            // Little-endian: least significant byte first
            int floatBits = ((bytes[i * 4 + 3] & 0xFF) << 24) |
                           ((bytes[i * 4 + 2] & 0xFF) << 16) |
                           ((bytes[i * 4 + 1] & 0xFF) << 8) |
                           ((bytes[i * 4] & 0xFF));

            float floatValue = Float.intBitsToFloat(floatBits);
            floats[i] = floatValue;

            // Debug: check first few values
            if (i < 3) {
                System.out.printf("Float[%d]: bits=0x%08X, value=%.6f%n", i, floatBits, floatValue);
            }

            // Validate values
            if (Double.isNaN(floatValue)) hasNaN = true;
            else if (Double.isInfinite(floatValue)) hasInfinite = true;
            else if (Math.abs(floatValue) > 0.0001 && Math.abs(floatValue) < 1000) {
                hasValidValues = true; // Reasonable range for normalized embeddings
            }
        }

        // Report embedding quality
        if (hasNaN) {
            System.out.println("  ❌ EMBEDDING CORRUPTED: Contains NaN values");
        } else if (hasInfinite) {
            System.out.println("  ❌ EMBEDDING CORRUPTED: Contains infinite values");
        } else if (!hasValidValues) {
            System.out.println("  ⚠️  EMBEDDING SUSPICIOUS: All values near zero or extremely large");
        } else {
            System.out.println("  ✅ EMBEDDING OK: Valid float values in reasonable range");
        }

        return floats;
    }

    /**
     * Compute cosine similarity between two embeddings
     */
    private double cosineSimilarity(double[] emb1, double[] emb2) {
        // Check for corrupted embeddings
        for (int i = 0; i < emb1.length; i++) {
            if (Double.isNaN(emb1[i]) || Double.isNaN(emb2[i]) ||
                Double.isInfinite(emb1[i]) || Double.isInfinite(emb2[i])) {
                return Double.NaN; // Return NaN to indicate corrupted data
            }
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < emb1.length; i++) {
            dotProduct += emb1[i] * emb2[i];
            norm1 += emb1[i] * emb1[i];
            norm2 += emb2[i] * emb2[i];
        }

        norm1 = Math.sqrt(norm1);
        norm2 = Math.sqrt(norm2);

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (norm1 * norm2);
    }

    /**
     * Main method to run the test
     */
    public static void main(String[] args) {
        FaceSimilarityTest test = new FaceSimilarityTest();
        test.runSimilarityTest();
    }
}