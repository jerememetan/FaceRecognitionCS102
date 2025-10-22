package app.test;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import app.service.FaceEmbeddingGenerator;
import app.util.ImageProcessor;

/**
 * Compare embeddings from the same image loaded from disk (training path) vs
 * preprocessed directly (live path)
 */
public class LiveVsTrainingComparison {
    public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        // Path to a training image
        String imagePath = "data\\facedata\\S13234_Jin_Rae\\S13234_008.jpg";

        FaceEmbeddingGenerator embGen = new FaceEmbeddingGenerator();
        ImageProcessor imgProc = new ImageProcessor();

        // Load image from disk (400x400 JPG)
        Mat trainingImage = Imgcodecs.imread(imagePath);
        if (trainingImage.empty()) {
            System.err.println("Failed to load image: " + imagePath);
            return;
        }

        System.out.println("Image loaded: " + trainingImage.size() + " channels=" + trainingImage.channels());
        System.out.println("Image type: " + trainingImage.type() + " (BGR=16, BGRA=24)");

        // Method 1: Training pipeline (load 400x400 → preprocess)
        // This simulates how training embeddings were generated
        Mat trainPreprocessed = preprocessForTraining(trainingImage.clone(), imgProc, embGen);
        byte[] trainEmbedding = embGen.generateEmbedding(trainPreprocessed);
        trainPreprocessed.release();

        // Method 2: Live recognition pipeline (simulate webcam → upscale → preprocess)
        // This simulates how live recognition processes frames
        // First downscale to simulate webcam ROI size (~150-200px)
        Mat simulatedWebcamROI = new Mat();
        org.opencv.imgproc.Imgproc.resize(trainingImage, simulatedWebcamROI, new Size(160, 160),
                0, 0, org.opencv.imgproc.Imgproc.INTER_AREA);

        Mat livePreprocessed = embGen.preprocessFaceForLiveRecognition(simulatedWebcamROI);
        byte[] liveEmbedding = embGen.generateEmbedding(livePreprocessed);
        livePreprocessed.release();
        simulatedWebcamROI.release();

        // Method 3: Direct preprocessing (no intermediate steps)
        Mat directPreprocessed = embGen.preprocessFaceForLiveRecognition(trainingImage.clone());
        byte[] directEmbedding = embGen.generateEmbedding(directPreprocessed);
        directPreprocessed.release();

        trainingImage.release();

        // Compare embeddings
        double trainVsLive = embGen.calculateSimilarity(trainEmbedding, liveEmbedding);
        double trainVsDirect = embGen.calculateSimilarity(trainEmbedding, directEmbedding);
        double liveVsDirect = embGen.calculateSimilarity(liveEmbedding, directEmbedding);

        System.out.println("\n=== Embedding Comparison ===");
        System.out.println("Training method   vs Live method:   " + String.format("%.4f", trainVsLive));
        System.out.println("Training method   vs Direct method: " + String.format("%.4f", trainVsDirect));
        System.out.println("Live method       vs Direct method: " + String.format("%.4f", liveVsDirect));

        System.out.println("\n=== Analysis ===");
        if (trainVsLive < 0.95) {
            System.out.println("⚠️ WARNING: Training and live pipelines produce different embeddings!");
            System.out.println("   Expected: ≥0.95, Got: " + String.format("%.4f", trainVsLive));
            System.out.println("   This explains why live recognition scores are lower (0.70-0.78 vs 0.85-0.92)");
        } else {
            System.out.println("✅ Training and live pipelines match well (" + String.format("%.4f", trainVsLive) + ")");
        }

        if (trainVsDirect > 0.98) {
            System.out
                    .println("✅ Direct preprocessing matches training (" + String.format("%.4f", trainVsDirect) + ")");
            System.out.println("   The issue is in the downscale→upscale simulation of webcam ROI");
        }
    }

    private static Mat preprocessForTraining(Mat image, ImageProcessor imgProc, FaceEmbeddingGenerator embGen) {
        // Simulate the training preprocessing path
        // This should match preprocessFaceForRecognition (private method)
        try {
            Mat denoised = new Mat();
            if (image.channels() == 1) {
                org.opencv.photo.Photo.fastNlMeansDenoising(image, denoised, 3.0f, 7, 21);
            } else {
                org.opencv.photo.Photo.fastNlMeansDenoisingColored(image, denoised, 3.0f, 3.0f, 7, 21);
            }
            image.release();

            Mat resized = new Mat();
            org.opencv.imgproc.Imgproc.resize(denoised, resized, new Size(96, 96),
                    0, 0, org.opencv.imgproc.Imgproc.INTER_CUBIC);
            denoised.release();

            return resized;
        } catch (Exception e) {
            System.err.println("Training preprocessing failed: " + e.getMessage());
            return image;
        }
    }
}
