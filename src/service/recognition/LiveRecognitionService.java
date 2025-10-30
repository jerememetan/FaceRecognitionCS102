package service.recognition;

import config.AppLogger;
import java.util.List;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import service.embedding.FaceEmbeddingGenerator;
import util.ImageProcessor;
import util.ImageProcessor.ImageQualityResult;

/**
 * Coordinates the live face recognition workflow by delegating to specialised
 * collaborators responsible for dataset management, scoring, decision making,
 * and temporal smoothing.
 */
public class LiveRecognitionService {

    private static final Scalar ACCEPT_COLOR = new Scalar(15, 255, 15);
    private static final Scalar REJECT_COLOR = new Scalar(0, 0, 255);

    private final ImageProcessor imageProcessor = new ImageProcessor();
    private final FaceEmbeddingGenerator embeddingGenerator = new FaceEmbeddingGenerator();
    private final LiveRecognitionPreprocessor livePreprocessor = new LiveRecognitionPreprocessor();
    private final RecognitionDatasetRepository datasetRepository = new RecognitionDatasetRepository(embeddingGenerator);
    private final RecognitionHistory history = new RecognitionHistory();
    private final RecognitionScorer scorer = new RecognitionScorer(datasetRepository, embeddingGenerator);
    private final RecognitionDecisionEngine decisionEngine = new RecognitionDecisionEngine();

    public LiveRecognitionService() {
        reloadDataset();
    }

    public void reloadDataset() {
        datasetRepository.reload();
        history.reset();
    }

    public RecognitionOutcome analyzeFace(Mat frame, Rect faceRect) {
        if (frame == null || frame.empty() || faceRect == null) {
            return RecognitionOutcome.rejected();
        }

        Rect paddedRect = RecognitionGeometry.paddedFaceRect(frame.size(), faceRect, 0.15);
        Mat faceColor = new Mat(frame, paddedRect).clone();
        try {
            ImageQualityResult qualityResult = imageProcessor.validateImageQualityDetailed(faceColor);
            if (!qualityResult.isGoodQuality()) {
                AppLogger.info("[Reject] Face rejected: Poor image quality. " + qualityResult.getFeedback());
                return RecognitionOutcome.rejected();
            }

            Mat preprocessedBlob = livePreprocessor.preprocessForLiveRecognition(faceColor, paddedRect);
            if (preprocessedBlob == null || preprocessedBlob.empty()) {
                AppLogger.info("[Reject] Face rejected: Preprocessing failed.");
                return RecognitionOutcome.rejected();
            }

            byte[] queryEmbedding = embeddingGenerator.generateEmbeddingFromBlob(preprocessedBlob);
            preprocessedBlob.release();

            if (queryEmbedding == null) {
                AppLogger.info("[Reject] Face rejected: Embedding generation failed.");
                return RecognitionOutcome.rejected();
            }

            history.recordEmbedding(queryEmbedding);
            byte[] smoothedEmbedding = history.buildSmoothedEmbedding(embeddingGenerator);

            RecognitionScorer.ScoreResult scoreResult = scorer.score(queryEmbedding, smoothedEmbedding);
            if (scoreResult.isEmpty() || scoreResult.bestIndex() < 0) {
                AppLogger.info("[Reject] Face rejected: No viable matches.");
                return RecognitionOutcome.rejected();
            }

            RecognitionProfile profile = datasetRepository.profileAt(scoreResult.bestIndex());
            if (profile == null) {
                AppLogger.warn("[Reject] Score result referenced missing profile index " + scoreResult.bestIndex());
                return RecognitionOutcome.rejected();
            }

            logScores(scoreResult);

            boolean consistent = history.isConsistent(scoreResult.bestIndex());
            int matchCount = history.countMatches(scoreResult.bestIndex());

            RecognitionDecisionEngine.RecognitionDecision decision = decisionEngine.evaluate(
                    profile,
                    scoreResult,
                    consistent,
                    matchCount,
                    history.consistencyWindowSize(),
                    history.minimumConsistencyCount());

            history.recordPrediction(decision.accepted() ? scoreResult.bestIndex() : -1);

            if (decision.accepted()) {
                String displayText = String.format("%s (%.2f)", decision.label(), decision.confidence());
                AppLogger.info(String.format(
                        "[Accept] %s | Raw=%.3f, Confidence=%.2f, Margin=%.3f | %s",
                        decision.label(), decision.rawScore(), decision.confidence(), decision.margin(),
                        decision.reason()));
                return RecognitionOutcome.accept(displayText);
            }

            AppLogger.info(String.format(
                    "[Reject] Best=%s(%.3f), 2nd=%.3f, Confidence=%.2f, Margin=%.3f | %s",
                    decision.label(),
                    decision.rawScore(),
                    scoreResult.secondBestScore(),
                    decision.confidence(),
                    decision.margin(),
                    decision.reason()));
            return RecognitionOutcome.rejected();
        } catch (Exception e) {
            AppLogger.error("Recognition error: " + e.getMessage(), e);
            return RecognitionOutcome.rejected();
        } finally {
            faceColor.release();
        }
    }

