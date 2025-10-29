package test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced Embedding Quality Test - Comprehensive analysis of embedding quality
 * Tests intra-person similarity, inter-person separation, embedding validity,
 * and provides detailed quality metrics and recommendations.
 */
public class EnhancedEmbeddingQualityTest {

    static {
        System.load(new File("lib/opencv_java480.dll").getAbsolutePath());
    }

    // Quality thresholds
    private static final double EXCELLENT_INTRA_SIM = 0.85;
    private static final double GOOD_INTRA_SIM = 0.75;
    private static final double POOR_INTRA_SIM = 0.60;

    private static final double EXCELLENT_SEPARATION = 0.30;
    private static final double GOOD_SEPARATION = 0.20;
    private static final double POOR_SEPARATION = 0.10;

    public static void main(String[] args) {
        System.out.println("🎯 Enhanced Embedding Quality Test");
        System.out.println("==================================\n");

        try {
            EnhancedEmbeddingQualityTest test = new EnhancedEmbeddingQualityTest();
            test.runComprehensiveQualityAnalysis();
        } catch (Exception e) {
            System.err.println("❌ Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void runComprehensiveQualityAnalysis() throws IOException {
        System.out.println("🔍 Starting comprehensive embedding quality analysis...\n");

        // Load all embeddings
        Map<String, List<byte[]>> personEmbeddings = loadAllEmbeddings();
        List<String> personNames = new ArrayList<>(personEmbeddings.keySet());

        if (personEmbeddings.isEmpty()) {
            System.out.println("❌ No valid embeddings found!");
            System.out.println("💡 Make sure you have .emb files in data/facedata/ subdirectories");
            return;
        }

        // Basic statistics
        printEmbeddingOverview(personEmbeddings, personNames);

        // Quality analysis
        EmbeddingQualityMetrics metrics = analyzeEmbeddingQuality(personEmbeddings, personNames);

        // Detailed results
        printQualityAssessment(metrics);

        // Recommendations
        printRecommendations(metrics);

        // Summary score
        printOverallScore(metrics);
    }

    private Map<String, List<byte[]>> loadAllEmbeddings() throws IOException {
        Map<String, List<byte[]>> embeddings = new HashMap<>();
        String facedataPath = "./data/facedata";
        File facedataDir = new File(facedataPath);

        if (!facedataDir.exists() || !facedataDir.isDirectory()) {
            throw new IOException("Facedata directory not found: " + facedataPath);
        }

        System.out.println("📂 Loading embeddings from: " + facedataDir.getAbsolutePath());

        File[] personDirs = facedataDir.listFiles(File::isDirectory);
        if (personDirs == null || personDirs.length == 0) {
            throw new IOException("No person directories found in: " + facedataPath);
        }

        System.out.println("👥 Found " + personDirs.length + " person directories");

        int totalValidEmbeddings = 0;
        int totalInvalidEmbeddings = 0;

        for (File personDir : personDirs) {
            String personName = personDir.getName();
            List<byte[]> personEmbs = new ArrayList<>();

            File[] embFiles = personDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".emb"));
            if (embFiles != null && embFiles.length > 0) {
                System.out.println("  " + personName + ": found " + embFiles.length + " .emb files");

                int validCount = 0;
                int invalidCount = 0;

                for (File embFile : embFiles) {
                    try {
                        byte[] emb = Files.readAllBytes(embFile.toPath());
                        if (isEmbeddingValid(emb)) {
                            personEmbs.add(emb);
                            validCount++;
                            totalValidEmbeddings++;
                        } else {
                            invalidCount++;
                            totalInvalidEmbeddings++;
                            System.out.println("    ⚠️  Invalid embedding: " + embFile.getName());
                        }
                    } catch (Exception e) {
                        invalidCount++;
                        totalInvalidEmbeddings++;
                        System.out.println("    ❌ Failed to load: " + embFile.getName() + " - " + e.getMessage());
                    }
                }

                System.out.println("    ✅ Valid: " + validCount + ", Invalid: " + invalidCount);

            } else {
                System.out.println("  " + personName + ": no .emb files found");
            }

            if (!personEmbs.isEmpty()) {
                embeddings.put(personName, personEmbs);
            }
        }

        System.out.println("\n📊 Loading Summary:");
        System.out.println("  Total valid embeddings: " + totalValidEmbeddings);
        System.out.println("  Total invalid embeddings: " + totalInvalidEmbeddings);
        System.out.println("  Persons with embeddings: " + embeddings.size());
        System.out.println();

        return embeddings;
    }

