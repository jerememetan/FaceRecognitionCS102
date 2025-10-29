package app.test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import ConfigurationAndLogging.AppConfig;
import ConfigurationAndLogging.AppLogger;
import app.service.FaceEmbeddingGenerator;
import facecrop.LiveRecognitionPreprocessor;
import org.opencv.core.Rect;

public class CheckEmbeddingConsistency {
    public static void main(String[] args) throws Exception {
        System.load(new File("lib/opencv_java480.dll").getAbsolutePath());

        AppConfig.getInstance();

        FaceEmbeddingGenerator generator = new FaceEmbeddingGenerator();

        Path databaseRoot = Paths.get(AppConfig.getInstance().getDatabaseStoragePath());
        if (!Files.exists(databaseRoot)) {
            throw new IllegalStateException("Face database path does not exist: " + databaseRoot);
        }

        Path[] sample;
        try (java.util.stream.Stream<Path> stream = Files.walk(databaseRoot, 2)) {
            sample = stream
                    .filter(p -> p.toString().toLowerCase().endsWith(".png"))
                    .sorted()
                    .map(png -> {
                        Path emb = Paths.get(png.toString().replaceAll("(?i)\\.png$", ".emb"));
                        return Files.exists(emb) ? new Path[] { png, emb } : null;
                    })
                    .filter(pair -> pair != null)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No matching PNG/EMB pair found in " + databaseRoot));
        }

        Path imagePath = sample[0];
        Path embedPath = sample[1];

    AppLogger.info(String.format("Using sample image: %s", imagePath));
    AppLogger.info(String.format("Using stored embedding: %s", embedPath));

        Mat image = Imgcodecs.imread(imagePath.toString());
        if (image.empty()) {
            throw new IllegalStateException("Failed to load test image");
        }

        byte[] regeneratedEmbedding = generator.generateEmbedding(image);

        byte[] storedEmbedding = Files.readAllBytes(embedPath);

        double similarity = generator.calculateSimilarity(regeneratedEmbedding, storedEmbedding);
    AppLogger.info(String.format("Stored embedding length: %d bytes", storedEmbedding.length));
    AppLogger.info(String.format("Regenerated embedding length: %d bytes",
        regeneratedEmbedding != null ? regeneratedEmbedding.length : -1));

        double regenMagnitude = calculateMagnitude(regeneratedEmbedding);
        double storedMagnitude = calculateMagnitude(storedEmbedding);
    AppLogger.info(String.format("Stored magnitude: %.6f | Regenerated magnitude: %.6f",
        storedMagnitude, regenMagnitude));

        // Also validate the live recognition preprocessing path
        LiveRecognitionPreprocessor preprocessor = new LiveRecognitionPreprocessor();
        Mat liveBlob = preprocessor.preprocessForLiveRecognition(image, new Rect(0, 0, image.width(), image.height()));
        byte[] liveEmbedding = generator.generateEmbeddingFromBlob(liveBlob);
        preprocessor.release();
        image.release();

        double liveSimilarity = generator.calculateSimilarity(liveEmbedding, storedEmbedding);
        double liveMagnitude = calculateMagnitude(liveEmbedding);
    AppLogger.info(String.format("Live magnitude: %.6f", liveMagnitude));

    AppLogger.info(String.format("Similarity between regenerated and stored embedding: %.4f", similarity));
    AppLogger.info(String.format("Similarity using live preprocessor path: %.4f", liveSimilarity));
    }

    private static double calculateMagnitude(byte[] embedding) {
        if (embedding == null) {
            return 0.0;
        }
        int length = embedding.length / 4;
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(embedding);
        double sumSq = 0.0;
        for (int i = 0; i < length; i++) {
            float v = bb.getFloat();
            sumSq += v * v;
        }
        return Math.sqrt(sumSq);
    }
}
