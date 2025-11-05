package service.recognition;

/**
 * Applies the business rules that decide whether a candidate match is accepted
 * or rejected.
 */
final class RecognitionDecisionEngine {

    private static final double CONFIDENCE_THRESHOLD_RELAXATION = 0.92;
    private static final double MIN_LIVE_ABSOLUTE_THRESHOLD = 0.48;
    private static final double MARGIN_CONFIDENCE_WEIGHT = 0.6;
    private static final double DISCRIMINATIVE_CONFIDENCE_WEIGHT = 0.4;
    private static final double MIN_RELATIVE_MARGIN_PCT = 0.10;
    private static final double MIN_ABSOLUTE_MARGIN = 0.08;
    private static final double MIN_CONFIDENCE = 0.20;
    private static final double STRONG_CONFIDENCE = 0.65;

    RecognitionDecision evaluate(
            RecognitionProfile profile,
            RecognitionScorer.ScoreResult scores,
            boolean consistent,
            int matchCount,
            int consistencyWindowSize,
            int minimumConsistencyCount) {

        if (profile == null || scores == null || scores.isEmpty()) {
            return RecognitionDecision.rejected("No profiles available");
        }

        double bestScore = scores.bestScore();
        double secondBest = scores.secondBestScore();
        double absoluteThreshold = profile.absoluteThreshold();
        double absoluteMargin = bestScore - secondBest;

    double marginConfidence = calculateMarginConfidence(bestScore, secondBest);
    double confidenceFloor = Math.max(
        MIN_LIVE_ABSOLUTE_THRESHOLD,
        Math.min(0.99, absoluteThreshold * CONFIDENCE_THRESHOLD_RELAXATION));
    double discriminativeConfidence = calculateDiscriminativeConfidence(
        scores.discriminativeScore(), confidenceFloor);
    double combinedConfidence = clamp01(
        (MARGIN_CONFIDENCE_WEIGHT * marginConfidence)
            + (DISCRIMINATIVE_CONFIDENCE_WEIGHT * discriminativeConfidence));

    double dynamicRawRequirement = Math.max(absoluteThreshold - 0.02, MIN_LIVE_ABSOLUTE_THRESHOLD);
        boolean rawScorePass = bestScore >= dynamicRawRequirement;
    boolean strongRawPass = bestScore >= Math.max(absoluteThreshold + 0.04, MIN_LIVE_ABSOLUTE_THRESHOLD + 0.05);
        boolean absoluteThresholdPass = bestScore >= absoluteThreshold;
        boolean absoluteMarginPass = absoluteMargin >= MIN_ABSOLUTE_MARGIN;
        boolean confidencePass = combinedConfidence >= MIN_CONFIDENCE;
        boolean strongConfidencePass = combinedConfidence >= STRONG_CONFIDENCE;
        boolean discriminativePass = scores.discriminativeScore() >= absoluteThreshold;

        double relativeMarginPct = bestScore > 0 ? (bestScore - secondBest) / bestScore : 0.0;
        double requiredMarginPct = Math.max(
                MIN_RELATIVE_MARGIN_PCT,
                profile.relativeMargin() / Math.max(bestScore, 1e-6));

    if (strongRawPass && strongConfidencePass && relativeMarginPct >= requiredMarginPct) {
            return RecognitionDecision.accept(
                    profile.displayLabel(),
                    bestScore,
                    combinedConfidence,
                    absoluteMargin,
                    relativeMarginPct,
                    requiredMarginPct,
                    "Strong confidence match");
        }

    if (rawScorePass && absoluteThresholdPass && absoluteMarginPass && confidencePass) {
            return RecognitionDecision.accept(
                    profile.displayLabel(),
                    bestScore,
                    combinedConfidence,
                    absoluteMargin,
                    relativeMarginPct,
                    requiredMarginPct,
                    "Standard confidence match");
        }

    if (rawScorePass && absoluteThresholdPass && consistent &&
        matchCount >= minimumConsistencyCount && combinedConfidence >= 0.10) {
            String reason = String.format(
                    "Consistency override (%d/%d frames)",
                    matchCount,
                    consistencyWindowSize);
            return RecognitionDecision.accept(
                    profile.displayLabel(),
                    bestScore,
                    combinedConfidence,
                    absoluteMargin,
                    relativeMarginPct,
                    requiredMarginPct,
                    reason);
        }

        if (rawScorePass && discriminativePass && absoluteMarginPass) {
            return RecognitionDecision.accept(
                    profile.displayLabel(),
                    bestScore,
                    combinedConfidence,
                    absoluteMargin,
                    relativeMarginPct,
                    requiredMarginPct,
                    "Discriminative match (low negative evidence)");
        }

    if (!rawScorePass && absoluteMarginPass && relativeMarginPct >= requiredMarginPct
        && combinedConfidence >= STRONG_CONFIDENCE) {
        return RecognitionDecision.accept(
            profile.displayLabel(),
            bestScore,
            combinedConfidence,
            absoluteMargin,
            relativeMarginPct,
            requiredMarginPct,
            "Relative evidence override");
    }

        String reason;
        if (!rawScorePass) {
            reason = String.format("Raw score too low (%.3f < %.2f)", bestScore, dynamicRawRequirement);
        } else if (!absoluteMarginPass) {
            reason = String.format("Insufficient margin (%.3f < %.2f)", absoluteMargin, MIN_ABSOLUTE_MARGIN);
        } else if (!confidencePass) {
            reason = String.format("Low confidence (%.2f < %.2f)", combinedConfidence, MIN_CONFIDENCE);
        } else {
            reason = "Multiple criteria failed";
        }

        return RecognitionDecision.reject(
                profile.displayLabel(),
                bestScore,
                combinedConfidence,
                absoluteMargin,
                relativeMarginPct,
                requiredMarginPct,
                reason);
    }