    public int getAdaptiveFrameSkip() {
        return datasetRepository.getAdaptiveFrameSkip();
    }

    public void release() {
        livePreprocessor.release();
    }

    private void logScores(RecognitionScorer.ScoreResult scoreResult) {
        List<RecognitionScorer.ProfileScore> scores = scoreResult.scores();
        StringBuilder builder = new StringBuilder("[Recognition] Scores: ");
        for (RecognitionScorer.ProfileScore profileScore : scores) {
            builder.append(String.format("%s=%.3f ",
                    profileScore.profile().displayLabel(),
                    profileScore.score()));
        }
        AppLogger.info(builder.toString());

        if (scoreResult.bestIndex() < 0 || scoreResult.bestIndex() >= scores.size()) {
            AppLogger.info("[Decision] No valid best profile to log.");
            return;
        }

        RecognitionProfile bestProfile = scores.get(scoreResult.bestIndex()).profile();
        AppLogger.info(String.format(
                "[Decision] Best=%s(%.3f), Discriminative=%.3f, AvgNeg=%.3f",
                bestProfile.displayLabel(),
                scoreResult.bestScore(),
                scoreResult.discriminativeScore(),
                scoreResult.averageNegativeScore()));
        AppLogger.info(String.format(
                "[Thresholds] Abs=%.3f, Margin=%.3f, Tightness=%.3f, StdDev=%.3f",
                bestProfile.absoluteThreshold(),
                bestProfile.relativeMargin(),
                bestProfile.tightness(),
                bestProfile.standardDeviation()));

        if (scoreResult.prefilterSkipped() > 0) {
            AppLogger.info(String.format(
                    "[Performance] Pre-filter skipped %d/%d persons (%.1f%% speedup)",
                    scoreResult.prefilterSkipped(),
                    scores.size(),
                    (scoreResult.prefilterSkipped() * 100.0) / scores.size()));
        }
    }

    public static class RecognitionOutcome {
        private final String displayText;
        private final Scalar displayColor;
        private final boolean accepted;

        private RecognitionOutcome(String displayText, Scalar displayColor, boolean accepted) {
            this.displayText = displayText;
            this.displayColor = displayColor;
            this.accepted = accepted;
        }

        public static RecognitionOutcome accept(String labelText) {
            return new RecognitionOutcome(labelText, ACCEPT_COLOR, true);
        }

        public static RecognitionOutcome rejected() {
            return new RecognitionOutcome("unknown", REJECT_COLOR, false);
        }

        public String displayText() {
            return displayText;
        }

        public Scalar displayColor() {
            return displayColor;
        }

        public boolean accepted() {
            return accepted;
        }
    }
}







