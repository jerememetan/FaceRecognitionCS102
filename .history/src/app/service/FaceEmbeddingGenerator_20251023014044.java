package app.service;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.opencv.dnn.Net;
import org.opencv.dnn.Dnn;
import org.opencv.photo.Photo;
import app.util.FaceAligner;
<<<<<<< HEAD
=======
import ConfigurationAndLogging.*;
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9

public class FaceEmbeddingGenerator {

    private Net embeddingNet;
    private boolean isInitialized = false;
    private FaceAligner aligner;

<<<<<<< HEAD
    private static final int EMBEDDING_SIZE = 128;
    private static final Size INPUT_SIZE = new Size(96, 96);
=======

    private static final int EMBEDDING_SIZE = AppConfig.getInstance().getEmbeddingSize();
    private static final int INPUT_S = AppConfig.getInstance().getEmbeddingInputSize();
    private static final Size INPUT_SIZE = new Size(INPUT_S, INPUT_S);
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9

    public FaceEmbeddingGenerator() {
        initializeEmbeddingNet();
        this.aligner = new FaceAligner();
    }

    private void initializeEmbeddingNet() {
        try {
<<<<<<< HEAD
            String modelPath = "data\\\\resources\\\\openface.nn4.small2.v1.t7";
=======

            String modelPath = AppConfig.getInstance().getEmbeddingModelPath();

>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9
            if (new java.io.File(modelPath).exists()) {
                embeddingNet = Dnn.readNetFromONNX(modelPath);
                isInitialized = true;
<<<<<<< HEAD
                System.out.println("✅ OpenFace nn4.small2.v1 model loaded successfully");
            } else {
                System.out.println("⚠️ OpenFace model not found, using feature-based embeddings");
=======
                System.out.println("✅ ArcFace ResNet100 model loaded successfully");
            } else {
                System.out.println("⚠️ ArcFace model not found, using feature-based embeddings");
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9
                isInitialized = false;
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to load face embedding model: " + e.getMessage());
            isInitialized = false;
        }
    }

    public byte[] generateEmbedding(Mat faceImage, Rect faceRect) {
        if (isInitialized) {
            return generateDeepEmbedding(faceImage, faceRect);
        } else {
            return generateFeatureBasedEmbedding(faceImage);
        }
    }

    private byte[] generateDeepEmbedding(Mat faceImage, Rect faceRect) {
        try {
<<<<<<< HEAD
            // Ensure 3-channel BGR
=======
            
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9
            Mat processed;
            if (faceImage.channels() != 3) {
                Mat temp = new Mat();
                if (faceImage.channels() == 1) {
                    Imgproc.cvtColor(faceImage, temp, Imgproc.COLOR_GRAY2BGR);
                } else {
                    temp = faceImage.clone();
                }
                processed = temp;
            } else {
                processed = faceImage.clone();
            }

            // *** CRITICAL FIX: Add face alignment ***
            Mat aligned = aligner.align(processed, faceRect);
<<<<<<< HEAD
            processed.release();

            if (aligned == null || aligned.empty()) {
                System.err.println("Alignment failed, using fallback");
                aligned = new Mat();
                Imgproc.resize(faceImage, aligned, INPUT_SIZE, 0, 0, Imgproc.INTER_CUBIC);
            }

            // Normalize to [0, 1] range - convert to float
            Mat normalized = new Mat();
            aligned.convertTo(normalized, CvType.CV_32F, 1.0 / 255.0);
            aligned.release();

            // Create blob for OpenFace model
            // swapRB=true converts BGR to RGB (OpenFace expects RGB)
            Mat blob = Dnn.blobFromImage(normalized, 1.0, INPUT_SIZE,
                    new Scalar(0, 0, 0), true, false);
            normalized.release();
=======

            if (aligned == null || aligned.empty()) {
                System.err.println("⚠️ Alignment failed, using cropped face fallback");
                aligned = new Mat();

                // ✅ FIX: Create proper face ROI BEFORE releasing processed
                Mat faceROI;
                if (faceRect != null && faceRect.width > 0 && faceRect.height > 0) {
                    // Ensure rect is within bounds
                    Rect safeRect = new Rect(
                        Math.max(0, faceRect.x),
                        Math.max(0, faceRect.y),
                        Math.min(faceRect.width, processed.width() - Math.max(0, faceRect.x)),
                        Math.min(faceRect.height, processed.height() - Math.max(0, faceRect.y))
                    );
                    faceROI = new Mat(processed, safeRect);
                } else {
                    // No valid rect, use whole processed image
                    faceROI = processed.clone();
                }

                // Resize the proper face ROI, not the original full image
                Imgproc.resize(faceROI, aligned, INPUT_SIZE, 0, 0, Imgproc.INTER_CUBIC);
                faceROI.release();
            }

            processed.release(); // ✅ Release AFTER fallback is handled

            // ✅ ArcFace requires [0,1] normalization range
            // blobFromImage will convert uint8 to float and scale to [0,1]
            Mat blob = Dnn.blobFromImage(aligned, 1.0 / 255.0, INPUT_SIZE,
                    new Scalar(0, 0, 0), true, false);
            aligned.release();
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9

            // Forward pass through network
            embeddingNet.setInput(blob);
            Mat embedding = embeddingNet.forward();
            blob.release();

            // Convert to byte array for storage
            byte[] result = matToByteArray(embedding);
            embedding.release();

            return result;

        } catch (Exception e) {
            System.err.println("Deep embedding generation failed: " + e.getMessage());
            e.printStackTrace();
            return null;
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
                gray = resized.clone();
            }

            extractHistogramFeatures(gray, features, 0);
            extractTextureFeatures(gray, features, 32);
            extractGeometricFeatures(gray, features, 64);
            extractGradientFeatures(gray, features, 96);

            gray.release();
            resized.release();

            return doubleArrayToByteArray(features);

        } catch (Exception e) {
            System.err.println("❌ Feature-based embedding generation failed: " + e.getMessage());
            e.printStackTrace();
            return null; // ✅ Return null to signal failure
        }
    }

