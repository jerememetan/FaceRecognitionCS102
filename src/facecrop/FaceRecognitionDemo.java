package facecrop;

import ConfigurationAndLogging.AppConfig;
import ConfigurationAndLogging.AppLogger;
import app.service.FaceEmbeddingGenerator;
import app.util.ImageProcessor;
import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.*;

public class FaceRecognitionDemo {

    private static final int FRAME_SLEEP_MS = 25;

    static {
        System.load(new File("lib/opencv_java480.dll").getAbsolutePath());
    }

    private static class PersonModel {
        final String label;
        final List<byte[]> embeddings;
        final double[] centroid;
        final double[] weights;
        final double softThreshold;
        final double highThreshold;

        PersonModel(String label, List<byte[]> embeddings, double[] centroid,
                    double[] weights, double softThreshold, double highThreshold) {
            this.label = label;
            this.embeddings = embeddings;
            this.centroid = centroid;
            this.weights = weights;
            this.softThreshold = softThreshold;
            this.highThreshold = highThreshold;
        }
    }

    public static void main(String[] args) {
        AppLogger.info("FaceRecognitionDemo running with embedding-based recognition");
        AppConfig config = AppConfig.getInstance();
        ImageProcessor imageProcessor = new ImageProcessor();
        FaceEmbeddingGenerator embGen = new FaceEmbeddingGenerator();

        List<PersonModel> models = loadPersonModels(config, embGen);
        if (models.isEmpty()) {
            AppLogger.error("No embeddings found. Capture faces first before running recognition.");
            return;
        }

        DetectorContext detectorContext = initializeDetectors(config);
        if (detectorContext.failed()) {
            AppLogger.error("Failed to initialize detectors. Abort recognition.");
            return;
        }

        VideoCapture capture = new VideoCapture(config.getCameraIndex());
        if (!capture.isOpened()) {
            AppLogger.error("Error opening webcam!");
            return;
        }

        JFrame frame = new JFrame("Embedding Face Recognition");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        JLabel display = new JLabel();
        frame.add(display);
        frame.setSize(800, 600);
        frame.setVisible(true);

        Deque<Integer> recentPredictions = new ArrayDeque<>(config.getConsistencyWindow());
        List<byte[]> cohortBuffer = buildCohortVectors(models);

        Mat webcamFrame = new Mat();
        Mat gray = new Mat();

        try {
            while (frame.isVisible() && capture.read(webcamFrame)) {
                List<Rect> detections = detectFaces(webcamFrame, gray, detectorContext, config);
                for (Rect rect : detections) {
                    Imgproc.rectangle(webcamFrame, new Point(rect.x, rect.y),
                            new Point(rect.x + rect.width, rect.y + rect.height), new Scalar(0, 255, 0), 2);

                    Mat faceColor = webcamFrame.submat(rect);
                    ImageProcessor.ImageQualityResult quality = imageProcessor.validateImageQualityDetailed(faceColor);
                    if (!quality.isGoodQuality()) {
                        Imgproc.putText(webcamFrame, "unknown", new Point(rect.x, rect.y - 10),
                                Imgproc.FONT_HERSHEY_SIMPLEX, 0.9, new Scalar(0, 0, 255), 2);
                        continue;
                    }

                    Mat aligned = alignFace(faceColor);
                    Mat preprocessed = imageProcessor.preprocessFaceImage(aligned);
                    Imgproc.resize(preprocessed, preprocessed,
                            new Size(AppConfig.KEY_RECOGNITION_CROP_SIZE_PX, AppConfig.KEY_RECOGNITION_CROP_SIZE_PX));
                    byte[] queryEmbedding = embGen.generateEmbedding(preprocessed);

                    RecognitionResult result = recognise(models, cohortBuffer, queryEmbedding, embGen, config,
                            recentPredictions);

                    Imgproc.putText(webcamFrame, result.displayLabel,
                            new Point(rect.x, rect.y - 10), Imgproc.FONT_HERSHEY_SIMPLEX, 0.9,
                            result.isUnknown ? new Scalar(0, 0, 255) : new Scalar(15, 255, 15), 2);
                }

                BufferedImage buffered = matToBufferedImage(webcamFrame);
                display.setIcon(new ImageIcon(buffered));
                display.repaint();

                try {
                    Thread.sleep(FRAME_SLEEP_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            capture.release();
            gray.release();
            webcamFrame.release();
            if (detectorContext.faceCascade != null) {
                detectorContext.faceCascade.release();
            }
            if (detectorContext.dnnNet != null) {
                detectorContext.dnnNet.close();
            }
            frame.dispose();
            AppLogger.info("FaceRecognitionDemo exited");
        }
    }

    private record RecognitionResult(String displayLabel, boolean isUnknown) {}

    private static RecognitionResult recognise(List<PersonModel> models, List<byte[]> cohortVectors,
                                               byte[] queryEmbedding, FaceEmbeddingGenerator embGen,
                                               AppConfig config, Deque<Integer> recentPredictions) {
        if (queryEmbedding == null) {
            return new RecognitionResult("unknown", true);
        }

        List<Double> scores = new ArrayList<>(models.size());
        for (int i = 0; i < models.size(); i++) {
            PersonModel model = models.get(i);
            double score = computeFusedScore(queryEmbedding, model.embeddings, model.centroid, model.weights, embGen,
                    config.getRecognitionTopK());
            if (config.isCohortEnabled()) {
                score = applyCohortZNorm(score, queryEmbedding, cohortVectors, i, config);
            }
            scores.add(score);
        }

        if (scores.isEmpty()) {
            return new RecognitionResult("unknown", true);
        }

        int bestIdx = 0;
        for (int i = 1; i < scores.size(); i++) {
            if (scores.get(i) > scores.get(bestIdx)) bestIdx = i;
        }

        double best = scores.get(bestIdx);
        double second = 0.0;
        for (int i = 0; i < scores.size(); i++) {
            if (i == bestIdx) continue;
            second = Math.max(second, scores.get(i));
        }

        PersonModel winner = models.get(bestIdx);
        boolean deep = embGen.isDeepLearningAvailable();
        double margin = deep ? config.getRecognitionMarginDeep() : config.getRecognitionMarginFallback();
        double soft = Math.max(config.getRecognitionThreshold(), winner.softThreshold);
        double high = Math.max(soft, winner.highThreshold);

        if (best >= high) {
            updateRecentPredictions(recentPredictions, bestIdx, config.getConsistencyWindow());
            return new RecognitionResult(winner.label, false);
        }

        boolean consistent = isConsistent(recentPredictions, bestIdx, config.getConsistencyMinCount());
        if (best >= soft && (best - second) >= margin) {
            updateRecentPredictions(recentPredictions, bestIdx, config.getConsistencyWindow());
            return new RecognitionResult(winner.label, false);
        }
        if (best >= (soft - 0.02) && consistent) {
            updateRecentPredictions(recentPredictions, bestIdx, config.getConsistencyWindow());
            return new RecognitionResult(winner.label, false);
        }

        updateRecentPredictions(recentPredictions, -1, config.getConsistencyWindow());
        return new RecognitionResult("unknown", true);
    }

    private static void updateRecentPredictions(Deque<Integer> recentPredictions, int idx, int window) {
        if (recentPredictions.size() == window) {
            recentPredictions.pollFirst();
        }
        recentPredictions.offerLast(idx);
    }

    private static boolean isConsistent(Deque<Integer> recentPredictions, int idx, int required) {
        if (idx < 0) return false;
        int count = 0;
        for (Integer val : recentPredictions) {
            if (val != null && val == idx) count++;
        }
        return count >= required;
    }

    private static class DetectorContext {
        final Net dnnNet;
        final CascadeClassifier faceCascade;

        DetectorContext(Net dnnNet, CascadeClassifier faceCascade) {
            this.dnnNet = dnnNet;
            this.faceCascade = faceCascade;
        }

        boolean failed() { return dnnNet == null && faceCascade == null; }
    }

    private static DetectorContext initializeDetectors(AppConfig config) {
        Net net = null;
        CascadeClassifier cascade = null;
        if (config.isDnnDetectionEnabled()) {
            try {
                net = Dnn.readNetFromTensorflow(config.getDnnModelPath(), config.getDnnConfigPath());
                AppLogger.info("Loaded DNN face detector");
            } catch (Exception e) {
                AppLogger.error("Failed to load DNN detector, will fall back to Haar", e);
            }
        }
        if (net == null) {
            cascade = new CascadeClassifier(config.getCascadePath());
            if (cascade.empty()) {
                AppLogger.error("Could not load Haar cascade from " + config.getCascadePath());
                cascade = null;
            }
        } else {
            // keep Haar around for runtime fallback
            cascade = new CascadeClassifier(config.getCascadePath());
            if (cascade.empty()) {
                AppLogger.warn("Haar cascade missing; DNN will operate without fallback.");
                cascade = null;
            }
        }
        return new DetectorContext(net, cascade);
    }

    private static List<Rect> detectFaces(Mat frame, Mat gray, DetectorContext ctx, AppConfig config) {
        List<Rect> detections = new ArrayList<>();
        if (ctx.dnnNet != null) {
            Mat blob = Dnn.blobFromImage(frame, 1.0, new Size(300, 300), new Scalar(104, 177, 123), false, false);
            ctx.dnnNet.setInput(blob);
            Mat det = ctx.dnnNet.forward();
            double confThr = config.getDnnConfidence();
            Mat rows7 = det.reshape(1, (int) (det.total() / 7));
            for (int r = 0; r < rows7.rows(); r++) {
                double confidence = rows7.get(r, 2)[0];
                if (confidence < confThr) continue;
                int x1 = (int) (rows7.get(r, 3)[0] * frame.cols());
                int y1 = (int) (rows7.get(r, 4)[0] * frame.rows());
                int x2 = (int) (rows7.get(r, 5)[0] * frame.cols());
                int y2 = (int) (rows7.get(r, 6)[0] * frame.rows());
                Rect rect = new Rect(
                        new Point(Math.max(0, x1), Math.max(0, y1)),
                        new Point(Math.min(frame.cols(), x2), Math.min(frame.rows(), y2))
                );
                if (rect.width >= config.getDetectionMinSize() && rect.height >= config.getDetectionMinSize()) {
                    detections.add(rect);
                }
            }
            blob.release();
            rows7.release();
            det.release();
        }

        if (detections.isEmpty() && ctx.faceCascade != null) {
            Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(gray, gray, new Size(AppConfig.KEY_PREPROCESSING_GAUSSIAN_KERNEL_SIZE,
                            AppConfig.KEY_PREPROCESSING_GAUSSIAN_KERNEL_SIZE), AppConfig.KEY_PREPROCESSING_GAUSSIAN_SIGMA_X);
            Imgproc.createCLAHE(AppConfig.KEY_PREPROCESSING_CLAHE_CLIP_LIMIT,
                    new Size(AppConfig.KEY_PREPROCESSING_CLAHE_GRID_SIZE, AppConfig.KEY_PREPROCESSING_CLAHE_GRID_SIZE))
                    .apply(gray, gray);
            MatOfRect faces = new MatOfRect();
            ctx.faceCascade.detectMultiScale(gray, faces, config.getDetectionScaleFactor(),
                    config.getDetectionMinNeighbors(), 0,
                    new Size(config.getDetectionMinSize(), config.getDetectionMinSize()), new Size());
            detections.addAll(Arrays.asList(faces.toArray()));
        }
        return detections;
    }

    private static Mat alignFace(Mat faceColor) {
        CascadeClassifier eyeDetector = null;
        Mat gray = new Mat();
        MatOfRect eyes = new MatOfRect();
        try {
            File eyeFile = new File("data/resources/haarcascade_eye.xml");
            if (!eyeFile.exists()) return faceColor;
            eyeDetector = new CascadeClassifier(eyeFile.getAbsolutePath());
            if (eyeDetector.empty()) return faceColor;
            Imgproc.cvtColor(faceColor, gray, Imgproc.COLOR_BGR2GRAY);
            eyeDetector.detectMultiScale(gray, eyes, 1.1, 2, 0, new Size(20, 20), new Size());
            Rect[] arr = eyes.toArray();
            if (arr.length >= 2) {
                Arrays.sort(arr, Comparator.comparingInt(r -> -r.width * r.height));
                Point c1 = new Point(arr[0].x + arr[0].width / 2.0, arr[0].y + arr[0].height / 2.0);
                Point c2 = new Point(arr[1].x + arr[1].width / 2.0, arr[1].y + arr[1].height / 2.0);
                if (c2.x < c1.x) {
                    Point tmp = c1; c1 = c2; c2 = tmp;
                }
                double angle = Math.toDegrees(Math.atan2(c2.y - c1.y, c2.x - c1.x));
                Mat rotMat = Imgproc.getRotationMatrix2D(
                        new Point(faceColor.cols() / 2.0, faceColor.rows() / 2.0), angle, 1.0);
                Mat rotated = new Mat();
                Imgproc.warpAffine(faceColor, rotated, rotMat, faceColor.size(), Imgproc.INTER_LINEAR,
                        Core.BORDER_REPLICATE);
                rotMat.release();
                return rotated;
            }
        } catch (Exception ignored) {
        } finally {
            if (eyeDetector != null) eyeDetector.release();
            gray.release();
            eyes.release();
        }
        return faceColor;
    }

    private static List<PersonModel> loadPersonModels(AppConfig config, FaceEmbeddingGenerator embGen) {
        File baseDir = new File(config.getDatabaseStoragePath());
        File[] personDirs = baseDir.listFiles(File::isDirectory);
        if (personDirs == null) return Collections.emptyList();

        List<PersonModel> models = new ArrayList<>();
        boolean pruningEnabled = config.isPruningEnabled();
        for (File personDir : personDirs) {
            File[] embFiles = personDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".emb"));
            if (embFiles == null || embFiles.length == 0) continue;
            List<byte[]> embeddings = new ArrayList<>();
            for (File embFile : embFiles) {
                try {
                    embeddings.add(java.nio.file.Files.readAllBytes(embFile.toPath()));
                } catch (Exception ex) {
                    AppLogger.warn("Failed to read embedding " + embFile.getAbsolutePath());
                }
            }
            if (embeddings.isEmpty()) continue;

            if (pruningEnabled && embeddings.size() > config.getPruningMinKeep()) {
                embeddings = pruneOutliers(embeddings, embGen, config.getPruningStdFactor(), config.getPruningMinKeep());
            }

            double[] centroid = computeCentroid(embeddings);
            double[] weights = computeQualityWeights(embeddings, embGen);

            double tightness = computeTightness(embeddings, embGen);
            double soft = Math.max(config.getRecognitionSoftThreshold(), config.getRecognitionThreshold());
            double high = config.getRecognitionHighThreshold();
            if (config.isPersonThresholdsEnabled()) {
                double adj = config.getPersonThresholdsBeta() * (1.0 - tightness);
                soft = clamp01(soft + adj);
                high = clamp01(high + adj);
            }

            String label = buildLabel(personDir.getName());
            models.add(new PersonModel(label, embeddings, centroid, weights, soft, high));
        }
        return models;
    }

    private static List<byte[]> buildCohortVectors(List<PersonModel> models) {
        List<byte[]> cohort = new ArrayList<>();
        for (PersonModel model : models) {
            if (model.centroid == null) continue;
            cohort.add(encodeDoubleVector(model.centroid));
        }
        return cohort;
    }

    private static String buildLabel(String folderName) {
        if (folderName == null || folderName.isBlank()) return "unknown";
        String[] parts = folderName.split("_", 2);
        if (parts.length == 2) {
            String id = parts[0].trim();
            String name = parts[1].trim();
            if (!id.isEmpty() && !name.isEmpty()) return id + " - " + name;
        }
        return folderName;
    }

    private static byte[] encodeDoubleVector(double[] vector) {
        ByteBuffer bb = ByteBuffer.allocate(vector.length * 8);
        for (double v : vector) {
            bb.putDouble(v);
        }
        return bb.array();
    }

    private static double[] computeCentroid(List<byte[]> embeddings) {
        double[] sum = null;
        for (byte[] emb : embeddings) {
            double[] decoded = decodeEmbedding(emb);
            if (decoded == null) continue;
            normalizeL2(decoded);
            if (sum == null) {
                sum = Arrays.copyOf(decoded, decoded.length);
            } else {
                for (int i = 0; i < decoded.length; i++) sum[i] += decoded[i];
            }
        }
        if (sum == null) return null;
        for (int i = 0; i < sum.length; i++) sum[i] /= embeddings.size();
        normalizeL2(sum);
        return sum;
    }

    private static double[] computeQualityWeights(List<byte[]> embeddings, FaceEmbeddingGenerator embGen) {
        int n = embeddings.size();
        double[] weights = new double[n];
        if (n == 0) return weights;
        double[] scores = new double[n];
        for (int i = 0; i < n; i++) {
            double acc = 0; int count = 0;
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                acc += embGen.calculateSimilarity(embeddings.get(i), embeddings.get(j));
                count++;
            }
            scores[i] = count > 0 ? acc / count : 0.0;
        }
        double min = Arrays.stream(scores).min().orElse(0.0);
        double max = Arrays.stream(scores).max().orElse(0.0);
        if (max - min < 1e-6) {
            Arrays.fill(weights, 1.0);
        } else {
            for (int i = 0; i < n; i++) weights[i] = (scores[i] - min) / (max - min);
        }
        return weights;
    }

