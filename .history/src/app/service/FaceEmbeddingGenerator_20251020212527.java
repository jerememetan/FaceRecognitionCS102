package app.service;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.opencv.dnn.Net;
import org.opencv.dnn.Dnn;

public class FaceEmbeddingGenerator {
    private Net embeddingNet;
    private boolean isInitialized = false;

    private static final int EMBEDDING_SIZE = 128;
    private static final Size INPUT_SIZE = new Size(96, 96);

    public FaceEmbeddingGenerator() {
        initializeEmbeddingNet();
    }

    private void initializeEmbeddingNet() {
        try {

            String modelPath = "data\\resources\\openface.nn4.small2.v1.t7";

            if (new java.io.File(modelPath).exists()) {
                embeddingNet = Dnn.readNetFromTorch(modelPath);
                isInitialized = true;
                System.out.println("Face embedding model loaded successfully");
            } else {
                System.out.println("Face embedding model not found, using feature-based embeddings");
                isInitialized = false;
            }
        } catch (Exception e) {
            System.err.println("Failed to load face embedding model: " + e.getMessage());
            isInitialized = false;
        }
    }

    public byte[] generateEmbedding(Mat faceImage) {
        if (isInitialized) {
            return generateDeepEmbedding(faceImage);
        } else {
            return generateFeatureBasedEmbedding(faceImage);
        }
    }

    private byte[] generateDeepEmbedding(Mat faceImage) {
        try {

            Mat colorImage = new Mat();
            if (faceImage.channels() == 1) {
                Imgproc.cvtColor(faceImage, colorImage, Imgproc.COLOR_GRAY2BGR);
            } else {
                colorImage = faceImage.clone();
            }

            Mat processedImage = new Mat();
            if (colorImage.width() != 96 || colorImage.height() != 96) {
                Imgproc.resize(colorImage, processedImage, INPUT_SIZE, 0, 0, Imgproc.INTER_CUBIC);
            } else {
                processedImage = colorImage.clone();
            }

            processedImage.convertTo(processedImage, CvType.CV_32F, 1.0 / 255.0);

            Mat blob = Dnn.blobFromImage(processedImage, 1.0, INPUT_SIZE, new Scalar(0, 0, 0), true, false);

            embeddingNet.setInput(blob);

            Mat embedding = embeddingNet.forward();

            colorImage.release();
            processedImage.release();

            return matToByteArray(embedding);

        } catch (Exception e) {
            System.err.println("Deep embedding generation failed: " + e.getMessage());
            return generateFeatureBasedEmbedding(faceImage);
        }
    }

    private byte[] generateFeatureBasedEmbedding(Mat faceImage) {
        try {

            double[] features = new double[EMBEDDING_SIZE];
            Mat resized = new Mat();
            Imgproc.resize(faceImage, resized, new Size(64, 64));

            Mat gray = new Mat();
            if (resized.channels() > 1) {
                Imgproc.cvtColor(resized, gray, Imgproc.COLOR_BGR2GRAY);
            } else {
                gray = resized;
            }

            extractHistogramFeatures(gray, features, 0);
            extractTextureFeatures(gray, features, 32);
            extractGeometricFeatures(gray, features, 64);
            extractGradientFeatures(gray, features, 96);

            return doubleArrayToByteArray(features);

        } catch (Exception e) {
            System.err.println("Feature-based embedding generation failed: " + e.getMessage());
            return new byte[EMBEDDING_SIZE * 8];
        }
    }

    private void extractHistogramFeatures(Mat image, double[] features, int offset) {

        Mat hist = new Mat();
        MatOfInt channels = new MatOfInt(0);
        MatOfInt histSize = new MatOfInt(32);
        MatOfFloat ranges = new MatOfFloat(0f, 256f);

        Imgproc.calcHist(java.util.Arrays.asList(image), channels, new Mat(), hist, histSize, ranges);

        Core.normalize(hist, hist, 0, 1, Core.NORM_MINMAX);
        for (int i = 0; i < 32 && i < hist.rows(); i++) {
            features[offset + i] = hist.get(i, 0)[0];
        }
    }