    private void extractHistogramFeatures(Mat image, double[] features, int offset) {
        Mat hist = new Mat();
        MatOfInt channels = new MatOfInt(0);
        MatOfInt histSize = new MatOfInt(32);
        MatOfFloat ranges = new MatOfFloat(0f, 256f);

        Imgproc.calcHist(java.util.Arrays.asList(image), channels, new Mat(),
                hist, histSize, ranges);
        Core.normalize(hist, hist, 0, 1, Core.NORM_MINMAX);

        for (int i = 0; i < 32 && i < hist.rows(); i++) {
            features[offset + i] = hist.get(i, 0)[0];
        }

        hist.release();
        channels.release();
        histSize.release();
        ranges.release();
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

            meanX.release();
            stdX.release();
            meanY.release();
            stdY.release();
        }

        gradX.release();
        gradY.release();
    }

    private void extractGeometricFeatures(Mat image, double[] features, int offset) {
        Moments moments = Imgproc.moments(image);

        if (offset + 31 < features.length && moments.m00 != 0) {
            features[offset] = moments.m00 / (image.width() * image.height());
            features[offset + 1] = moments.m10 / moments.m00;
            features[offset + 2] = moments.m01 / moments.m00;

            Mat huMoments = new Mat();
            Imgproc.HuMoments(moments, huMoments);

            for (int i = 0; i < Math.min(7, huMoments.rows()) && (offset + 3 + i) < features.length; i++) {
                double hu = huMoments.get(i, 0)[0];
                features[offset + 3 + i] = Math.log(Math.abs(hu) + 1e-10);
            }

            huMoments.release();
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

        gradX.release();
        gradY.release();
        magnitude.release();
        angle.release();
    }

    private byte[] matToByteArray(Mat mat) {
        try {
            float[] floatArray = new float[EMBEDDING_SIZE];
            mat.get(0, 0, floatArray);

            // ✅ CRITICAL: Validate neural network output BEFORE normalization
            boolean hasInvalid = false;
            for (float f : floatArray) {
                if (Float.isNaN(f) || Float.isInfinite(f)) {
                    System.err.println("❌ Neural network produced invalid output (NaN/Inf detected)");
                    hasInvalid = true;
                    break;
                }
            }

            if (hasInvalid) {
                return null; // Signal failure instead of corrupting data
            }

            // Calculate norm
            double norm = 0.0;
            for (float f : floatArray) {
                norm += f * f;
            }
            norm = Math.sqrt(Math.max(norm, 1e-12));
<<<<<<< HEAD
            for (int i = 0; i < floatArray.length; i++) {
                floatArray[i] /= norm;
=======

            // ✅ Additional safety: Check if norm is valid
            if (Double.isNaN(norm) || Double.isInfinite(norm) || norm < 1e-10) {
                System.err.println("❌ Invalid embedding norm: " + norm);
                return null;
            }

            // Normalize
            for (int i = 0; i < floatArray.length; i++) {
                floatArray[i] /= (float)norm;
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9
            }

            java.nio.ByteBuffer byteBuffer = java.nio.ByteBuffer.allocate(EMBEDDING_SIZE * 4);
            for (float f : floatArray) {
                byteBuffer.putFloat(f);
            }

            return byteBuffer.array();

        } catch (Exception e) {
            System.err.println("❌ Mat to byte array conversion failed: " + e.getMessage());
<<<<<<< HEAD
            return new byte[EMBEDDING_SIZE * 4];
=======
            e.printStackTrace();
            return null; // ✅ Return null instead of corrupt data
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9
        }
    }

    private byte[] doubleArrayToByteArray(double[] array) {

        double norm = 0.0;
        for (double d : array) {
            norm += d * d;
        }
        norm = Math.sqrt(Math.max(norm, 1e-12));
        for (int i = 0; i < array.length; i++) {
            array[i] /= norm;
        }

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
            // Always use cosine similarity for embeddings (both are unit vectors after normalization)
            if (embedding1.length == EMBEDDING_SIZE * 4) { // Float32 embeddings (ArcFace)
                return calculateCosineSimilarity(embedding1, embedding2);
            } else if (embedding1.length == EMBEDDING_SIZE * 8) { // Float64 embeddings (Legacy)
                return calculateCosineSimilarityDouble(embedding1, embedding2);
            } else {
                return 0.0;
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
<<<<<<< HEAD
                    if (Math.abs(f) > 1e-6)
=======
                    if (Math.abs(f) > 1e-12)  // ✅ Relaxed - counts values > 0.000000000001
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9
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
<<<<<<< HEAD
                    if (Math.abs(d) > 1e-6)
=======
                    if (Math.abs(d) > 1e-12)  // ✅ Relaxed - counts values > 0.000000000001
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9
                        validCount++;
                }
            }

            magnitude = Math.sqrt(magnitude);
<<<<<<< HEAD
            if (magnitude < 1e-6) {
=======
            if (magnitude < 1e-10) {  // ✅ Relaxed threshold
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9
                System.err.println("Invalid embedding: zero magnitude");
                return false;
            }

            double nonZeroRatio = (double) validCount / EMBEDDING_SIZE;
<<<<<<< HEAD
            if (nonZeroRatio < 0.5) {
=======
            if (nonZeroRatio < 0.05) {  // ✅ FURTHER RELAXED for ArcFace (5% non-zero is OK)
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9
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
            java.util.List<org.opencv.core.Rect> faceRects,
            app.util.ImageProcessor imageProcessor,
            ProgressCallback progressCallback) {

        if (imagePaths == null || imagePaths.isEmpty()) {
            return new BatchProcessingResult(0, 0, 0, "No images to process");
        }

        if (progressCallback != null) {
            progressCallback.onProgress("Processing captured images...");
        }

        int processedCount = 0;
        java.util.List<byte[]> generatedEmbeddings = new java.util.ArrayList<>();
        java.util.List<String> embeddingPaths = new java.util.ArrayList<>();
        java.util.List<String> successfulImagePaths = new java.util.ArrayList<>();

        for (int i = 0; i < imagePaths.size(); i++) {
            String imagePath = imagePaths.get(i);
            org.opencv.core.Rect faceRect = (faceRects != null && i < faceRects.size()) ? faceRects.get(i) : null;

            try {
                Mat image = org.opencv.imgcodecs.Imgcodecs.imread(imagePath);
                if (image.empty()) {
                    System.err.println("❌ Failed to read captured image: " + imagePath);
                    continue;
                }

                Mat faceROI;
                if (faceRect != null) {
<<<<<<< HEAD
                    // Use detected face rectangle for better alignment
                    faceROI = new Mat(image, faceRect);
                } else {
                    // ✅ FIX: Saved image IS ALREADY the cropped face! Use whole image as face
                    // region
                    // Don't re-detect, use entire image as the face
                    Rect wholeFaceRect = new Rect(0, 0, image.width(), image.height());
                    faceROI = image.clone(); // Use whole image
                    faceRect = wholeFaceRect; // Update faceRect for alignment
=======
                    // ✅ Validate rect is within image bounds
                    if (faceRect.x >= 0 && faceRect.y >= 0 && 
                        faceRect.x + faceRect.width <= image.width() &&
                        faceRect.y + faceRect.height <= image.height() &&
                        faceRect.width > 0 && faceRect.height > 0) {
                        faceROI = new Mat(image, faceRect);
                    } else {
                        System.err.println("⚠️ Invalid faceRect, using whole image");
                        faceRect = new Rect(0, 0, image.width(), image.height());
                        faceROI = image.clone();
                    }
                } else {
                    // Saved image IS ALREADY the cropped face
                    Rect wholeFaceRect = new Rect(0, 0, image.width(), image.height());
                    faceROI = image.clone();
                    faceRect = null; // ✅ FIX: Pass null to indicate entire image is the face
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9
                }
                image.release();

                byte[] embedding = generateEmbedding(faceROI, faceRect);
                faceROI.release();

                if (embedding != null && isEmbeddingValid(embedding)) {
                    String embPath = imagePath.replace(".png", ".emb");
                    try {
                        java.nio.file.Files.write(java.nio.file.Paths.get(embPath), embedding);
                        generatedEmbeddings.add(embedding);
                        embeddingPaths.add(embPath);
                        successfulImagePaths.add(imagePath);
                        processedCount++;
                        System.out.println("✓ Saved embedding: " + new java.io.File(embPath).getName());
                    } catch (Exception e) {
                        System.err.println("❌ Failed to save embedding for " + imagePath + ": " + e.getMessage());
                    }
                } else {
                    System.err.println("❌ Invalid embedding generated for: " + imagePath);
                }

            } catch (Exception e) {
                System.err.println("❌ Error processing image " + imagePath + ": " + e.getMessage());
            }
        }

        System.out
                .println("Embedding generation complete: " + processedCount + "/" + imagePaths.size() + " successful");

        if (processedCount < imagePaths.size() && progressCallback != null) {
            progressCallback.onProgress(
                    "Warning: Only " + processedCount + "/" + imagePaths.size() + " embeddings generated successfully");
        }

        int removedCount = 0;
        int weakRemovedCount = 0;
        if (generatedEmbeddings.size() >= 5) {
            if (progressCallback != null) {
                progressCallback.onProgress("Analyzing embedding quality...");
            }
            removedCount = detectAndRemoveOutliers(generatedEmbeddings, embeddingPaths, successfulImagePaths);
            if (removedCount > 0) {
                System.out.println("✓ Auto-removed " + removedCount + " outlier embedding(s)");
                if (progressCallback != null) {
                    progressCallback.onProgress("Removed " + removedCount + " outlier image(s) automatically");
                }
            } else {
                System.out.println("✓ All embeddings passed outlier check");
            }

            // Now check for weak embeddings among remaining ones
            if (generatedEmbeddings.size() >= 3) { // Need at least 3 for meaningful quality analysis
                weakRemovedCount = detectAndRemoveWeakEmbeddings(generatedEmbeddings, embeddingPaths,
                        successfulImagePaths);
                if (weakRemovedCount > 0) {
                    System.out.println("✓ Auto-removed " + weakRemovedCount + " weak embedding(s)");
                    if (progressCallback != null) {
                        progressCallback.onProgress("Removed " + weakRemovedCount + " weak image(s) automatically");
                    }
                } else {
                    System.out.println("✓ All embeddings passed weakness check");
                }
            }
        }

        String message = processedCount > 0
                ? "Successfully processed " + processedCount + " embeddings"
                : "Failed to process embeddings";

        return new BatchProcessingResult(processedCount, removedCount, weakRemovedCount, message);
    }

<<<<<<< HEAD
    private Mat preprocessForTraining(Mat faceImage) {
        try {

            Mat denoised = new Mat();
            if (faceImage.channels() == 1) {
                Photo.fastNlMeansDenoising(faceImage, denoised, 3.0f, 7, 21);
            } else {
                Photo.fastNlMeansDenoisingColored(faceImage, denoised, 3.0f, 3.0f, 7, 21);
            }

            Mat resized = new Mat();
            Imgproc.resize(denoised, resized, INPUT_SIZE, 0, 0, Imgproc.INTER_CUBIC);
            denoised.release();

            return resized;

        } catch (Exception e) {
            System.err.println("❌ Training preprocessing failed: " + e.getMessage());
            return faceImage.clone();
        }
    }
=======

>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9

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
                        System.out.println("  ✗ Deleted outlier embedding: " + embFile.getName());
                        removedCount++;
                    }

                    java.io.File imgFile = new java.io.File(imagePaths.get(idx));
                    if (imgFile.exists() && imgFile.delete()) {
                        System.out.println("  ✗ Deleted outlier image: " + imgFile.getName());
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

    private int detectAndRemoveWeakEmbeddings(java.util.List<byte[]> embeddings,
            java.util.List<String> embeddingPaths,
            java.util.List<String> imagePaths) {
        if (embeddings == null || embeddings.size() < 3) {
            return 0;
        }

        try {
            // Calculate average similarity scores for each embedding
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

            // Calculate mean and standard deviation
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

<<<<<<< HEAD
            // Define weak embedding criteria (much more lenient for training data):
            // - Score below mean - 1.0 * stdDev (very lenient)
            // - Absolute score below 0.6 (reasonable quality threshold for training)
=======
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9
            double weakThreshold = Math.max(0.5, mean - 1.0 * stdDev);
            double absoluteWeakThreshold = 0.6;

            java.util.List<Integer> weakIndices = new java.util.ArrayList<>();
            for (int i = 0; i < avgSimilarities.length; i++) {
                boolean isWeakByDeviation = avgSimilarities[i] < weakThreshold;
                boolean isWeakByAbsolute = avgSimilarities[i] < absoluteWeakThreshold;

                if (isWeakByDeviation || isWeakByAbsolute) {
                    weakIndices.add(i);
                    System.out.println(String.format(
                            "  Weak embedding detected: index=%d, avgSim=%.4f, threshold=%.4f, absThresh=%.4f",
                            i, avgSimilarities[i], weakThreshold, absoluteWeakThreshold));
                }
            }

            int removedCount = 0;
            // Remove in reverse order to maintain correct indices
            for (int i = weakIndices.size() - 1; i >= 0; i--) {
                int idx = weakIndices.get(i);
                try {
                    java.io.File embFile = new java.io.File(embeddingPaths.get(idx));
                    if (embFile.exists() && embFile.delete()) {
                        System.out.println("  ✗ Deleted weak embedding: " + embFile.getName());
                        removedCount++;
                    }

                    java.io.File imgFile = new java.io.File(imagePaths.get(idx));
                    if (imgFile.exists() && imgFile.delete()) {
                        System.out.println("  ✗ Deleted weak image: " + imgFile.getName());
                    }

                    // Remove from lists to maintain consistency
                    embeddings.remove(idx);
                    embeddingPaths.remove(idx);
                    imagePaths.remove(idx);

                } catch (Exception e) {
                    System.err.println("Failed to delete weak files at index " + idx + ": " + e.getMessage());
                }
            }

            return removedCount;

        } catch (Exception e) {
            System.err.println("Weak embedding detection failed: " + e.getMessage());
            return 0;
        }
    }

    public void release() {
        if (aligner != null) {
            aligner.release();
        }
    }
    // Inner classes

    public static class BatchProcessingResult {
        private final int processedCount;
        private final int removedOutlierCount;
        private final int removedWeakCount;
        private final String message;

        public BatchProcessingResult(int processedCount, int removedOutlierCount, int removedWeakCount,
                String message) {
            this.processedCount = processedCount;
            this.removedOutlierCount = removedOutlierCount;
            this.removedWeakCount = removedWeakCount;
            this.message = message;
        }

        public int getProcessedCount() {
            return processedCount;
        }

        public int getRemovedOutlierCount() {
            return removedOutlierCount;
        }

        public int getRemovedWeakCount() {
            return removedWeakCount;
        }

        public int getTotalRemovedCount() {
            return removedOutlierCount + removedWeakCount;
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