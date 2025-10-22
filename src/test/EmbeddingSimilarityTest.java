package test;

import app.service.FaceEmbeddingGenerator;
import ConfigurationAndLogging.AppConfig;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Test script to analyze embedding similarities between facedata embeddings.
 * Calculates intra-person and inter-person similarities to validate embedding quality.
 */
public class EmbeddingSimilarityTest {

    static {
        System.load(new File("lib/opencv_java480.dll").getAbsolutePath());
    }

    private final FaceEmbeddingGenerator embGen;

    public EmbeddingSimilarityTest() throws Exception {
        System.out.println("Loading FaceEmbeddingGenerator...");
        try {
            this.embGen = new FaceEmbeddingGenerator();
            System.out.println("✅ FaceEmbeddingGenerator loaded successfully");
        } catch (Exception e) {
            System.err.println("❌ Failed to initialize FaceEmbeddingGenerator: " + e.getMessage());
            throw e;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Embedding Similarity Test ===\n");

        try {
            System.out.println("Initializing test...");
            EmbeddingSimilarityTest test = new EmbeddingSimilarityTest();
            System.out.println("✅ Test initialized successfully");
            test.runSimilarityAnalysis();
        } catch (Exception e) {
            System.err.println("❌ Fatal error in main: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void runSimilarityAnalysis() {
        StringBuilder output = new StringBuilder();

        try {
            // Load all embeddings
            Map<String, List<byte[]>> personEmbeddings = loadAllEmbeddings();
            List<String> personNames = new ArrayList<>(personEmbeddings.keySet());

            if (personEmbeddings.isEmpty()) {
                output.append("❌ No embeddings found!\n");
                System.out.print(output.toString());
                return;
            }

            output.append("📊 Loaded embeddings for ").append(personNames.size()).append(" persons:\n");
            for (String person : personNames) {
                output.append("  ").append(person).append(": ").append(personEmbeddings.get(person).size()).append(" embeddings\n");
            }
            output.append("\n");

            // Calculate intra-person similarities
            output.append("🔍 Calculating intra-person similarities...\n");
            Map<String, List<Double>> intraSimilarities = calculateIntraPersonSimilarities(personEmbeddings);

            // Calculate inter-person similarities
            output.append("🔍 Calculating inter-person similarities...\n");
            List<Double> interSimilarities = calculateInterPersonSimilarities(personEmbeddings, personNames);

            // Print statistics
            output.append(getStatisticsString(intraSimilarities, interSimilarities, personNames));

            // Print detailed similarity matrix
            output.append(getSimilarityMatrixString(personEmbeddings, personNames));

            System.out.print(output.toString());

        } catch (Exception e) {
            System.err.println("❌ Error during similarity analysis: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Map<String, List<byte[]>> loadAllEmbeddings() throws IOException {
        Map<String, List<byte[]>> embeddings = new HashMap<>();

        // Use hardcoded path for testing
        String facedataPath = "./data/facedata";
        File facedataDir = new File(facedataPath);

        if (!facedataDir.exists() || !facedataDir.isDirectory()) {
            throw new IOException("Facedata directory not found: " + facedataPath);
        }

        System.out.println("Loading embeddings from: " + facedataDir.getAbsolutePath());

        File[] personDirs = facedataDir.listFiles(File::isDirectory);
        if (personDirs == null) {
            throw new IOException("No person directories found in: " + facedataPath);
        }

        System.out.println("Found " + personDirs.length + " person directories");

        for (File personDir : personDirs) {
            String personName = personDir.getName();
            List<byte[]> personEmbs = new ArrayList<>();

            File[] embFiles = personDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".emb"));
            if (embFiles != null) {
                System.out.println("  " + personName + ": found " + embFiles.length + " .emb files");
                for (File embFile : embFiles) {
                    try {
                        byte[] emb = Files.readAllBytes(embFile.toPath());
                        if (embGen.isEmbeddingValid(emb)) {
                            personEmbs.add(emb);
                        } else {
                            System.out.println("⚠️  Skipping invalid embedding: " + embFile.getName());
                        }
                    } catch (Exception e) {
                        System.out.println("⚠️  Failed to load embedding: " + embFile.getName() + " - " + e.getMessage());
                    }
                }
            } else {
                System.out.println("  " + personName + ": no .emb files found");
            }

            if (!personEmbs.isEmpty()) {
                embeddings.put(personName, personEmbs);
            }
        }

        return embeddings;
    }

    private Map<String, List<Double>> calculateIntraPersonSimilarities(Map<String, List<byte[]>> personEmbeddings) {
        Map<String, List<Double>> intraSimilarities = new HashMap<>();

        for (Map.Entry<String, List<byte[]>> entry : personEmbeddings.entrySet()) {
            String personName = entry.getKey();
            List<byte[]> embeddings = entry.getValue();
            List<Double> similarities = new ArrayList<>();

            // Calculate all pairwise similarities for this person
            for (int i = 0; i < embeddings.size(); i++) {
                for (int j = i + 1; j < embeddings.size(); j++) {
                    double sim = embGen.calculateSimilarity(embeddings.get(i), embeddings.get(j));
                    similarities.add(sim);
                }
            }

            intraSimilarities.put(personName, similarities);
        }

        return intraSimilarities;
    }

    private List<Double> calculateInterPersonSimilarities(Map<String, List<byte[]>> personEmbeddings, List<String> personNames) {
        List<Double> interSimilarities = new ArrayList<>();

        // Sample embeddings from different persons
        for (int i = 0; i < personNames.size(); i++) {
            for (int j = i + 1; j < personNames.size(); j++) {
                String person1 = personNames.get(i);
                String person2 = personNames.get(j);

                List<byte[]> embs1 = personEmbeddings.get(person1);
                List<byte[]> embs2 = personEmbeddings.get(person2);

                // Sample a few embeddings from each person to avoid too many calculations
                int sampleSize = Math.min(3, Math.min(embs1.size(), embs2.size()));

                for (int k = 0; k < sampleSize; k++) {
                    for (int l = 0; l < sampleSize; l++) {
                        double sim = embGen.calculateSimilarity(embs1.get(k), embs2.get(l));
                        interSimilarities.add(sim);
                    }
                }
            }
        }

        return interSimilarities;
    }

    private String getStatisticsString(Map<String, List<Double>> intraSimilarities, List<Double> interSimilarities, List<String> personNames) {
        StringBuilder output = new StringBuilder();
        output.append("📈 SIMILARITY STATISTICS\n");
        output.append("=".repeat(50)).append("\n");

        // Intra-person statistics
        output.append("\n🎯 Intra-person similarities (same person):\n");
        List<Double> allIntraSimilarities = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : intraSimilarities.entrySet()) {
            List<Double> sims = entry.getValue();
            if (!sims.isEmpty()) {
                allIntraSimilarities.addAll(sims);
                double avg = sims.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                double min = sims.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
                double max = sims.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
                output.append(String.format("  %s: %.3f avg (%.3f - %.3f), %d pairs%n",
                    entry.getKey(), avg, min, max, sims.size()));
            }
        }

        // Inter-person statistics
        output.append("\n🚫 Inter-person similarities (different persons):\n");
        if (!interSimilarities.isEmpty()) {
            double avg = interSimilarities.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double min = interSimilarities.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            double max = interSimilarities.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            output.append(String.format("  Overall: %.3f avg (%.3f - %.3f), %d comparisons%n",
                avg, min, max, interSimilarities.size()));
        }

        // Overall assessment
        output.append("\n📊 OVERALL ASSESSMENT:\n");
        if (!allIntraSimilarities.isEmpty() && !interSimilarities.isEmpty()) {
            double avgIntra = allIntraSimilarities.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double avgInter = interSimilarities.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double separation = avgIntra - avgInter;

            output.append(String.format("  Average intra-person similarity: %.3f%n", avgIntra));
            output.append(String.format("  Average inter-person similarity: %.3f%n", avgInter));
            output.append(String.format("  Separation margin: %.3f%n", separation));

            if (separation > 0.2) {
                output.append("  ✅ Good separation - embeddings are well-clustered\n");
            } else if (separation > 0.1) {
                output.append("  ⚠️  Moderate separation - some overlap possible\n");
            } else {
                output.append("  ❌ Poor separation - high risk of misidentification\n");
            }
        }
        output.append("\n");

        return output.toString();
    }

    private String getSimilarityMatrixString(Map<String, List<byte[]>> personEmbeddings, List<String> personNames) {
        StringBuilder output = new StringBuilder();
        output.append("🔢 SIMILARITY MATRIX (average similarity between persons)\n");
        output.append("=".repeat(60)).append("\n");

        // Header
        output.append("         ");
        for (String name : personNames) {
            output.append(String.format("%-12s", name.substring(0, Math.min(12, name.length()))));
        }
        output.append("\n");

        // Matrix
        for (int i = 0; i < personNames.size(); i++) {
            String person1 = personNames.get(i);
            output.append(String.format("%-9s", person1.substring(0, Math.min(9, person1.length()))));

            for (int j = 0; j < personNames.size(); j++) {
                if (i == j) {
                    // Intra-person similarity
                    List<Double> intraSims = calculateIntraPersonSimilarities(
                        Collections.singletonMap(person1, personEmbeddings.get(person1))).get(person1);
                    double avgIntra = intraSims.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    output.append(String.format("%-12.3f", avgIntra));
                } else {
                    // Inter-person similarity
                    String person2 = personNames.get(j);
                    List<byte[]> embs1 = personEmbeddings.get(person1);
                    List<byte[]> embs2 = personEmbeddings.get(person2);

                    double totalSim = 0.0;
                    int count = 0;
                    int sampleSize = Math.min(2, Math.min(embs1.size(), embs2.size()));

                    for (int k = 0; k < sampleSize; k++) {
                        for (int l = 0; l < sampleSize; l++) {
                            totalSim += embGen.calculateSimilarity(embs1.get(k), embs2.get(l));
                            count++;
                        }
                    }

                    double avgSim = count > 0 ? totalSim / count : 0.0;
                    output.append(String.format("%-12.3f", avgSim));
                }
            }
            output.append("\n");
        }
        output.append("\n");

        return output.toString();
    }
}