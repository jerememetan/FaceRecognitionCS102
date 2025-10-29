// ============================================================================
// CRITICAL DEBUG CODE - Run this to find embedding similarity issues
// ============================================================================

package test;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;

public class DebugEmbeddingIssues {

    private static final int EMBEDDING_SIZE = 512;
    private static final String DATA_PATH = "./data/facedata";

    public static void main(String[] args) throws Exception {
        System.out.println("🔴 ARCFACE EMBEDDING DEBUG ANALYSIS");
        System.out.println("=".repeat(120));

        // Load all embeddings grouped by person
        Map<String, List<float[]>> personEmbeddings = loadEmbeddingsByPerson();

        // ISSUE #1: Check if all embeddings are virtually identical
        debugIssue1_AllSimilar(personEmbeddings);

        // ISSUE #2: Check if person averaging is being done
        debugIssue2_NoAveraging(personEmbeddings);

        // ISSUE #3: Check embedding distribution
        debugIssue3_EmbeddingDistribution(personEmbeddings);

        // ISSUE #4: Analyze inter-person similarity
        debugIssue4_InterPersonSimilarity(personEmbeddings);

        // ISSUE #5: Check if embeddings from stuffed animal are being generated
        debugIssue5_InvalidSubjects(personEmbeddings);
    }

    // ========================================================================
    // ISSUE #1: Are ALL embeddings too similar?
    // ========================================================================
    private static void debugIssue1_AllSimilar(Map<String, List<float[]>> personEmbeddings) {
        System.out.println("\n🔍 ISSUE #1: Checking if ALL embeddings are too similar");
        System.out.println("-".repeat(120));

        List<float[]> allEmbeddings = new ArrayList<>();
        for (List<float[]> embList : personEmbeddings.values()) {
            allEmbeddings.addAll(embList);
        }

        System.out.println("Total embeddings: " + allEmbeddings.size());

        // Calculate all-pairs similarities
        double[] similarities = new double[allEmbeddings.size() * (allEmbeddings.size() - 1) / 2];
        int idx = 0;
        for (int i = 0; i < allEmbeddings.size(); i++) {
            for (int j = i + 1; j < allEmbeddings.size(); j++) {
                similarities[idx++] = cosineSimilarity(allEmbeddings.get(i), allEmbeddings.get(j));
            }
        }

        // Statistics
        double min = Arrays.stream(similarities).min().orElse(0);
        double max = Arrays.stream(similarities).max().orElse(0);
        double avg = Arrays.stream(similarities).average().orElse(0);
        double median = getMedian(similarities);

        System.out.printf("Similarity statistics across ALL embeddings:\n");
        System.out.printf("  Min:    %.6f\n", min);
        System.out.printf("  Max:    %.6f\n", max);
        System.out.printf("  Avg:    %.6f\n", avg);
        System.out.printf("  Median: %.6f\n", median);
        System.out.printf("  Range:  %.6f\n\n", max - min);

        if (max - min < 0.05) {
            System.out.println("❌ PROBLEM: ALL embeddings are nearly identical!");
            System.out.println("   Expected range: 0.3 (min should be 0.2-0.3, max around 1.0)");
            System.out.println("   This suggests:");
            System.out.println("   - Model is outputting garbage");
            System.out.println("   - Face detection failing for non-face subjects");
            System.out.println("   - Invalid input to model\n");
        } else if (avg > 0.95) {
            System.out.println("⚠️  WARNING: Average similarity is very high (0.95+)!");
            System.out.println("   This suggests face images are from same session\n");
        }
    }

