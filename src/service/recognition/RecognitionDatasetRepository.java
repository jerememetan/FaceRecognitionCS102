package service.recognition;

import config.AppConfig;
import config.AppLogger;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import service.embedding.FaceEmbeddingGenerator;

/**
 * Loads and models the face embedding dataset present on disk. The repository
 * is responsible for discovering folders, validating embeddings, and computing
 * per-person statistics needed for downstream recognition steps.
 */
final class RecognitionDatasetRepository {

    private static final double LIVE_THRESHOLD_RELAXATION = 0.85;
    private static final double MIN_LIVE_ABSOLUTE_THRESHOLD = 0.48;

    private final FaceEmbeddingGenerator embeddingGenerator;
    private final List<RecognitionProfile> profiles = new ArrayList<>();
    private final Object lock = new Object();

    RecognitionDatasetRepository(FaceEmbeddingGenerator embeddingGenerator) {
        this.embeddingGenerator = embeddingGenerator;
    }

    void reload() {
        String databaseRoot = AppConfig.getInstance().getDatabaseStoragePath();
        File root = new File(databaseRoot);
        File[] directories = root.listFiles(File::isDirectory);

        if (directories == null || directories.length == 0) {
            AppLogger.error("No image folders found at " + databaseRoot + "!");
            synchronized (lock) {
                profiles.clear();
            }
            return;
        }

        AppLogger.info("=== Folder debug ===");
        for (File dir : directories) {
            AppLogger.info("Found folder: " + dir.getAbsolutePath());
            File[] contents = dir.listFiles();
            if (contents != null) {
                AppLogger.info("  Contains: " + contents.length + " items");
            } else {
                AppLogger.info("  (Cannot list files)");
            }
            AppLogger.info("  Display label: " + buildDisplayLabel(dir.getName()));
        }
        AppLogger.info("====================");

        boolean deepLearning = embeddingGenerator.isDeepLearningAvailable();
        List<RecognitionProfile> refreshedProfiles = new ArrayList<>();

        for (File dir : directories) {
            String displayLabel = buildDisplayLabel(dir.getName());
            List<byte[]> embeddings = loadEmbeddings(dir);
            double[] centroid = computeCentroid(embeddings);
            double tightness = computeTightness(embeddings);
            double stdDev = computeStdDev(embeddings, centroid);

            double baseAbsolute = deepLearning ? 0.60 : 0.55;
            double baseMargin = deepLearning ? 0.10 : 0.12;

            boolean likelyHasGlasses = stdDev > 0.12;

            if (likelyHasGlasses) {
                double relaxationFactor = Math.max(0.88, 0.95 - (stdDev * 0.5));
                baseAbsolute *= relaxationFactor;
                baseMargin *= 0.85;
                AppLogger.info(String.format(
                        "  [Glasses Mode] %s variation stdDev=%.3f -> relaxed thresholds by %.1f%%",
                        displayLabel,
                        stdDev,
                        (1.0 - relaxationFactor) * 100));
            }

            double trainingAbsoluteThreshold = baseAbsolute + ((1.0 - tightness) * 0.10);
            double liveAbsoluteThreshold = Math.max(
                    MIN_LIVE_ABSOLUTE_THRESHOLD,
                    trainingAbsoluteThreshold * LIVE_THRESHOLD_RELAXATION);
            double relativeMargin = baseMargin + ((1.0 - tightness) * 0.10);

            RecognitionProfile profile = new RecognitionProfile(
                    dir.getAbsolutePath(),
                    displayLabel,
                    embeddings,
                    centroid,
                    tightness,
                    liveAbsoluteThreshold,
                    relativeMargin,
                    stdDev);

            refreshedProfiles.add(profile);

            AppLogger.info(String.format(
                    "Person %s: tightness=%.3f, stdDev=%.3f, absThresh.live=%.3f (training=%.3f), margin=%.3f",
                    profile.displayLabel(),
                    tightness,
                    stdDev,
                    liveAbsoluteThreshold,
                    trainingAbsoluteThreshold,
                    relativeMargin));
        }

        synchronized (lock) {
            profiles.clear();
            profiles.addAll(refreshedProfiles);
            debugCentroidLocked();
        }
    }

    List<RecognitionProfile> profiles() {
        synchronized (lock) {
            return List.copyOf(profiles);
        }
    }

    RecognitionProfile profileAt(int index) {
        synchronized (lock) {
            if (index < 0 || index >= profiles.size()) {
                return null;
            }
            return profiles.get(index);
        }
    }

    int size() {
        synchronized (lock) {
            return profiles.size();
        }
    }

    boolean isEmpty() {
        synchronized (lock) {
            return profiles.isEmpty();
        }
    }

