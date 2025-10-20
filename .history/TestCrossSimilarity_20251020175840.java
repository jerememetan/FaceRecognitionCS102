import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

public class TestCrossSimilarity {
    // Decode embedding from .emb file (assume 128 floats)
    public static double[] decodeEmbedding(byte[] emb) {
        if (emb == null || emb.length != 128 * 4)
            return null;
        float[] fv = new float[128];
        ByteBuffer bb = ByteBuffer.wrap(emb);
        for (int i = 0; i < 128; i++)
            fv[i] = bb.getFloat();
        double[] dv = new double[128];
        for (int i = 0; i < 128; i++)
            dv[i] = fv[i];
        // L2 normalize
        double norm = 0.0;
        for (double x : dv)
            norm += x * x;
        norm = Math.sqrt(Math.max(norm, 1e-12));
        for (int i = 0; i < dv.length; i++)
            dv[i] /= norm;
        return dv;
    }

    // Cosine similarity
    public static double cosineSimilarity(double[] a, double[] b) {
        if (a == null || b == null || a.length != b.length)
            return 0.0;
        double dot = 0.0;
        for (int i = 0; i < a.length; i++)
            dot += a[i] * b[i];
        return dot;
    }

    public static void main(String[] args) throws Exception {
        String baseDir = "data/facedata";
        File db = new File(baseDir);
        if (!db.exists() || !db.isDirectory()) {
            System.out.println("Database folder not found: " + baseDir);
            return;
        }
        // Map: student -> list of embeddings
        Map<String, List<double[]>> studentEmbeddings = new LinkedHashMap<>();
        for (File studentDir : db.listFiles()) {
            if (!studentDir.isDirectory())
                continue;
            String student = studentDir.getName();
            List<double[]> embList = new ArrayList<>();
            for (File embFile : studentDir.listFiles()) {
                if (!embFile.getName().toLowerCase().endsWith(".emb"))
                    continue;
                try (FileInputStream fis = new FileInputStream(embFile)) {
                    byte[] emb = fis.readAllBytes();
                    double[] vec = decodeEmbedding(emb);
                    if (vec != null)
                        embList.add(vec);
                } catch (Exception e) {
                    System.out.println("Failed to read " + embFile.getName() + ": " + e.getMessage());
                }
            }
            if (!embList.isEmpty())
                studentEmbeddings.put(student, embList);
        }
        // Print summary
        System.out.println("Loaded embeddings for " + studentEmbeddings.size() + " students.");
        for (String student : studentEmbeddings.keySet()) {
            System.out.println("  " + student + ": " + studentEmbeddings.get(student).size() + " embeddings");
        }
        // Compute cross-similarity matrix (average similarity between all pairs)
        List<String> students = new ArrayList<>(studentEmbeddings.keySet());
        double[][] matrix = new double[students.size()][students.size()];
        double maxSim = -2.0, minSim = 2.0;
        String maxPair = "", minPair = "";
        for (int i = 0; i < students.size(); i++) {
            for (int j = 0; j < students.size(); j++) {
                List<double[]> embA = studentEmbeddings.get(students.get(i));
                List<double[]> embB = studentEmbeddings.get(students.get(j));
                double sum = 0.0;
                int count = 0;
                double localMax = -2.0, localMin = 2.0;
                for (double[] a : embA) {
                    for (double[] b : embB) {
                        double sim = cosineSimilarity(a, b);
                        sum += sim;
                        count++;
                        if (i != j) { // Only cross-student pairs
                            if (sim > maxSim) {
                                maxSim = sim;
                                maxPair = students.get(i) + " vs " + students.get(j);
                            }
                            if (sim < minSim) {
                                minSim = sim;
                                minPair = students.get(i) + " vs " + students.get(j);
                            }
                        }
                        if (sim > localMax)
                            localMax = sim;
                        if (sim < localMin)
                            localMin = sim;
                    }
                }
                matrix[i][j] = count > 0 ? sum / count : 0.0;
                if (i != j) {
                    System.out.printf("Highest sim %s vs %s: %.4f\n", students.get(i), students.get(j), localMax);
                    System.out.printf("Lowest  sim %s vs %s: %.4f\n", students.get(i), students.get(j), localMin);
                }
            }
        }
        // Print matrix
        System.out.println("\nCROSS-SIMILARITY MATRIX (avg cosine similarity):");
        System.out.print("          ");
        for (String s : students)
            System.out.printf("%16s", s);
        System.out.println();
        for (int i = 0; i < students.size(); i++) {
            System.out.printf("%10s", students.get(i));
            for (int j = 0; j < students.size(); j++) {
                System.out.printf("%16.4f", matrix[i][j]);
            }
            System.out.println();
        }
        // Print global highest/lowest cross-student similarity
        System.out.println("\nGLOBAL HIGHEST CROSS-STUDENT SIMILARITY:");
        System.out.printf("%s: %.4f\n", maxPair, maxSim);
        System.out.println("GLOBAL LOWEST CROSS-STUDENT SIMILARITY:");
        System.out.printf("%s: %.4f\n", minPair, minSim);
    }
}