    // ========================================================================
    // ISSUE #2: Is the test comparing individual embeddings instead of averaging?
    // ========================================================================
    private static void debugIssue2_NoAveraging(Map<String, List<float[]>> personEmbeddings) {
        System.out.println("\n🔍 ISSUE #2: Checking if embeddings are being averaged by person");
        System.out.println("-".repeat(120));

        for (Map.Entry<String, List<float[]>> entry : personEmbeddings.entrySet()) {
            String person = entry.getKey();
            List<float[]> embeddings = entry.getValue();

            System.out.printf("\nPerson: %s (%d embeddings)\n", person, embeddings.size());

            if (embeddings.size() < 2) {
                System.out.println("  ⚠️  Only 1 embedding for this person");
                continue;
            }

            // Show first 5 embeddings' first 5 values
            System.out.println("  Individual embeddings (first 5 dimensions):");
            for (int i = 0; i < Math.min(5, embeddings.size()); i++) {
                System.out.printf("    Emb %d: ", i);
                for (int j = 0; j < 5; j++) {
                    System.out.printf("%.5f ", embeddings.get(i)[j]);
                }
                System.out.println();
            }

            // Calculate average
            float[] average = averageEmbeddings(embeddings);
            System.out.print("  AVERAGE : ");
            for (int j = 0; j < 5; j++) {
                System.out.printf("%.5f ", average[j]);
            }
            System.out.println();

            // Are embeddings diverse?
            double variance = 0;
            for (float[] emb : embeddings) {
                variance += cosineSimilarity(emb, average);
            }
            variance /= embeddings.size();

            System.out.printf("  Avg similarity to person average: %.6f\n", variance);
            if (variance > 0.99) {
                System.out.println("    ✅ Embeddings are very similar (same session)");
            } else if (variance > 0.90) {
                System.out.println("    ⚠️  Embeddings have some variation (different times)");
            } else {
                System.out.println("    ❌ Embeddings are very different (too much variation?)");
            }
        }
    }

    // ========================================================================
    // ISSUE #3: Check embedding distribution and magnitude
    // ========================================================================
    private static void debugIssue3_EmbeddingDistribution(Map<String, List<float[]>> personEmbeddings) {
        System.out.println("\n🔍 ISSUE #3: Analyzing embedding distribution");
        System.out.println("-".repeat(120));

        for (Map.Entry<String, List<float[]>> entry : personEmbeddings.entrySet()) {
            String person = entry.getKey();
            List<float[]> embeddings = entry.getValue();

            System.out.printf("\nPerson: %s\n", person);

            // Check magnitudes (should be ~1.0 for L2 normalized)
            System.out.print("  L2 Magnitudes: ");
            double[] magnitudes = new double[Math.min(5, embeddings.size())];
            for (int i = 0; i < magnitudes.length; i++) {
                magnitudes[i] = calculateMagnitude(embeddings.get(i));
                System.out.printf("%.4f ", magnitudes[i]);
            }
            System.out.println();

            // Check value ranges
            float minVal = Float.MAX_VALUE;
            float maxVal = -Float.MAX_VALUE;
            double sumAbsVal = 0;

            for (float[] emb : embeddings) {
                for (float val : emb) {
                    minVal = Math.min(minVal, val);
                    maxVal = Math.max(maxVal, val);
                    sumAbsVal += Math.abs(val);
                }
            }

            double avgAbsVal = sumAbsVal / (embeddings.size() * EMBEDDING_SIZE);
            System.out.printf("  Value range: [%.4f, %.4f]\n", minVal, maxVal);
            System.out.printf("  Average |value|: %.4f\n", avgAbsVal);

            if (avgAbsVal < 0.01) {
                System.out.println("    ❌ PROBLEM: Values are too small (near zero)!");
                System.out.println("       This suggests model failed or preprocessing wrong");
            } else if (avgAbsVal > 0.1) {
                System.out.println("    ⚠️  Values seem larger than typical");
            } else {
                System.out.println("    ✅ Value distribution looks reasonable");
            }
        }
    }

    // ========================================================================
    // ISSUE #4: Verify inter-person similarity is LOW
    // ========================================================================
    private static void debugIssue4_InterPersonSimilarity(Map<String, List<float[]>> personEmbeddings) {
        System.out.println("\n🔍 ISSUE #4: Checking inter-person similarity");
        System.out.println("-".repeat(120));

        List<String> persons = new ArrayList<>(personEmbeddings.keySet());

        if (persons.size() < 2) {
            System.out.println("Not enough persons for inter-person comparison");
            return;
        }

        System.out.printf("Comparing %d persons\n", persons.size());

        for (int i = 0; i < persons.size(); i++) {
            for (int j = i + 1; j < persons.size(); j++) {
                String p1 = persons.get(i);
                String p2 = persons.get(j);

                List<float[]> emb1 = personEmbeddings.get(p1);
                List<float[]> emb2 = personEmbeddings.get(p2);

                // Average embeddings for each person
                float[] avg1 = averageEmbeddings(emb1);
                float[] avg2 = averageEmbeddings(emb2);

                double similarity = cosineSimilarity(avg1, avg2);

                System.out.printf("%s vs %s: %.6f", p1, p2, similarity);

                if (similarity > 0.75) {
                    System.out.println(" ❌ TOO HIGH!");
                } else if (similarity > 0.5) {
                    System.out.println(" ⚠️  MODERATE");
                } else {
                    System.out.println(" ✅ GOOD");
                }
            }
        }
    }

