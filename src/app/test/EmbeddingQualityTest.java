package app.test;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Comprehensive test to analyze embedding quality for all students
 * Generates a detailed report in the logs folder
 */
public class EmbeddingQualityTest {

    private static final String DATABASE_PATH = "data/facedata";
    private static final String LOGS_PATH = "logs";
    private static final double OUTLIER_THRESHOLD = 0.10;
    private static final double WEAK_THRESHOLD = 0.05;

    private static PrintWriter reportWriter;
    private static int totalStudents = 0;
    private static int studentsWithOutliers = 0;
    private static int totalOutliers = 0;
    private static int totalEmbeddings = 0;
    private static double sumTightness = 0.0;
    private static List<StudentReport> allReports = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("EMBEDDING QUALITY TEST - ALL STUDENTS");
        System.out.println("=".repeat(80));
        System.out.println();

        File dbDir = new File(DATABASE_PATH);
        if (!dbDir.exists() || !dbDir.isDirectory()) {
            System.err.println("Error: Database directory not found: " + DATABASE_PATH);
            return;
        }

        // Create report file
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        String reportPath = LOGS_PATH + "/embedding_quality_report_" + timestamp + ".txt";

        try {
            new File(LOGS_PATH).mkdirs();
            reportWriter = new PrintWriter(new FileWriter(reportPath));

            writeHeader();

            // Get all student folders
            File[] studentFolders = dbDir.listFiles(File::isDirectory);
            if (studentFolders == null || studentFolders.length == 0) {
                System.out.println("No student folders found in " + DATABASE_PATH);
                reportWriter.println("No student folders found.");
                return;
            }

            Arrays.sort(studentFolders, Comparator.comparing(File::getName));

            System.out.println("Found " + studentFolders.length + " student folder(s)");
            System.out.println("Analyzing embeddings...\n");

            // Analyze each student
            for (File studentFolder : studentFolders) {
                analyzeStudent(studentFolder);
            }

            // Write summary
            writeSummary();

            reportWriter.close();

            System.out.println("\n" + "=".repeat(80));
            System.out.println("ANALYSIS COMPLETE");
            System.out.println("=".repeat(80));
            System.out.println("Report saved to: " + reportPath);
            System.out.println();
            printConsoleSummary();

        } catch (IOException e) {
            System.err.println("Error creating report: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void writeHeader() {
        reportWriter.println("=".repeat(80));
        reportWriter.println("EMBEDDING QUALITY REPORT");
        reportWriter.println("=".repeat(80));
        reportWriter.println("Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        reportWriter.println("Database: " + new File(DATABASE_PATH).getAbsolutePath());
        reportWriter.println();
    }

    private static void analyzeStudent(File studentFolder) {
        String studentName = studentFolder.getName();
        totalStudents++;

        System.out.println("Analyzing: " + studentName);

        reportWriter.println("=".repeat(80));
        reportWriter.println("STUDENT: " + studentName);
        reportWriter.println("=".repeat(80));
        reportWriter.println();

        // Load all .emb files
        File[] embFiles = studentFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".emb"));
        if (embFiles == null || embFiles.length == 0) {
            String msg = "  ⚠ No embedding files found";
            System.out.println(msg);
            reportWriter.println(msg);
            reportWriter.println();
            return;
        }

        Arrays.sort(embFiles, Comparator.comparing(File::getName));

        // Load embeddings
        List<byte[]> embeddings = new ArrayList<>();
        List<String> fileNames = new ArrayList<>();
        List<String> filePaths = new ArrayList<>();

        for (File embFile : embFiles) {
            try {
                byte[] emb = loadEmbedding(embFile);
                if (emb != null) {
                    embeddings.add(emb);
                    fileNames.add(embFile.getName());
                    filePaths.add(embFile.getAbsolutePath());
                }
            } catch (IOException e) {
                reportWriter.println("  ⚠ Could not load: " + embFile.getName());
            }
        }

        totalEmbeddings += embeddings.size();

        reportWriter.println("Embeddings found: " + embeddings.size());
        reportWriter.println();

        if (embeddings.size() < 2) {
            String msg = "  ⚠ Insufficient embeddings for analysis (need at least 2)";
            System.out.println(msg);
            reportWriter.println(msg);
            reportWriter.println();
            return;
        }

        // Check normalization
        reportWriter.println("--- NORMALIZATION CHECK ---");
        double minNorm = Double.MAX_VALUE, maxNorm = 0, sumNorm = 0;
        boolean allNormalized = true;

        for (int i = 0; i < embeddings.size(); i++) {
            double norm = calculateNorm(embeddings.get(i));
            sumNorm += norm;
            if (norm < minNorm)
                minNorm = norm;
            if (norm > maxNorm)
                maxNorm = norm;

            if (Math.abs(norm - 1.0) > 0.01) {
                allNormalized = false;
                reportWriter.printf("  ⚠ %s: norm = %.6f (NOT normalized)%n", fileNames.get(i), norm);
            }
        }

        double avgNorm = sumNorm / embeddings.size();
        reportWriter.printf("Norm range: %.6f to %.6f (avg: %.6f)%n", minNorm, maxNorm, avgNorm);

        if (allNormalized) {
            reportWriter.println("✓ All embeddings properly L2-normalized");
        } else {
            reportWriter.println("✗ Some embeddings are NOT properly normalized!");
        }
        reportWriter.println();

        // Calculate tightness and find outliers
        reportWriter.println("--- QUALITY ANALYSIS ---");

        double[] avgSims = new double[embeddings.size()];
        double overallTightness = 0;
        int pairCount = 0;

        for (int i = 0; i < embeddings.size(); i++) {
            double sum = 0;
            int count = 0;
            for (int j = 0; j < embeddings.size(); j++) {
                if (i != j) {
                    double sim = cosineSimilarity(embeddings.get(i), embeddings.get(j));
                    sum += sim;
                    count++;
                    if (j > i) {
                        overallTightness += sim;
                        pairCount++;
                    }
                }
            }
            avgSims[i] = sum / count;
        }

        overallTightness /= pairCount;
        sumTightness += overallTightness;

        reportWriter.printf("Overall tightness: %.4f%n", overallTightness);

        // Classify quality
        String qualityLevel;
        String expectedScores;
        if (overallTightness >= 0.85) {
            qualityLevel = "EXCELLENT";
            expectedScores = "0.82-0.95";
        } else if (overallTightness >= 0.75) {
            qualityLevel = "GOOD";
            expectedScores = "0.75-0.90";
        } else if (overallTightness >= 0.65) {
            qualityLevel = "MODERATE";
            expectedScores = "0.65-0.85";
        } else {
            qualityLevel = "POOR";
            expectedScores = "0.55-0.75";
        }

        reportWriter.println("Quality level: " + qualityLevel);
        reportWriter.println("Expected live recognition scores: " + expectedScores);
        reportWriter.println();

        // Find outliers
        reportWriter.println("--- EMBEDDING RANKING ---");

        Integer[] indices = new Integer[embeddings.size()];
        for (int i = 0; i < indices.length; i++)
            indices[i] = i;
        Arrays.sort(indices, (a, b) -> Double.compare(avgSims[b], avgSims[a]));

        List<Integer> outlierIndices = new ArrayList<>();
        List<Integer> weakIndices = new ArrayList<>();

        for (int idx : indices) {
            String status;
            if (avgSims[idx] >= overallTightness + WEAK_THRESHOLD) {
                status = "✓ GOOD";
            } else if (avgSims[idx] >= overallTightness - WEAK_THRESHOLD) {
                status = "  OK  ";
            } else if (avgSims[idx] >= overallTightness - OUTLIER_THRESHOLD) {
                status = "⚠ WEAK";
                weakIndices.add(idx);
            } else {
                status = "✗ OUTLIER";
                outlierIndices.add(idx);
            }

            reportWriter.printf("%s  %s  %.4f  (dev: %+.3f)%n",
                    status, fileNames.get(idx), avgSims[idx], avgSims[idx] - overallTightness);
        }

        reportWriter.println();

        // Report outliers
        if (!outlierIndices.isEmpty() || !weakIndices.isEmpty()) {
            if (!outlierIndices.isEmpty()) {
                studentsWithOutliers++;
                totalOutliers += outlierIndices.size();

                reportWriter.println("--- OUTLIERS DETECTED ---");
                reportWriter.println("These embeddings should be REMOVED:");
                for (int idx : outlierIndices) {
                    reportWriter.println("  ✗ " + filePaths.get(idx));
                    System.out.println("  ✗ Outlier: " + fileNames.get(idx));
                }
                reportWriter.println();
            }

            if (!weakIndices.isEmpty()) {
                reportWriter.println("--- WEAK EMBEDDINGS ---");
                reportWriter.println("Consider removing if tightness needs improvement:");
                for (int idx : weakIndices) {
                    reportWriter.println("  ⚠ " + filePaths.get(idx));
                }
                reportWriter.println();
            }

            // Calculate improvement
            List<Double> goodSims = new ArrayList<>();
            Set<Integer> toRemove = new HashSet<>(outlierIndices);

            for (int i = 0; i < embeddings.size(); i++) {
                if (toRemove.contains(i))
                    continue;
                for (int j = i + 1; j < embeddings.size(); j++) {
                    if (toRemove.contains(j))
                        continue;
                    goodSims.add(cosineSimilarity(embeddings.get(i), embeddings.get(j)));
                }
            }

            if (!goodSims.isEmpty()) {
                double newTightness = goodSims.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                double improvement = newTightness - overallTightness;

                reportWriter.println("--- PROJECTED IMPROVEMENT ---");
                reportWriter.printf("Current tightness: %.4f%n", overallTightness);
                reportWriter.printf("After removing outliers: %.4f%n", newTightness);
                reportWriter.printf("Improvement: +%.4f (+%.1f%%)%n",
                        improvement, (improvement / overallTightness) * 100);
                reportWriter.printf("New expected scores: %.2f-%.2f%n",
                        newTightness - 0.05, newTightness + 0.15);
                reportWriter.println();
            }
        } else {
            reportWriter.println("✓ No outliers detected - all embeddings are of good quality");
            reportWriter.println();
            System.out.println("  ✓ No outliers");
        }

        // Store report for summary
        allReports.add(new StudentReport(
                studentName,
                embeddings.size(),
                overallTightness,
                qualityLevel,
                outlierIndices.size(),
                weakIndices.size(),
                allNormalized));

        reportWriter.println();
    }