    private void extractTextureFeatures(Mat image, double[] features, int offset) {

        Mat gradX = new Mat(), gradY = new Mat();
        Imgproc.Sobel(image, gradX, CvType.CV_64F, 1, 0, 3);
        Imgproc.Sobel(image, gradY, CvType.CV_64F, 0, 1, 3);

        int regions = 4;
        int regionWidth = image.width() / 2;
        int regionHeight = image.height() / 2;

        for (int i = 0; i < regions && (offset + i * 8) < features.length; i++) {
            int x = (i % 2) * regionWidth;
            int y = (i / 2) * regionHeight;

            Rect roi = new Rect(x, y, regionWidth, regionHeight);
            Mat regionGradX = new Mat(gradX, roi);
            Mat regionGradY = new Mat(gradY, roi);

            MatOfDouble meanX = new MatOfDouble();
            MatOfDouble stdX = new MatOfDouble();
            Core.meanStdDev(regionGradX, meanX, stdX);

            MatOfDouble meanY = new MatOfDouble();
            MatOfDouble stdY = new MatOfDouble();
            Core.meanStdDev(regionGradY, meanY, stdY);

            int baseIdx = offset + i * 8;
            if (baseIdx + 3 < features.length) {
                features[baseIdx] = meanX.toArray()[0] / 255.0;
                features[baseIdx + 1] = stdX.toArray()[0] / 255.0;
                features[baseIdx + 2] = meanY.toArray()[0] / 255.0;
                features[baseIdx + 3] = stdY.toArray()[0] / 255.0;
            }
        }
    }

    private void extractGeometricFeatures(Mat image, double[] features, int offset) {

        Moments moments = Imgproc.moments(image);

        if (offset + 31 < features.length) {
            features[offset] = moments.m00 / (image.width() * image.height()); // Area ratio
            features[offset + 1] = moments.m10 / moments.m00;
            features[offset + 2] = moments.m01 / moments.m00;

            Mat huMoments = new Mat();
            Imgproc.HuMoments(moments, huMoments);

            for (int i = 0; i < Math.min(7, huMoments.rows()) && (offset + 3 + i) < features.length; i++) {
                double hu = huMoments.get(i, 0)[0];
                features[offset + 3 + i] = Math.log(Math.abs(hu) + 1e-10);
            }
        }
    }

    private void extractGradientFeatures(Mat image, double[] features, int offset) {
        Mat gradX = new Mat(), gradY = new Mat();
        Imgproc.Sobel(image, gradX, CvType.CV_64F, 1, 0, 3);
        Imgproc.Sobel(image, gradY, CvType.CV_64F, 0, 1, 3);

        Mat magnitude = new Mat(), angle = new Mat();
        Core.cartToPolar(gradX, gradY, magnitude, angle, true);

        int numBins = 16;
        double[] hogFeatures = new double[numBins];

        for (int y = 0; y < angle.rows(); y++) {
            for (int x = 0; x < angle.cols(); x++) {
                double ang = angle.get(y, x)[0];
                double mag = magnitude.get(y, x)[0];

                int bin = (int) (ang / (360.0 / numBins)) % numBins;
                hogFeatures[bin] += mag;
            }
        }

        double sum = java.util.Arrays.stream(hogFeatures).sum();
        if (sum > 0) {
            for (int i = 0; i < numBins && (offset + i) < features.length; i++) {
                features[offset + i] = hogFeatures[i] / sum;
            }
        }
    }

    private byte[] matToByteArray(Mat mat) {
        try {
            float[] floatArray = new float[EMBEDDING_SIZE];
            mat.get(0, 0, floatArray);

            java.nio.ByteBuffer byteBuffer = java.nio.ByteBuffer.allocate(EMBEDDING_SIZE * 4);
            for (float f : floatArray) {
                byteBuffer.putFloat(f);
            }
            return byteBuffer.array();
        } catch (Exception e) {
            return new byte[EMBEDDING_SIZE * 4];
        }
    }