    private void printEmbeddingOverview(Map<String, List<byte[]>> personEmbeddings, List<String> personNames) {
        System.out.println("📊 EMBEDDING OVERVIEW");
        System.out.println("=".repeat(50));

        System.out.println("👥 Persons with embeddings:");
        for (String person : personNames) {
            List<byte[]> embs = personEmbeddings.get(person);
            int embSize = embs.get(0).length;
            String embType = embSize == 512 ? "Float32 (ArcFace)" : embSize == 1024 ? "Float64 (Legacy)" : "Unknown";
            System.out.printf("  %-15s: %2d embeddings (%s)%n", person, embs.size(), embType);
        }
        System.out.println();
    }

    private EmbeddingQualityMetrics analyzeEmbeddingQuality(Map<String, List<byte[]>> personEmbeddings,
            List<String> personNames) {
        EmbeddingQualityMetrics metrics = new EmbeddingQualityMetrics();

        System.out.println("🔬 ANALYZING EMBEDDING QUALITY");
        System.out.println("=".repeat(50));

        // Calculate intra-person similarities
        System.out.println("🎯 Calculating intra-person similarities...");
        metrics.intraSimilarities = calculateIntraPersonSimilarities(personEmbeddings);

        // Calculate inter-person similarities
        System.out.println("🚫 Calculating inter-person similarities...");
        metrics.interSimilarities = calculateInterPersonSimilarities(personEmbeddings, personNames);

        // Calculate quality metrics
        metrics.calculateQualityMetrics();

        // Detect outliers
        System.out.println("🔍 Detecting embedding outliers...");
        metrics.outlierAnalysis = detectOutliers(personEmbeddings);

        System.out.println("✅ Quality analysis complete\n");

        return metrics;
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
                    double sim = calculateSimilarity(embeddings.get(i), embeddings.get(j));
                    similarities.add(sim);
                }
            }

            intraSimilarities.put(personName, similarities);
        }

        return intraSimilarities;
    }

    private List<Double> calculateInterPersonSimilarities(Map<String, List<byte[]>> personEmbeddings,
            List<String> personNames) {
        List<Double> interSimilarities = new ArrayList<>();

        // Sample embeddings from different persons (limit to avoid too many
        // calculations)
        for (int i = 0; i < personNames.size(); i++) {
            for (int j = i + 1; j < personNames.size(); j++) {
                String person1 = personNames.get(i);
                String person2 = personNames.get(j);

                List<byte[]> embs1 = personEmbeddings.get(person1);
                List<byte[]> embs2 = personEmbeddings.get(person2);

                // Sample up to 5 embeddings from each person
                int sampleSize = Math.min(5, Math.min(embs1.size(), embs2.size()));

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

    private OutlierAnalysis detectOutliers(Map<String, List<byte[]>> personEmbeddings) {
        OutlierAnalysis analysis = new OutlierAnalysis();

        for (Map.Entry<String, List<byte[]>> entry : personEmbeddings.entrySet()) {
            String personName = entry.getKey();
            List<byte[]> embeddings = entry.getValue();

            if (embeddings.size() < 3)
                continue; // Need at least 3 for meaningful analysis

            // Calculate average similarity to other embeddings of same person
            List<Double> avgSimilarities = new ArrayList<>();

            for (int i = 0; i < embeddings.size(); i++) {
                double sum = 0.0;
                int count = 0;

                for (int j = 0; j < embeddings.size(); j++) {
                    if (i != j) {
                        sum += calculateSimilarity(embeddings.get(i), embeddings.get(j));
                        count++;
                    }
                }

                double avgSim = count > 0 ? sum / count : 0.0;
                avgSimilarities.add(avgSim);
            }

            // Find outliers (similarity more than 2 standard deviations below mean)
            double mean = avgSimilarities.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double variance = avgSimilarities.stream().mapToDouble(sim -> Math.pow(sim - mean, 2)).average()
                    .orElse(0.0);
            double stdDev = Math.sqrt(variance);

            double outlierThreshold = mean - 2 * stdDev;

            for (int i = 0; i < avgSimilarities.size(); i++) {
                if (avgSimilarities.get(i) < outlierThreshold) {
                    analysis.outliers.add(new OutlierInfo(personName, i, avgSimilarities.get(i), outlierThreshold));
                }
            }
        }

        return analysis;
    }

    private boolean isEmbeddingValid(byte[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return false;
        }

        // Check expected sizes (ArcFace: 512 floats = 2048 bytes for float32)
        boolean isFloatEmbedding = embedding.length == 512 * 4; // 2048 bytes
        boolean isDoubleEmbedding = embedding.length == 512 * 8; // 4096 bytes

        if (!isFloatEmbedding && !isDoubleEmbedding) {
            System.err.println("  ⚠️ Unexpected embedding size: " + embedding.length +
                    " bytes (expected 2048 for float32 or 4096 for float64)");
            return false;
        }

        try {
            double magnitude = 0.0;
            int validCount = 0;
            boolean hasNaN = false;
            boolean hasInf = false;

            if (isFloatEmbedding) {
                float[] floats = byteArrayToFloatArray(embedding);
                for (float f : floats) {
                    if (Float.isNaN(f))
                        hasNaN = true;
                    if (Float.isInfinite(f))
                        hasInf = true;
                    magnitude += f * f;
                    if (Math.abs(f) > 1e-6)
                        validCount++;
                }
            } else {
                double[] doubles = byteArrayToDoubleArray(embedding);
                for (double d : doubles) {
                    if (Double.isNaN(d))
                        hasNaN = true;
                    if (Double.isInfinite(d))
                        hasInf = true;
                    magnitude += d * d;
                    if (Math.abs(d) > 1e-6)
                        validCount++;
                }
            }

            if (hasNaN || hasInf) {
                return false;
            }

            magnitude = Math.sqrt(magnitude);
            if (magnitude < 0.5 || magnitude > 1.5) {
                return false; // Clearly corrupted or not normalized properly
            }

            double nonZeroRatio = (double) validCount / 512;
            if (nonZeroRatio < 0.05) { // Only 5% non-zero - clearly corrupted
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private double calculateSimilarity(byte[] emb1, byte[] emb2) {
        if (emb1 == null || emb2 == null || emb1.length != emb2.length) {
            return 0.0;
        }

        try {
            // Always use cosine similarity for embeddings (both are unit vectors after
            // normalization)
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

        // Calculate magnitudes
        double mag1 = 0, mag2 = 0;
        for (float f : vec1)
            mag1 += f * f;
        for (float f : vec2)
            mag2 += f * f;
        mag1 = Math.sqrt(mag1);
        mag2 = Math.sqrt(mag2);

        // ✅ PROPER COSINE SIMILARITY FORMULA
        double dotProduct = 0.0;
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
        }

        // ✅ DIVIDE BY MAGNITUDES!
        if (mag1 > 0 && mag2 > 0) {
            return Math.max(-1.0, Math.min(1.0, dotProduct / (mag1 * mag2)));
        }
        return 0.0;
    }

    private double calculateCosineSimilarityDouble(byte[] emb1, byte[] emb2) {
        double[] vec1 = byteArrayToDoubleArray(emb1);
        double[] vec2 = byteArrayToDoubleArray(emb2);

        // Calculate magnitudes
        double mag1 = 0, mag2 = 0;
        for (double d : vec1)
            mag1 += d * d;
        for (double d : vec2)
            mag2 += d * d;
        mag1 = Math.sqrt(mag1);
        mag2 = Math.sqrt(mag2);

        // ✅ PROPER COSINE SIMILARITY FORMULA
        double dotProduct = 0.0;
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
        }

        // ✅ DIVIDE BY MAGNITUDES!
        if (mag1 > 0 && mag2 > 0) {
            return Math.max(-1.0, Math.min(1.0, dotProduct / (mag1 * mag2)));
        }
        return 0.0;
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

    private void printQualityAssessment(EmbeddingQualityMetrics metrics) {
        System.out.println("📈 QUALITY ASSESSMENT");
        System.out.println("=".repeat(50));

        // Intra-person analysis
        System.out.println("\n🎯 Intra-person Similarities (Same Person):");
        System.out.println("-".repeat(40));

        for (Map.Entry<String, List<Double>> entry : metrics.intraSimilarities.entrySet()) {
            String person = entry.getKey();
            List<Double> sims = entry.getValue();

            if (!sims.isEmpty()) {
                double avg = sims.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                double min = sims.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
                double max = sims.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

                String quality = getSimilarityQuality(avg, true);
                System.out.printf("  %-15s: %.3f avg (%.3f - %.3f) %s [%d pairs]%n",
                        person, avg, min, max, quality, sims.size());
            }
        }

        // Inter-person analysis
        System.out.println("\n🚫 Inter-person Similarities (Different Persons):");
        System.out.println("-".repeat(40));

        if (!metrics.interSimilarities.isEmpty()) {
            double avg = metrics.interSimilarities.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double min = metrics.interSimilarities.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            double max = metrics.interSimilarities.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

            String quality = getSimilarityQuality(avg, false);
            System.out.printf("  Overall: %.3f avg (%.3f - %.3f) %s [%d comparisons]%n",
                    avg, min, max, quality, metrics.interSimilarities.size());
        }

        // Separation analysis
        System.out.println("\n📊 Separation Analysis:");
        System.out.println("-".repeat(40));
        System.out.printf("  Average intra-person similarity: %.3f%n", metrics.avgIntraSimilarity);
        System.out.printf("  Average inter-person similarity: %.3f%n", metrics.avgInterSimilarity);
        System.out.printf("  Separation margin: %.3f%n", metrics.separationMargin);

        String sepQuality = getSeparationQuality(metrics.separationMargin);
        System.out.println("  Quality: " + sepQuality);

        // Outlier analysis
        if (!metrics.outlierAnalysis.outliers.isEmpty()) {
            System.out.println("\n⚠️  Outlier Detection:");
            System.out.println("-".repeat(40));
            System.out.println("  Found " + metrics.outlierAnalysis.outliers.size() + " potential outlier embeddings:");

            for (OutlierInfo outlier : metrics.outlierAnalysis.outliers) {
                System.out.printf("    %s embedding #%d: %.3f (threshold: %.3f)%n",
                        outlier.personName, outlier.embeddingIndex, outlier.similarity, outlier.threshold);
            }
        } else {
            System.out.println("\n✅ No outliers detected - all embeddings are well-clustered");
        }

        System.out.println();
    }

    private void printRecommendations(EmbeddingQualityMetrics metrics) {
        System.out.println("💡 RECOMMENDATIONS");
        System.out.println("=".repeat(50));

        List<String> recommendations = new ArrayList<>();

        // Check intra-person quality
        if (metrics.avgIntraSimilarity < GOOD_INTRA_SIM) {
            recommendations.add(
                    "⚠️  Intra-person similarities are low. Consider re-capturing face data with better lighting and angles.");
        }

        // Check separation
        if (metrics.separationMargin < GOOD_SEPARATION) {
            recommendations.add(
                    "⚠️  Poor separation between persons. The system may have difficulty distinguishing between different people.");
        }

        // Check for outliers
        if (!metrics.outlierAnalysis.outliers.isEmpty()) {
            recommendations.add(
                    "⚠️  Outlier embeddings detected. Consider removing and re-capturing these problematic face images.");
        }

        // Check minimum embeddings per person
        boolean hasInsufficientData = metrics.intraSimilarities.values().stream()
                .anyMatch(sims -> sims.size() < 3);

        if (hasInsufficientData) {
            recommendations.add(
                    "⚠️  Some persons have fewer than 3 embeddings. Capture more face images for better recognition accuracy.");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("✅ Embedding quality looks good! The system should perform well.");
        }

        for (String rec : recommendations) {
            System.out.println(rec);
        }
        System.out.println();
    }

    private void printOverallScore(EmbeddingQualityMetrics metrics) {
        System.out.println("🏆 OVERALL QUALITY SCORE");
        System.out.println("=".repeat(50));

        double score = calculateOverallScore(metrics);
        String grade = getGrade(score);

        System.out.printf("  Score: %.1f/100 (%s)%n", score, grade);

        // Detailed breakdown
        System.out.println("\n  Breakdown:");
        System.out.printf("    Intra-person consistency: %.1f/25%n",
                Math.min(25, metrics.avgIntraSimilarity * 25 / EXCELLENT_INTRA_SIM));
        System.out.printf("    Inter-person separation: %.1f/25%n",
                Math.min(25, Math.max(0, metrics.separationMargin * 25 / EXCELLENT_SEPARATION)));
        System.out.printf("    Data quality: %.1f/25%n", 25.0 - (metrics.outlierAnalysis.outliers.size() * 5.0));
        System.out.printf("    Data quantity: %.1f/25%n", Math.min(25, metrics.totalEmbeddings / 2.0));

        System.out.println("\n  Interpretation:");
        if (score >= 90) {
            System.out.println("    🏆 Excellent! Ready for production use.");
        } else if (score >= 75) {
            System.out.println("    ✅ Good! Should work well for most scenarios.");
        } else if (score >= 60) {
            System.out.println("    ⚠️  Fair. May have occasional recognition issues.");
        } else {
            System.out.println("    ❌ Poor. Significant improvements needed before use.");
        }

        System.out.println();
    }

    private double calculateOverallScore(EmbeddingQualityMetrics metrics) {
        double score = 0;

        // Intra-person consistency (0-25 points)
        score += Math.min(25, metrics.avgIntraSimilarity * 25 / EXCELLENT_INTRA_SIM);

        // Inter-person separation (0-25 points)
        score += Math.min(25, Math.max(0, metrics.separationMargin * 25 / EXCELLENT_SEPARATION));

        // Data quality (0-25 points, reduced by outliers)
        score += Math.max(0, 25.0 - (metrics.outlierAnalysis.outliers.size() * 5.0));

        // Data quantity (0-25 points)
        score += Math.min(25, metrics.totalEmbeddings / 2.0); // 2 points per embedding, max 25

        return Math.max(0, Math.min(100, score));
    }

    private String getGrade(double score) {
        if (score >= 90)
            return "A";
        else if (score >= 80)
            return "B";
        else if (score >= 70)
            return "C";
        else if (score >= 60)
            return "D";
        else
            return "F";
    }

    private String getSimilarityQuality(double similarity, boolean isIntra) {
        if (isIntra) {
            if (similarity >= EXCELLENT_INTRA_SIM)
                return "🟢 Excellent";
            else if (similarity >= GOOD_INTRA_SIM)
                return "🟡 Good";
            else if (similarity >= POOR_INTRA_SIM)
                return "🟠 Fair";
            else
                return "🔴 Poor";
        } else {
            if (similarity <= 0.3)
                return "🟢 Excellent";
            else if (similarity <= 0.4)
                return "🟡 Good";
            else if (similarity <= 0.5)
                return "🟠 Fair";
            else
                return "🔴 Poor";
        }
    }

    private String getSeparationQuality(double separation) {
        if (separation >= EXCELLENT_SEPARATION)
            return "🟢 Excellent separation";
        else if (separation >= GOOD_SEPARATION)
            return "🟡 Good separation";
        else if (separation >= POOR_SEPARATION)
            return "🟠 Moderate separation";
        else
            return "🔴 Poor separation - high risk of confusion";
    }

    // Inner classes for data structures
    private static class EmbeddingQualityMetrics {
        Map<String, List<Double>> intraSimilarities = new HashMap<>();
        List<Double> interSimilarities = new ArrayList<>();
        OutlierAnalysis outlierAnalysis = new OutlierAnalysis();

        double avgIntraSimilarity = 0.0;
        double avgInterSimilarity = 0.0;
        double separationMargin = 0.0;
        int totalEmbeddings = 0;

        void calculateQualityMetrics() {
            // Calculate averages
            List<Double> allIntra = intraSimilarities.values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toList());

            if (!allIntra.isEmpty()) {
                avgIntraSimilarity = allIntra.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            }

            if (!interSimilarities.isEmpty()) {
                avgInterSimilarity = interSimilarities.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            }

            separationMargin = avgIntraSimilarity - avgInterSimilarity;

            // Count total embeddings
            totalEmbeddings = intraSimilarities.values().stream()
                    .mapToInt(List::size)
                    .sum();
        }
    }

    private static class OutlierAnalysis {
        List<OutlierInfo> outliers = new ArrayList<>();
    }

    private static class OutlierInfo {
        String personName;
        int embeddingIndex;
        double similarity;
        double threshold;

        OutlierInfo(String personName, int embeddingIndex, double similarity, double threshold) {
            this.personName = personName;
            this.embeddingIndex = embeddingIndex;
            this.similarity = similarity;
            this.threshold = threshold;
        }
    }
}