    private static void writeSummary() {
        reportWriter.println("=".repeat(80));
        reportWriter.println("OVERALL SUMMARY");
        reportWriter.println("=".repeat(80));
        reportWriter.println();

        reportWriter.println("--- STATISTICS ---");
        reportWriter.println("Total students analyzed: " + totalStudents);
        reportWriter.println("Total embeddings: " + totalEmbeddings);
        reportWriter.println("Average embeddings per student: " +
                (totalStudents > 0 ? String.format("%.1f", (double) totalEmbeddings / totalStudents) : "0"));
        reportWriter.println();

        reportWriter.println("--- QUALITY METRICS ---");
        double avgTightness = totalStudents > 0 ? sumTightness / totalStudents : 0;
        reportWriter.printf("Average tightness: %.4f%n", avgTightness);
        reportWriter.println("Students with outliers: " + studentsWithOutliers +
                " (" + (totalStudents > 0 ? String.format("%.1f%%", (double) studentsWithOutliers / totalStudents * 100)
                        : "0%")
                + ")");
        reportWriter.println("Total outliers found: " + totalOutliers);
        reportWriter.println();

        // Quality distribution
        reportWriter.println("--- QUALITY DISTRIBUTION ---");
        Map<String, Long> qualityCounts = allReports.stream()
                .collect(Collectors.groupingBy(r -> r.qualityLevel, Collectors.counting()));

        reportWriter.println("EXCELLENT (≥0.85): " + qualityCounts.getOrDefault("EXCELLENT", 0L));
        reportWriter.println("GOOD (0.75-0.84): " + qualityCounts.getOrDefault("GOOD", 0L));
        reportWriter.println("MODERATE (0.65-0.74): " + qualityCounts.getOrDefault("MODERATE", 0L));
        reportWriter.println("POOR (<0.65): " + qualityCounts.getOrDefault("POOR", 0L));
        reportWriter.println();

        // Top performers
        if (!allReports.isEmpty()) {
            reportWriter.println("--- TOP 5 PERFORMERS (Highest Tightness) ---");
            allReports.stream()
                    .sorted((a, b) -> Double.compare(b.tightness, a.tightness))
                    .limit(5)
                    .forEach(r -> reportWriter.printf("  %s: %.4f (%s, %d embeddings)%n",
                            r.studentName, r.tightness, r.qualityLevel, r.embeddingCount));
            reportWriter.println();

            // Students needing attention
            List<StudentReport> needsAttention = allReports.stream()
                    .filter(r -> r.outlierCount > 0 || r.tightness < 0.70)
                    .sorted((a, b) -> Integer.compare(b.outlierCount, a.outlierCount))
                    .collect(Collectors.toList());

            if (!needsAttention.isEmpty()) {
                reportWriter.println("--- STUDENTS NEEDING ATTENTION ---");
                for (StudentReport r : needsAttention) {
                    reportWriter.printf("  %s: tightness=%.4f, outliers=%d, weak=%d%n",
                            r.studentName, r.tightness, r.outlierCount, r.weakCount);
                }
                reportWriter.println();
            }
        }

        reportWriter.println("--- RECOMMENDATIONS ---");
        if (studentsWithOutliers > 0) {
            reportWriter.println("✗ " + studentsWithOutliers + " student(s) have outlier embeddings");
            reportWriter.println("  → Remove outlier files listed above to improve recognition accuracy");
        }

        if (avgTightness < 0.70) {
            reportWriter.println("⚠ Overall average tightness is low (<0.70)");
            reportWriter.println("  → Consider re-capturing face images with better quality:");
            reportWriter.println("    • Consistent lighting");
            reportWriter.println("    • Sharp focus (not blurry)");
            reportWriter.println("    • Face straight-on (±15° max)");
            reportWriter.println("    • Neutral expression");
        } else if (avgTightness < 0.80) {
            reportWriter.println("✓ Average tightness is acceptable (0.70-0.80)");
            reportWriter.println("  → Can be improved by removing outliers and weak embeddings");
        } else {
            reportWriter.println("✓ Excellent overall quality (tightness ≥0.80)");
            reportWriter.println("  → Training data is in good shape");
        }

        reportWriter.println();
        reportWriter.println("=".repeat(80));
        reportWriter.println("END OF REPORT");
        reportWriter.println("=".repeat(80));
    }

