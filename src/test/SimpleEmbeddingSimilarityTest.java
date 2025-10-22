package test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple embedding similarity test that loads and compares embeddings directly.
 * This version doesn't use FaceEmbeddingGenerator to avoid initialization issues.
 */
public class SimpleEmbeddingSimilarityTest {

    static {
        System.load(new File("lib/opencv_java480.dll").getAbsolutePath());
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Simple Embedding Similarity Test ===\n");

        SimpleEmbeddingSimilarityTest test = new SimpleEmbeddingSimilarityTest();
        test.runSimilarityAnalysis();
    }

    public void runSimilarityAnalysis() {
        try {
            // Load all embeddings
            Map<String, List<byte[]>> personEmbeddings = loadAllEmbeddings();
            List<String> personNames = new ArrayList<>(personEmbeddings.keySet());

            if (personEmbeddings.isEmpty()) {
                System.out.println("❌ No embeddings found!");
                return;
            }

            System.out.println("📊 Loaded embeddings for " + personNames.size() + " persons:");
            for (String person : personNames) {
                System.out.println("  " + person + ": " + personEmbeddings.get(person).size() + " embeddings");
            }
            System.out.println();

            // Calculate intra-person similarities
            System.out.println("🔍 Calculating intra-person similarities...");
            Map<String, List<Double>> intraSimilarities = calculateIntraPersonSimilarities(personEmbeddings);

            // Calculate inter-person similarities
            System.out.println("🔍 Calculating inter-person similarities...");
            List<Double> interSimilarities = calculateInterPersonSimilarities(personEmbeddings, personNames);

            // Print statistics
            printStatistics(intraSimilarities, interSimilarities, personNames);

        } catch (Exception e) {
            System.err.println("❌ Error during similarity analysis: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Map<String, List<byte[]>> loadAllEmbeddings() throws IOException {
        Map<String, List<byte[]>> embeddings = new HashMap<>();

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
                        if (isEmbeddingValid(emb)) {
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

    private boolean isEmbeddingValid(byte[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return false;
        }

        boolean isFloatEmbedding = embedding.length == 512 * 4;
        boolean isDoubleEmbedding = embedding.length == 512 * 8;

        if (!isFloatEmbedding && !isDoubleEmbedding) {
            System.err.println("Invalid embedding size: " + embedding.length + ", expected 2048 or 4096 bytes");
            return false;
        }

        try {
            double magnitude = 0.0;
            int validCount = 0;

            if (isFloatEmbedding) {
                float[] floats = byteArrayToFloatArray(embedding);
                for (float f : floats) {
                    if (Float.isNaN(f) || Float.isInfinite(f)) {
                        return false;
                    }
                    magnitude += f * f;
                    if (Math.abs(f) > 1e-6) validCount++;
                }
            } else {
                double[] doubles = byteArrayToDoubleArray(embedding);
                for (double d : doubles) {
                    if (Double.isNaN(d) || Double.isInfinite(d)) {
                        return false;
                    }
                    magnitude += d * d;
                    if (Math.abs(d) > 1e-6) validCount++;
                }
            }

            magnitude = Math.sqrt(magnitude);
            if (magnitude < 0.5 || magnitude > 1.5) {
                return false;
            }

            double nonZeroRatio = (double) validCount / 512;
            if (nonZeroRatio < 0.05) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, List<Double>> calculateIntraPersonSimilarities(Map<String, List<byte[]>> personEmbeddings) {
        Map<String, List<Double>> intraSimilarities = new HashMap<>();

        for (Map.Entry<String, List<byte[]>> entry : personEmbeddings.entrySet()) {
            String personName = entry.getKey();
            List<byte[]> embeddings = entry.getValue();
            List<Double> similarities = new ArrayList<>();

            for (int i = 0; i < embeddings.size(); i++) {
                for (int j = i + 1; j < embeddings.size(); j++) {
                    double sim = calculateSimilarity(embeddings.get(i), embeddings.get(j));
                    similarities.add(sim);
                }
            }

            intraSimilarities.put(personName, similarities);
        }

        return intraSimilarities;
    }

    private List<Double> calculateInterPersonSimilarities(Map<String, List<byte[]>> personEmbeddings, List<String> personNames) {
        List<Double> interSimilarities = new ArrayList<>();

        for (int i = 0; i < personNames.size(); i++) {
            for (int j = i + 1; j < personNames.size(); j++) {
                String person1 = personNames.get(i);
                String person2 = personNames.get(j);

                List<byte[]> embs1 = personEmbeddings.get(person1);
                List<byte[]> embs2 = personEmbeddings.get(person2);

                int sampleSize = Math.min(3, Math.min(embs1.size(), embs2.size()));

                for (int k = 0; k < sampleSize; k++) {
                    for (int l = 0; l < sampleSize; l++) {
                        double sim = calculateSimilarity(embs1.get(k), embs2.get(l));
                        interSimilarities.add(sim);
                    }
                }
            }
        }

        return interSimilarities;
    }

    private double calculateSimilarity(byte[] emb1, byte[] emb2) {
        if (emb1 == null || emb2 == null || emb1.length != emb2.length) {
            return 0.0;
        }

        try {
            // Always use cosine similarity for embeddings (both are unit vectors after normalization)
            if (emb1.length == 512 * 4) { // Float32 embeddings (ArcFace)
                return calculateCosineSimilarity(emb1, emb2);
            } else if (emb1.length == 512 * 8) { // Float64 embeddings (Legacy)
                return calculateCosineSimilarityDouble(emb1, emb2);
            } else {
                return 0.0;
            }
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double calculateCosineSimilarity(byte[] emb1, byte[] emb2) {
        float[] vec1 = byteArrayToFloatArray(emb1);
        float[] vec2 = byteArrayToFloatArray(emb2);

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    private double calculateCosineSimilarityDouble(byte[] emb1, byte[] emb2) {
        double[] vec1 = byteArrayToDoubleArray(emb1);
        double[] vec2 = byteArrayToDoubleArray(emb2);

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    private float[] byteArrayToFloatArray(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) {
            floats[i] = buffer.getFloat();
        }
        return floats;
    }

    private double[] byteArrayToDoubleArray(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        double[] doubles = new double[bytes.length / 8];
        for (int i = 0; i < doubles.length; i++) {
            doubles[i] = buffer.getDouble();
        }
        return doubles;
    }

    private void printStatistics(Map<String, List<Double>> intraSimilarities, List<Double> interSimilarities, List<String> personNames) {
        System.out.println("📈 SIMILARITY STATISTICS");
        System.out.println("=" .repeat(50));

        System.out.println("\n🎯 Intra-person similarities (same person):");
        List<Double> allIntraSimilarities = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : intraSimilarities.entrySet()) {
            List<Double> sims = entry.getValue();
            if (!sims.isEmpty()) {
                allIntraSimilarities.addAll(sims);
                double avg = sims.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                double min = sims.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
                double max = sims.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
                System.out.printf("  %s: %.3f avg (%.3f - %.3f), %d pairs%n",
                    entry.getKey(), avg, min, max, sims.size());
            }
        }

        System.out.println("\n🚫 Inter-person similarities (different persons):");
        if (!interSimilarities.isEmpty()) {
            double avg = interSimilarities.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double min = interSimilarities.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            double max = interSimilarities.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            System.out.printf("  Overall: %.3f avg (%.3f - %.3f), %d comparisons%n",
                avg, min, max, interSimilarities.size());
        }

        System.out.println("\n📊 OVERALL ASSESSMENT:");
        if (!allIntraSimilarities.isEmpty() && !interSimilarities.isEmpty()) {
            double avgIntra = allIntraSimilarities.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double avgInter = interSimilarities.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double separation = avgIntra - avgInter;

            System.out.printf("  Average intra-person similarity: %.3f%n", avgIntra);
            System.out.printf("  Average inter-person similarity: %.3f%n", avgInter);
            System.out.printf("  Separation margin: %.3f%n", separation);

            if (separation > 0.2) {
                System.out.println("  ✅ Good separation - embeddings are well-clustered");
            } else if (separation > 0.1) {
                System.out.println("  ⚠️  Moderate separation - some overlap possible");
            } else {
                System.out.println("  ❌ Poor separation - high risk of misidentification");
            }
        }
        System.out.println();
    }
}