    int getAdaptiveFrameSkip() {
        int numPeople;
        synchronized (lock) {
            numPeople = profiles.size();
        }
        if (numPeople <= 5) {
            return 2;
        } else if (numPeople <= 20) {
            return 3;
        }
        return 4;
    }

    private List<byte[]> loadEmbeddings(File folder) {
        List<byte[]> embeddings = new ArrayList<>();
        File[] embeddingFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".emb"));
        if (embeddingFiles == null) {
            return embeddings;
        }

        for (File file : embeddingFiles) {
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                if (bytes != null && bytes.length > 0 && embeddingGenerator.isEmbeddingValid(bytes)) {
                    embeddings.add(bytes);
                } else {
                    AppLogger.warn("Skipping invalid embedding file: " + file.getAbsolutePath());
                }
            } catch (Exception e) {
                AppLogger.warn("Failed to read embedding file: " + file.getAbsolutePath());
            }
        }
        return embeddings;
    }

    private double computeTightness(List<byte[]> embeddings) {
        if (embeddings == null || embeddings.size() < 2) {
            return 1.0;
        }

        double sum = 0.0;
        int comparisons = 0;
        for (int i = 0; i < embeddings.size(); i++) {
            for (int j = i + 1; j < embeddings.size(); j++) {
                sum += embeddingGenerator.calculateSimilarity(embeddings.get(i), embeddings.get(j));
                comparisons++;
            }
        }
        return comparisons > 0 ? sum / comparisons : 1.0;
    }

    private double computeStdDev(List<byte[]> embeddings, double[] centroid) {
        if (embeddings == null || embeddings.size() < 2 || centroid == null) {
            return 0.0;
        }

        double[] similarities = new double[embeddings.size()];
        double mean = 0.0;

        for (int i = 0; i < embeddings.size(); i++) {
            similarities[i] = RecognitionEmbeddingUtils.cosineSimilarity(embeddings.get(i), centroid);
            mean += similarities[i];
        }

        mean /= embeddings.size();

        double variance = 0.0;
        for (double similarity : similarities) {
            double diff = similarity - mean;
            variance += diff * diff;
        }

        return Math.sqrt(variance / embeddings.size());
    }

    private double[] computeCentroid(List<byte[]> embeddings) {
        if (embeddings == null || embeddings.isEmpty()) {
            return null;
        }

        double[] reference = null;
        for (byte[] embedding : embeddings) {
            reference = RecognitionEmbeddingUtils.decodeToDouble(embedding);
            if (reference != null) {
                break;
            }
        }

        if (reference == null) {
            return null;
        }

        double[] centroid = new double[reference.length];
        int valid = 0;

        for (byte[] embedding : embeddings) {
            double[] vector = RecognitionEmbeddingUtils.decodeToDouble(embedding);
            if (vector == null || vector.length != centroid.length) {
                continue;
            }
            for (int i = 0; i < vector.length; i++) {
                centroid[i] += vector[i];
            }
            valid++;
        }

        if (valid == 0) {
            return null;
        }

        for (int i = 0; i < centroid.length; i++) {
            centroid[i] /= valid;
        }

        RecognitionEmbeddingUtils.normalizeL2InPlace(centroid);
        return centroid;
    }

    private void debugCentroid() {
        synchronized (lock) {
            debugCentroidLocked();
        }
    }

    private void debugCentroidLocked() {
        if (profiles.isEmpty()) {
            return;
        }

        RecognitionProfile first = profiles.get(0);
        double[] centroid = first.centroid();
        if (centroid == null) {
            return;
        }

        AppLogger.info("DEBUG CENTROID:");
        AppLogger.info("  Centroid length: " + centroid.length);
        StringBuilder builder = new StringBuilder("  Centroid first 10 values:");
        for (int i = 0; i < Math.min(10, centroid.length); i++) {
            builder.append(" ").append(centroid[i]);
        }
        AppLogger.info(builder.toString());

        double magnitude = 0.0;
        for (double value : centroid) {
            magnitude += value * value;
        }
        magnitude = Math.sqrt(magnitude);
        AppLogger.info("  Centroid magnitude: " + magnitude);

        if (magnitude == 0 || Double.isNaN(magnitude)) {
            AppLogger.error("  ❌ CENTROID IS CORRUPTED!");
        } else if (Math.abs(magnitude - 1.0) > 0.1) {
            AppLogger.warn("  ⚠️ CENTROID NOT UNIT NORM! Magnitude: " + magnitude);
        }
    }

    static String buildDisplayLabel(String folderName) {
        if (folderName == null || folderName.isEmpty()) {
            return "unknown";
        }

        String[] parts = folderName.split("_", 2);
        if (parts.length == 2) {
            String studentId = parts[0].trim();
            String studentName = parts[1].trim();
            if (!studentId.isEmpty() && !studentName.isEmpty()) {
                return studentId + " - " + studentName;
            }
        }

        return folderName;
    }
}