    private static void printConsoleSummary() {
        System.out.println("Summary:");
        System.out.println("  Students analyzed: " + totalStudents);
        System.out.println("  Total embeddings: " + totalEmbeddings);
        System.out.printf("  Average tightness: %.4f%n", totalStudents > 0 ? sumTightness / totalStudents : 0);
        System.out.println("  Students with outliers: " + studentsWithOutliers);
        System.out.println("  Total outliers found: " + totalOutliers);

        if (totalOutliers > 0) {
            System.out.println();
            System.out.println("⚠ Action required: Remove " + totalOutliers + " outlier embedding(s)");
            System.out.println("  Check the report for specific file paths");
        } else {
            System.out.println();
            System.out.println("✓ All embeddings are of acceptable quality");
        }
    }

    private static byte[] loadEmbedding(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return fis.readAllBytes();
        }
    }

    private static double calculateNorm(byte[] embedding) {
        float[] vec = byteArrayToFloatArray(embedding);
        double sum = 0.0;
        for (float f : vec) {
            sum += f * f;
        }
        return Math.sqrt(sum);
    }

    private static double cosineSimilarity(byte[] emb1, byte[] emb2) {
        float[] vec1 = byteArrayToFloatArray(emb1);
        float[] vec2 = byteArrayToFloatArray(emb2);

        double dot = 0.0;
        for (int i = 0; i < vec1.length; i++) {
            dot += vec1[i] * vec2[i];
        }

        return Math.max(-1.0, Math.min(1.0, dot));
    }

    private static float[] byteArrayToFloatArray(byte[] bytes) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) {
            floats[i] = buffer.getFloat();
        }
        return floats;
    }

    static class StudentReport {
        String studentName;
        int embeddingCount;
        double tightness;
        String qualityLevel;
        int outlierCount;
        int weakCount;
        boolean allNormalized;

        StudentReport(String studentName, int embeddingCount, double tightness,
                String qualityLevel, int outlierCount, int weakCount, boolean allNormalized) {
            this.studentName = studentName;
            this.embeddingCount = embeddingCount;
            this.tightness = tightness;
            this.qualityLevel = qualityLevel;
            this.outlierCount = outlierCount;
            this.weakCount = weakCount;
            this.allNormalized = allNormalized;
        }
    }
}