    private static double computeTightness(List<byte[]> embeddings, FaceEmbeddingGenerator embGen) {
        int n = embeddings.size();
        if (n < 2) return 1.0;
        double acc = 0; int count = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                acc += embGen.calculateSimilarity(embeddings.get(i), embeddings.get(j));
                count++;
            }
        }
        return count > 0 ? acc / count : 1.0;
    }

    private static List<byte[]> pruneOutliers(List<byte[]> embeddings, FaceEmbeddingGenerator embGen,
                                              double stdFactor, int minKeep) {
        int n = embeddings.size();
        if (n <= minKeep) return embeddings;
        double[] scores = new double[n];
        for (int i = 0; i < n; i++) {
            double acc = 0; int cnt = 0;
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                acc += embGen.calculateSimilarity(embeddings.get(i), embeddings.get(j));
                cnt++;
            }
            scores[i] = cnt > 0 ? acc / cnt : 0.0;
        }
        double mean = Arrays.stream(scores).average().orElse(0.0);
        double variance = 0.0;
        for (double score : scores) variance += Math.pow(score - mean, 2);
        variance /= Math.max(1, n - 1);
        double std = Math.sqrt(variance);
        double cutoff = mean - stdFactor * std;
        List<byte[]> kept = new ArrayList<>();
        for (int i = 0; i < n; i++) if (scores[i] >= cutoff) kept.add(embeddings.get(i));
        if (kept.size() < minKeep) {
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) order[i] = i;
            Arrays.sort(order, (a, b) -> Double.compare(scores[b], scores[a]));
            kept.clear();
            for (int i = 0; i < Math.min(minKeep, n); i++) kept.add(embeddings.get(order[i]));
        }
        return kept;
    }

    private static double computeFusedScore(byte[] queryEmb, List<byte[]> exemplars, double[] centroid,
                                            double[] weights, FaceEmbeddingGenerator embGen, int topK) {
        if (exemplars == null || exemplars.isEmpty()) return 0.0;
        int n = exemplars.size();
        double[][] list = new double[n][2];
        for (int i = 0; i < n; i++) {
            double sim = embGen.calculateSimilarity(queryEmb, exemplars.get(i));
            double w = (weights != null && i < weights.length) ? Math.max(0.2, weights[i]) : 1.0;
            list[i][0] = sim;
            list[i][1] = w;
        }
        Arrays.sort(list, Comparator.comparingDouble(a -> a[0]));
        int k = Math.min(topK, n);
        double weightedSum = 0.0, weightTotal = 0.0;
        double[] top = new double[k];
        for (int i = 0; i < k; i++) {
            double sim = list[n - 1 - i][0];
            double w = list[n - 1 - i][1];
            top[i] = sim;
            weightedSum += sim * w;
            weightTotal += w;
        }
        double avg = weightTotal > 1e-6 ? weightedSum / weightTotal : Arrays.stream(top).average().orElse(0.0);
        double median = k % 2 == 1 ? top[k / 2] : (top[k / 2 - 1] + top[k / 2]) / 2.0;
        double exemplarScore = 0.7 * avg + 0.3 * median;

        double centroidScore = 0.0;
        if (centroid != null) {
            centroidScore = cosineSimilarity(queryEmb, centroid);
        }
        double alpha = Math.min(0.8, Math.max(0.4, 0.4 + 0.02 * Math.max(0, n - 5)));
        return alpha * centroidScore + (1.0 - alpha) * exemplarScore;
    }

    private static double applyCohortZNorm(double score, byte[] queryEmb, List<byte[]> cohortVectors,
                                           int excludeIdx, AppConfig config) {
        if (cohortVectors.isEmpty()) return score;
        List<Double> sims = new ArrayList<>();
        for (int i = 0; i < cohortVectors.size(); i++) {
            if (i == excludeIdx) continue;
            sims.add(cosineSimilarity(queryEmb, decodeEmbedding(cohortVectors.get(i))));
        }
        if (sims.isEmpty()) return score;
        sims.sort((a, b) -> Double.compare(b, a));
        int k = Math.min(config.getCohortSize(), sims.size());
        double mean = 0.0;
        for (int i = 0; i < k; i++) mean += sims.get(i);
        mean /= k;
        double variance = 0.0;
        for (int i = 0; i < k; i++) variance += Math.pow(sims.get(i) - mean, 2);
        double std = Math.sqrt(variance / Math.max(1, k - 1));
        if (std < 1e-6) return score;
        double z = (score - mean) / std;
        return z >= config.getCohortZMin() ? score : 0.0;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double cosineSimilarity(byte[] embBytes, double[] vector) {
        double[] decoded = decodeEmbedding(embBytes);
        if (decoded == null || vector == null || decoded.length != vector.length) return 0.0;
        normalizeL2(decoded);
        double dot = 0.0, norm1 = 0.0, norm2 = 0.0;
        for (int i = 0; i < decoded.length; i++) {
            dot += decoded[i] * vector[i];
            norm1 += decoded[i] * decoded[i];
            norm2 += vector[i] * vector[i];
        }
        double denom = Math.sqrt(Math.max(norm1, 1e-12)) * Math.sqrt(Math.max(norm2, 1e-12));
        return denom > 0 ? dot / denom : 0.0;
    }

    private static double[] decodeEmbedding(byte[] emb) {
        if (emb == null) return null;
        try {
            if (emb.length == 128 * 4) {
                double[] dv = new double[128];
                ByteBuffer bb = ByteBuffer.wrap(emb);
                for (int i = 0; i < 128; i++) dv[i] = bb.getFloat();
                return dv;
            }
            if (emb.length == 128 * 8) {
                double[] dv = new double[128];
                ByteBuffer bb = ByteBuffer.wrap(emb);
                for (int i = 0; i < 128; i++) dv[i] = bb.getDouble();
                return dv;
            }
            int n = emb.length / 4;
            double[] dv = new double[n];
            ByteBuffer bb = ByteBuffer.wrap(emb);
            for (int i = 0; i < n; i++) dv[i] = bb.getFloat();
            return dv;
        } catch (Exception e) {
            return null;
        }
    }

    private static void normalizeL2(double[] vector) {
        double norm = 0.0;
        for (double v : vector) norm += v * v;
        norm = Math.sqrt(Math.max(norm, 1e-12));
        for (int i = 0; i < vector.length; i++) vector[i] /= norm;
    }

    private static BufferedImage matToBufferedImage(Mat mat) {
        int width = mat.cols();
        int height = mat.rows();
        int type = mat.channels() > 1 ? BufferedImage.TYPE_3BYTE_BGR : BufferedImage.TYPE_BYTE_GRAY;
        Mat converted = mat;
        if (mat.channels() > 1) {
            converted = new Mat();
            Imgproc.cvtColor(mat, converted, Imgproc.COLOR_BGR2RGB);
        }
        BufferedImage image = new BufferedImage(width, height, type);
        byte[] data = new byte[(int) (converted.total() * converted.elemSize())];
        converted.get(0, 0, data);
        image.getRaster().setDataElements(0, 0, width, height, data);
        if (converted != mat) converted.release();
        return image;
    }
}