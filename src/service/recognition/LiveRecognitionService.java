package service.recognition;

import config.AppLogger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    private static final long SESSION_TIMEOUT_MILLIS = 5_000;
    private static final long FRAME_LAG_RESET_MS = 900;

    private final ImageProcessor imageProcessor = new ImageProcessor();
    private final FaceEmbeddingGenerator embeddingGenerator = new FaceEmbeddingGenerator();
    private final LiveRecognitionPreprocessor livePreprocessor = new LiveRecognitionPreprocessor();
    private final RecognitionDatasetRepository datasetRepository = new RecognitionDatasetRepository(embeddingGenerator);
    private final RecognitionScorer scorer = new RecognitionScorer(datasetRepository, embeddingGenerator);
    private final RecognitionDecisionEngine decisionEngine = new RecognitionDecisionEngine();
    private final RecognitionConfidenceCalibrator confidenceCalibrator = new RecognitionConfidenceCalibrator();
    private final Map<String, RecognitionSession> sessions = new ConcurrentHashMap<>();

    public LiveRecognitionService() {
        reloadDataset();
    }

    public void reloadDataset() {
        datasetRepository.reload();
        sessions.clear();
    }

    public RecognitionOutcome analyzeFace(Mat frame, Rect faceRect, String sessionId) {
        if (frame == null || frame.empty() || faceRect == null) {
            return RecognitionOutcome.rejected();
        }

        String key = (sessionId == null || sessionId.isBlank()) ? "default" : sessionId;
        RecognitionSession session = sessionFor(key);
        long frameTimestamp = System.currentTimeMillis();
        session.touch();
        cleanupStaleSessions();

        if (session.registerFrame(frameTimestamp, FRAME_LAG_RESET_MS)) {
            AppLogger.info("Frame cadence gap detected; resetting recognition history for session " + key);
            session.history.reset();
        }

        Rect paddedRect = RecognitionGeometry.paddedFaceRect(frame.size(), faceRect, 0.15);
        Mat faceColor = new Mat(frame, paddedRect).clone();
        try {
            ImageQualityResult qualityResult = imageProcessor.validateImageQualityDetailed(faceColor);
            if (!qualityResult.isGoodQuality()) {
                AppLogger.info("[Reject] Face rejected: Poor image quality. " + qualityResult.getFeedback());
                return RecognitionOutcome.rejected();
            }

            if (qualityResult.isBorderline()) {
                AppLogger.info("[Warn] Borderline image quality accepted: " + qualityResult.getFeedback());
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

            RecognitionHistory history = session.history;

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

            RecognitionFrameMetrics frameMetrics = RecognitionFrameMetrics.from(
                    frame.cols(),
                    frame.rows(),
                    faceRect,
                    paddedRect,
                    qualityResult);

            RecognitionConfidenceCalibrator.Calibration calibration = confidenceCalibrator.calibrate(
                scoreResult,
                frameMetrics);

            logScores(scoreResult, calibration);

            boolean consistent = history.isConsistent(scoreResult.bestIndex());
            int matchCount = history.countMatches(scoreResult.bestIndex());

            RecognitionDecisionEngine.RecognitionDecision decision = decisionEngine.evaluate(
                    profile,
                    scoreResult,
                    calibration,
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
                logDecisionAdjustments(decision);
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
            logDecisionAdjustments(decision);
            return RecognitionOutcome.rejected();
        } catch (Exception e) {
            AppLogger.error("Recognition error: " + e.getMessage(), e);
            return RecognitionOutcome.rejected();
        } finally {
            faceColor.release();
        }
    }

    /**
     * Analyzes a face and returns detailed recognition information including confidence and student ID.
     * This method is used for attendance marking where confidence levels are needed.
     * 
     * @param frame The video frame
     * @param faceRect The detected face rectangle
     * @param sessionId Session identifier
     * @return DetailedRecognitionResult with student ID, confidence, and recognition status
     */
    public DetailedRecognitionResult analyzeFaceDetailed(Mat frame, Rect faceRect, String sessionId) {
        if (frame == null || frame.empty() || faceRect == null) {
            return new DetailedRecognitionResult(null, 0.0, false);
        }

        String key = (sessionId == null || sessionId.isBlank()) ? "default" : sessionId;
        RecognitionSession session = sessionFor(key);
        long frameTimestamp = System.currentTimeMillis();
        session.touch();
        cleanupStaleSessions();

        if (session.registerFrame(frameTimestamp, FRAME_LAG_RESET_MS)) {
            AppLogger.info("Frame cadence gap detected; resetting recognition history for session " + key);
            session.history.reset();
        }

        Rect paddedRect = RecognitionGeometry.paddedFaceRect(frame.size(), faceRect, 0.15);
        Mat faceColor = new Mat(frame, paddedRect).clone();
        try {
            ImageQualityResult qualityResult = imageProcessor.validateImageQualityDetailed(faceColor);
            if (!qualityResult.isGoodQuality()) {
                return new DetailedRecognitionResult(null, 0.0, false);
            }

            RecognitionFrameMetrics frameMetrics = RecognitionFrameMetrics.from(
                    frame.cols(),
                    frame.rows(),
                    faceRect,
                    paddedRect,
                    qualityResult);

            Mat preprocessedBlob = livePreprocessor.preprocessForLiveRecognition(faceColor, paddedRect);
            if (preprocessedBlob == null || preprocessedBlob.empty()) {
                return new DetailedRecognitionResult(null, 0.0, false);
            }

            byte[] queryEmbedding = embeddingGenerator.generateEmbeddingFromBlob(preprocessedBlob);
            preprocessedBlob.release();

            if (queryEmbedding == null) {
                return new DetailedRecognitionResult(null, 0.0, false);
            }

            RecognitionHistory history = session.history;
            history.recordEmbedding(queryEmbedding);
            byte[] smoothedEmbedding = history.buildSmoothedEmbedding(embeddingGenerator);

            RecognitionScorer.ScoreResult scoreResult = scorer.score(queryEmbedding, smoothedEmbedding);
            if (scoreResult.isEmpty() || scoreResult.bestIndex() < 0) {
                return new DetailedRecognitionResult(null, 0.0, false);
            }

            RecognitionProfile profile = datasetRepository.profileAt(scoreResult.bestIndex());
            if (profile == null) {
                return new DetailedRecognitionResult(null, 0.0, false);
            }

            boolean consistent = history.isConsistent(scoreResult.bestIndex());
            int matchCount = history.countMatches(scoreResult.bestIndex());

            RecognitionConfidenceCalibrator.Calibration calibration = confidenceCalibrator.calibrate(
                    scoreResult,
                    frameMetrics);

            RecognitionDecisionEngine.RecognitionDecision decision = decisionEngine.evaluate(
                    profile,
                    scoreResult,
                    calibration,
                    consistent,
                    matchCount,
                    history.consistencyWindowSize(),
                    history.minimumConsistencyCount());

            history.recordPrediction(decision.accepted() ? scoreResult.bestIndex() : -1);

            // Extract student ID from profile's displayLabel (format: "S12345 - Name")
            // Use profile label instead of decision label, as decision label might be "unknown" when rejected
            String profileLabel = profile.displayLabel();
            String studentId = extractStudentIdFromLabel(profileLabel);
            
            // Use decision confidence (0.0-1.0 scale)
            double confidence = decision.confidence();
            
            // Also calculate raw similarity score as a fallback confidence measure
            double rawScore = scoreResult.bestScore();
            
            // Log the recognition decision for debugging
            AppLogger.info(String.format(
                "[Attendance Recognition] ProfileLabel=%s, StudentID=%s, Confidence=%.2f, RawScore=%.3f, Accepted=%b",
                profileLabel, studentId, confidence, rawScore, decision.accepted()));
            
            // Return result even if not accepted, so we can use confidence for attendance marking
            // This allows the attendance window to show recognition results even with lower confidence
            // Use the higher of decision confidence or a normalized raw score
        double effectiveConfidence = Math.max(
            decision.confidence(),
            Math.min(1.0, rawScore + decision.thresholdRelief()));
            return new DetailedRecognitionResult(studentId, effectiveConfidence, decision.accepted());
        } catch (Exception e) {
            AppLogger.error("Recognition error in analyzeFaceDetailed: " + e.getMessage(), e);
            return new DetailedRecognitionResult(null, 0.0, false);
        } finally {
            faceColor.release();
        }
    }
    
    private String extractStudentIdFromLabel(String label) {
        if (label == null || label.isEmpty() || label.equals("unknown")) {
            return null;
        }
        
        // Format: "S12345 - Name"
        String[] parts = label.split(" - ");
        if (parts.length > 0) {
            String studentId = parts[0].trim();
            // Validate student ID format (S followed by 5 digits)
            if (studentId.matches("^S\\d{5}$")) {
                return studentId;
            }
        }
        return null;
    }
    
    /**
     * Detailed recognition result containing student ID, confidence, and recognition status.
     */
    public static class DetailedRecognitionResult {
        private final String studentId;
        private final double confidence;
        private final boolean recognized;
        
        public DetailedRecognitionResult(String studentId, double confidence, boolean recognized) {
            this.studentId = studentId;
            this.confidence = confidence;
            this.recognized = recognized;
        }
        
        public String getStudentId() {
            return studentId;
        }
        
        public double getConfidence() {
            return confidence;
        }
        
        public boolean isRecognized() {
            return recognized;
        }
    }

    public int getAdaptiveFrameSkip() {
        return datasetRepository.getAdaptiveFrameSkip();
    }

    public void release() {
        livePreprocessor.release();
        sessions.clear();
    }

    public void discardSession(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    private void logScores(RecognitionScorer.ScoreResult scoreResult,
            RecognitionConfidenceCalibrator.Calibration calibration) {
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

    if (calibration != null && calibration.adjusted()) {
        AppLogger.info(String.format(
            "[Calibration] scale=%.2f, coverage=%.3f, relief=%.3f, marginRelax=%.2f, confBoost=%.2f, discBoost=%.2f %s",
            calibration.normalizedScale(),
            calibration.faceCoverage(),
            calibration.thresholdRelief(),
            calibration.marginRelaxation(),
            calibration.confidenceBoost(),
            calibration.discriminativeBoost(),
            calibration.notes().isEmpty() ? "" : ("| " + calibration.notes())));
    }

        if (scoreResult.prefilterSkipped() > 0) {
            AppLogger.info(String.format(
                    "[Performance] Pre-filter skipped %d/%d persons (%.1f%% speedup)",
                    scoreResult.prefilterSkipped(),
                    scores.size(),
                    (scoreResult.prefilterSkipped() * 100.0) / scores.size()));
        }
    }

    private void logDecisionAdjustments(RecognitionDecisionEngine.RecognitionDecision decision) {
        if (decision == null) {
            return;
        }

        double relief = decision.thresholdRelief();
        double confAdj = decision.confidenceAdjustment();
        double marginRelax = decision.marginRelaxation();
        double scaleRatio = decision.scaleRatio();

        if (Math.abs(relief) < 1e-6
                && Math.abs(confAdj) < 1e-6
                && Math.abs(1.0 - marginRelax) < 1e-6
                && Math.abs(scaleRatio - 1.0) < 1e-6) {
            return;
        }

        AppLogger.info(String.format(
                "[DecisionAdjust] relief=%.3f, confAdj=%.2f, marginRelax=%.2f, scale=%.2f, borderline=%b",
                relief,
                confAdj,
                marginRelax,
                scaleRatio,
                decision.borderlineQuality()));
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

    private RecognitionSession sessionFor(String sessionId) {
        return sessions.computeIfAbsent(sessionId, key -> new RecognitionSession());
    }

    private void cleanupStaleSessions() {
        long cutoff = System.currentTimeMillis() - SESSION_TIMEOUT_MILLIS;
        sessions.entrySet().removeIf(entry -> entry.getValue().lastUpdated < cutoff);
    }

    private static final class RecognitionSession {
        private final RecognitionHistory history = new RecognitionHistory();
        private volatile long lastUpdated = System.currentTimeMillis();
        private volatile long lastFrameTimestamp = 0L;

        void touch() {
            lastUpdated = System.currentTimeMillis();
        }

        boolean registerFrame(long frameTimestamp, long lagThresholdMs) {
            long previous = lastFrameTimestamp;
            lastFrameTimestamp = frameTimestamp;
            if (previous == 0L) {
                return false;
            }
            return (frameTimestamp - previous) > lagThresholdMs;
        }
    }
}