    private double calculateMarginConfidence(double bestScore, double secondScore) {
        double denominator = Math.max(bestScore, 0.01);
        double margin = bestScore - secondScore;
        double confidence = margin / denominator;
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    private double calculateDiscriminativeConfidence(double discriminativeScore, double threshold) {
        double numerator = discriminativeScore - threshold;
        double denominator = Math.max(0.05, 1.0 - threshold);
        return clamp01(numerator / denominator);
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    static final class RecognitionDecision {
        private final boolean accepted;
        private final String label;
        private final double rawScore;
        private final double confidence;
        private final double margin;
        private final double relativeMarginPct;
        private final double requiredMarginPct;
        private final String reason;

        private RecognitionDecision(boolean accepted, String label, double rawScore, double confidence,
                double margin, double relativeMarginPct, double requiredMarginPct, String reason) {
            this.accepted = accepted;
            this.label = label;
            this.rawScore = rawScore;
            this.confidence = confidence;
            this.margin = margin;
            this.relativeMarginPct = relativeMarginPct;
            this.requiredMarginPct = requiredMarginPct;
            this.reason = reason;
        }

        static RecognitionDecision accept(String label, double rawScore, double confidence,
                double margin, double relativeMarginPct, double requiredMarginPct, String reason) {
            return new RecognitionDecision(true, label, rawScore, confidence, margin, relativeMarginPct,
                    requiredMarginPct, reason);
        }

        static RecognitionDecision reject(String label, double rawScore, double confidence,
                double margin, double relativeMarginPct, double requiredMarginPct, String reason) {
            return new RecognitionDecision(false, label, rawScore, confidence, margin, relativeMarginPct,
                    requiredMarginPct, reason);
        }

        static RecognitionDecision rejected(String reason) {
            return new RecognitionDecision(false, "unknown", 0.0, 0.0, 0.0, 0.0, 0.0, reason);
        }

        boolean accepted() {
            return accepted;
        }

        String label() {
            return label;
        }

        double rawScore() {
            return rawScore;
        }

        double confidence() {
            return confidence;
        }

        double margin() {
            return margin;
        }

        double relativeMarginPct() {
            return relativeMarginPct;
        }

        double requiredMarginPct() {
            return requiredMarginPct;
        }

        String reason() {
            return reason;
        }
    }
}