    // ========================================================================
    // ISSUE #5: Check if stuffed animal embeddings are being generated
    // ========================================================================
    private static void debugIssue5_InvalidSubjects(Map<String, List<float[]>> personEmbeddings) {
        System.out.println("\n🔍 ISSUE #5: Checking for invalid subjects (stuffed animals)");
        System.out.println("-".repeat(120));

        System.out.println("Persons found in dataset:");
        for (String person : personEmbeddings.keySet()) {
            System.out.printf("  - %s (%d embeddings)\n", person, personEmbeddings.get(person).size());

            // Check if this is actually a face
            if (person.toLowerCase().contains("animal") ||
                    person.toLowerCase().contains("toy") ||
                    person.toLowerCase().contains("doll")) {
                System.out.println("    ⚠️  WARNING: This might not be a real face!");
            }
        }

        // Cross-check: If you have a stuffed animal, its embeddings should be VERY
        // different
        List<String> persons = new ArrayList<>(personEmbeddings.keySet());
        if (persons.size() >= 2) {
            float[] emb1 = averageEmbeddings(personEmbeddings.get(persons.get(0)));
            float[] emb2 = averageEmbeddings(personEmbeddings.get(persons.get(1)));

            double similarity = cosineSimilarity(emb1, emb2);
            System.out.printf("\nSimilarity between first two subjects: %.6f\n", similarity);

            if (similarity > 0.98) {
                System.out.println("❌ MAJOR PROBLEM: Different subjects have 0.98+ similarity!");
                System.out.println("   This means:");
                System.out.println("   1. Face detection failing (no face found, default output)");
                System.out.println("   2. Model output is constant/garbage");
                System.out.println("   3. Alignment failing for non-face");
                System.out.println("\n   FIX: Verify face detection returns valid faces for all images");
                System.out.println("        Check if stuffed animal image is being processed correctly");
            }
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private static Map<String, List<float[]>> loadEmbeddingsByPerson() throws Exception {
        Map<String, List<float[]>> result = new HashMap<>();

        File dataDir = new File(DATA_PATH);
        File[] personDirs = dataDir.listFiles(File::isDirectory);

        if (personDirs == null) {
            System.out.println("No person directories found at: " + DATA_PATH);
            return result;
        }

        for (File personDir : personDirs) {
            String personName = personDir.getName();
            List<float[]> embeddings = new ArrayList<>();

            File[] embFiles = personDir.listFiles((dir, name) -> name.endsWith(".emb"));
            if (embFiles == null)
                continue;

            for (File embFile : embFiles) {
                byte[] data = Files.readAllBytes(embFile.toPath());
                float[] embedding = byteArrayToFloatArray(data);
                embeddings.add(embedding);
            }

            result.put(personName, embeddings);
        }

        return result;
    }

    private static byte[] floatArrayToByteArray(float[] floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4);
        for (float f : floats) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }

    private static float[] byteArrayToFloatArray(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) {
            floats[i] = buffer.getFloat();
        }
        return floats;
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        double dotProduct = 0;
        double normA = 0;
        double normB = 0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0)
            return 0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static float[] averageEmbeddings(List<float[]> embeddings) {
        float[] average = new float[EMBEDDING_SIZE];
        for (float[] emb : embeddings) {
            for (int i = 0; i < EMBEDDING_SIZE; i++) {
                average[i] += emb[i] / embeddings.size();
            }
        }
        return average;
    }

    private static double calculateMagnitude(float[] vec) {
        double sum = 0;
        for (float v : vec) {
            sum += v * v;
        }
        return Math.sqrt(sum);
    }

    private static double getMedian(double[] values) {
        Arrays.sort(values);
        if (values.length % 2 == 0) {
            return (values[values.length / 2 - 1] + values[values.length / 2]) / 2;
        }
        return values[values.length / 2];
    }
}