    private byte[] doubleArrayToByteArray(double[] array) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(array.length * 8);
        for (double d : array) {
            buffer.putDouble(d);
        }
        return buffer.array();
    }

    public double calculateSimilarity(byte[] embedding1, byte[] embedding2) {
        if (embedding1 == null || embedding2 == null ||
                embedding1.length != embedding2.length) {
            return 0.0;
        }

        try {
            if (isInitialized) {
                return calculateCosineSimilarity(embedding1, embedding2);
            } else {
                return calculateEuclideanSimilarity(embedding1, embedding2);
            }
        } catch (Exception e) {
            System.err.println("Similarity calculation failed: " + e.getMessage());
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

    private double calculateEuclideanSimilarity(byte[] emb1, byte[] emb2) {
        double[] vec1 = byteArrayToDoubleArray(emb1);
        double[] vec2 = byteArrayToDoubleArray(emb2);

        double sumSquaredDiff = 0.0;
        for (int i = 0; i < vec1.length; i++) {
            double diff = vec1[i] - vec2[i];
            sumSquaredDiff += diff * diff;
        }

        double distance = Math.sqrt(sumSquaredDiff);
        return 1.0 / (1.0 + distance);
    }

    private float[] byteArrayToFloatArray(byte[] bytes) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) {
            floats[i] = buffer.getFloat();
        }
        return floats;
    }

    private double[] byteArrayToDoubleArray(byte[] bytes) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
        double[] doubles = new double[bytes.length / 8];
        for (int i = 0; i < doubles.length; i++) {
            doubles[i] = buffer.getDouble();
        }
        return doubles;
    }

    public boolean isDeepLearningAvailable() {
        return isInitialized;
    }

    public boolean isEmbeddingValid(byte[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return false;
        }

        int expectedSize = isInitialized ? (EMBEDDING_SIZE * 4) : (EMBEDDING_SIZE * 8);
        if (embedding.length != expectedSize) {
            System.err.println("Invalid embedding size: " + embedding.length + ", expected: " + expectedSize);
            return false;
        }

        try {

            double magnitude = 0.0;
            int validCount = 0;

            if (isInitialized) {

                float[] floats = byteArrayToFloatArray(embedding);
                for (float f : floats) {
                    if (Float.isNaN(f) || Float.isInfinite(f)) {
                        System.err.println("Invalid embedding: contains NaN or Inf");
                        return false;
                    }
                    magnitude += f * f;
                    if (Math.abs(f) > 1e-6)
                        validCount++;
                }
            } else {

                double[] doubles = byteArrayToDoubleArray(embedding);
                for (double d : doubles) {
                    if (Double.isNaN(d) || Double.isInfinite(d)) {
                        System.err.println("Invalid embedding: contains NaN or Inf");
                        return false;
                    }
                    magnitude += d * d;
                    if (Math.abs(d) > 1e-6)
                        validCount++;
                }
            }

            magnitude = Math.sqrt(magnitude);
            if (magnitude < 1e-6) {
                System.err.println("Invalid embedding: zero magnitude");
                return false;
            }

            double nonZeroRatio = (double) validCount / EMBEDDING_SIZE;
            if (nonZeroRatio < 0.5) {
                System.err.println("Invalid embedding: too sparse (" +
                        String.format("%.1f%%", nonZeroRatio * 100) + " non-zero)");
                return false;
            }

            return true;

        } catch (Exception e) {
            System.err.println("Embedding validation failed: " + e.getMessage());
            return false;
        }
    }

    public BatchProcessingResult processCapturedImages(
            java.util.List<String> imagePaths,
            app.util.ImageProcessor imageProcessor,
            ProgressCallback progressCallback) {

        if (imagePaths == null || imagePaths.isEmpty()) {
            return new BatchProcessingResult(0, 0, "No images to process");
        }

        if (progressCallback != null) {
            progressCallback.onProgress("Processing captured images...");
        }

        int processedCount = 0;
        java.util.List<byte[]> generatedEmbeddings = new java.util.ArrayList<>();
        java.util.List<String> embeddingPaths = new java.util.ArrayList<>();

        for (String imagePath : imagePaths) {
            try {
                Mat image = org.opencv.imgcodecs.Imgcodecs.imread(imagePath);
                if (image.empty()) {
                    System.err.println("Failed to read captured image: " + imagePath);
                    continue;
                }

                Mat processedFace = preprocessFaceForRecognition(image, imageProcessor);
                image.release();

                byte[] embedding = generateEmbedding(processedFace);
                processedFace.release();

                if (embedding != null && isEmbeddingValid(embedding)) {
                    String embPath = imagePath.replace(".png", ".emb");
                    try {
                        java.nio.file.Files.write(java.nio.file.Paths.get(embPath), embedding);
                        generatedEmbeddings.add(embedding);
                        embeddingPaths.add(embPath);
                        processedCount++;
                        System.out.println("âœ“ Saved embedding: " + new java.io.File(embPath).getName());
                    } catch (Exception e) {
                        System.err.println("Failed to save embedding for " + imagePath + ": " + e.getMessage());
                    }
                } else {
                    System.err.println("Invalid embedding generated for: " + imagePath);
                }
            } catch (Exception e) {
                System.err.println("Error processing image " + imagePath + ": " + e.getMessage());
            }
        }

        System.out
                .println("Embedding generation complete: " + processedCount + "/" + imagePaths.size() + " successful");

        if (processedCount < imagePaths.size() && progressCallback != null) {
            progressCallback.onProgress(
                    "Warning: Only " + processedCount + "/" + imagePaths.size() + " embeddings generated successfully");
        }

        int removedCount = 0;
        if (generatedEmbeddings.size() >= 5) {
            if (progressCallback != null) {
                progressCallback.onProgress("Analyzing embedding quality...");
            }
            removedCount = detectAndRemoveOutliers(generatedEmbeddings, embeddingPaths, imagePaths);

            if (removedCount > 0) {
                System.out.println("âœ“ Auto-removed " + removedCount + " outlier embedding(s)");
                if (progressCallback != null) {
                    progressCallback.onProgress("Removed " + removedCount + " low-quality image(s) automatically");
                }
            } else {
                System.out.println("âœ“ All embeddings passed quality check - no outliers detected");
            }
        }

        String message = processedCount > 0
                ? "Successfully processed " + processedCount + " embeddings"
                : "Failed to process embeddings";

        return new BatchProcessingResult(processedCount, removedCount, message);
    }

    public Mat preprocessFaceForLiveRecognition(Mat faceROI) {
        if (faceROI == null || faceROI.empty()) {
            return new Mat();
        }

        Mat color = new Mat();
        try {
            if (faceROI.channels() == 1) {
                Imgproc.cvtColor(faceROI, color, Imgproc.COLOR_GRAY2BGR);
            } else {
                color = faceROI.clone();
            }


Mat processed = new Mat();
Imgproc.resize(color, processed, INPUT_SIZE, 0, 0, Imgproc.INTER_CUBIC);
color.release();

// Then apply denoising directly on 96x96
Mat denoised = new Mat();
if (processed.channels() == 1) {
    Photo.fastNlMeansDenoising(processed, denoised, 5.0f, 7, 21);
} else {
    Photo.fastNlMeansDenoisingColored(processed, denoised, 5.0f, 5.0f, 7, 21);
}
processed.release();
return denoised;

        } catch (Exception e) {
            System.err.println("Live recognition preprocessing failed: " + e.getMessage());
            e.printStackTrace();
            color.release();
            return faceROI.clone();
        }
    }

    private Mat preprocessFaceForRecognition(Mat faceImage, app.util.ImageProcessor imageProcessor) {

        try {

            Mat denoised = new Mat();
            if (faceImage.channels() == 1) {
                org.opencv.photo.Photo.fastNlMeansDenoising(faceImage, denoised, 3.0f, 7, 21);
            } else {
                org.opencv.photo.Photo.fastNlMeansDenoisingColored(faceImage, denoised, 3.0f, 3.0f, 7, 21);
            }

            Mat resized = new Mat();
            org.opencv.imgproc.Imgproc.resize(denoised, resized, INPUT_SIZE, 0, 0,
                    org.opencv.imgproc.Imgproc.INTER_CUBIC);

            denoised.release();
            return resized;

        } catch (Exception e) {
            System.err.println("Minimal preprocessing failed, using clone: " + e.getMessage());
            return faceImage.clone();
        }
    }

    private int detectAndRemoveOutliers(java.util.List<byte[]> embeddings,
            java.util.List<String> embeddingPaths,
            java.util.List<String> imagePaths) {

        if (embeddings == null || embeddings.size() < 5) {
            return 0;
        }

        try {

            double[] avgSimilarities = new double[embeddings.size()];

            for (int i = 0; i < embeddings.size(); i++) {
                double sum = 0;
                int count = 0;

                for (int j = 0; j < embeddings.size(); j++) {
                    if (i != j) {
                        sum += calculateSimilarity(embeddings.get(i), embeddings.get(j));
                        count++;
                    }
                }

                avgSimilarities[i] = sum / count;
            }

            double mean = 0;
            for (double sim : avgSimilarities) {
                mean += sim;
            }
            mean /= avgSimilarities.length;

            double variance = 0;
            for (double sim : avgSimilarities) {
                variance += Math.pow(sim - mean, 2);
            }
            double stdDev = Math.sqrt(variance / avgSimilarities.length);

            double outlierThreshold = Math.max(0.70, mean - 1.5 * stdDev);

            java.util.List<Integer> outlierIndices = new java.util.ArrayList<>();
            for (int i = 0; i < avgSimilarities.length; i++) {
                if (avgSimilarities[i] < outlierThreshold) {
                    outlierIndices.add(i);
                    System.out.println(String.format("  Outlier detected: index=%d, avgSim=%.4f, threshold=%.4f",
                            i, avgSimilarities[i], outlierThreshold));
                }
            }

            int removedCount = 0;
            for (int idx : outlierIndices) {
                try {

                    java.io.File embFile = new java.io.File(embeddingPaths.get(idx));
                    if (embFile.exists() && embFile.delete()) {
                        System.out.println("  âœ— Deleted outlier embedding: " + embFile.getName());
                        removedCount++;
                    }

                    java.io.File imgFile = new java.io.File(imagePaths.get(idx));
                    if (imgFile.exists() && imgFile.delete()) {
                        System.out.println("  âœ— Deleted outlier image: " + imgFile.getName());
                    }
                } catch (Exception e) {
                    System.err.println("Failed to delete outlier files at index " + idx + ": " + e.getMessage());
                }
            }

            return removedCount;

        } catch (Exception e) {
            System.err.println("Outlier detection failed: " + e.getMessage());
            return 0;
        }
    }

    public static class BatchProcessingResult {
        private final int processedCount;
        private final int removedOutlierCount;
        private final String message;

        public BatchProcessingResult(int processedCount, int removedOutlierCount, String message) {
            this.processedCount = processedCount;
            this.removedOutlierCount = removedOutlierCount;
            this.message = message;
        }

        public int getProcessedCount() {
            return processedCount;
        }

        public int getRemovedOutlierCount() {
            return removedOutlierCount;
        }

        public String getMessage() {
            return message;
        }

        public boolean isSuccess() {
            return processedCount > 0;
        }
    }

    public interface ProgressCallback {
        void onProgress(String message);
    }
}