package service.recognition;

/**
 * Applies the business rules that decide whether a candidate match is accepted
 * or rejected.
 */
final class RecognitionDecisionEngine {

    private static final double CONFIDENCE_THRESHOLD_RELAXATION = 0.92;
    private static final double MIN_LIVE_ABSOLUTE_THRESHOLD = 0.42;
    private static final double MARGIN_CONFIDENCE_WEIGHT = 0.6;
    private static final double DISCRIMINATIVE_CONFIDENCE_WEIGHT = 0.4;
    private static final double MIN_RELATIVE_MARGIN_PCT = 0.08;
    private static final double MIN_ABSOLUTE_MARGIN = 0.07;
    private static final double MIN_CONFIDENCE = 0.20;
    private static final double STRONG_CONFIDENCE = 0.55;
    private static final double NEAR_THRESHOLD_DELTA = 0.09;
    private static final double NEAR_THRESHOLD_MARGIN_FACTOR = 0.85;
    private static final double NEAR_THRESHOLD_CONFIDENCE = 0.28;

    RecognitionDecision evaluate(
        RecognitionProfile profile,
        RecognitionScorer.ScoreResult scores,
        RecognitionConfidenceCalibrator.Calibration calibration,
        boolean consistent,
        int matchCount,
        int consistencyWindowSize,
        int minimumConsistencyCount) {

        if (profile == null || scores == null || scores.isEmpty()) {
            return RecognitionDecision.rejected("No profiles available");
        }

    RecognitionConfidenceCalibrator.Calibration adjustments = calibration != null
        ? calibration
        : RecognitionConfidenceCalibrator.Calibration.noAdjustment();

        double bestScore = scores.bestScore();
        double secondBest = scores.secondBestScore();
    double absoluteThreshold = Math.max(
        MIN_LIVE_ABSOLUTE_THRESHOLD,
        profile.absoluteThreshold() - adjustments.thresholdRelief());
        double absoluteMargin = bestScore - secondBest;

        double marginConfidence = calculateMarginConfidence(bestScore, secondBest);
    double confidenceFloor = Math.max(
        MIN_LIVE_ABSOLUTE_THRESHOLD,
        Math.min(0.99, absoluteThreshold * CONFIDENCE_THRESHOLD_RELAXATION)
            - adjustments.thresholdRelief() * 0.5);
    double discriminativeScore = clamp01(scores.discriminativeScore() + adjustments.discriminativeBoost());
    double discriminativeConfidence = calculateDiscriminativeConfidence(
        discriminativeScore, confidenceFloor);
        double combinedConfidence = clamp01(
                (MARGIN_CONFIDENCE_WEIGHT * marginConfidence)
            + (DISCRIMINATIVE_CONFIDENCE_WEIGHT * discriminativeConfidence)
            + adjustments.confidenceBoost());

    double dynamicRawRequirement = Math.max(absoluteThreshold - 0.25, MIN_LIVE_ABSOLUTE_THRESHOLD)
        - adjustments.thresholdRelief() * 0.3;
    dynamicRawRequirement = Math.max(MIN_LIVE_ABSOLUTE_THRESHOLD, dynamicRawRequirement);
    boolean rawScorePass = bestScore >= dynamicRawRequirement;
    boolean strongRawPass = bestScore >= Math.max(
        absoluteThreshold + 0.04 - adjustments.thresholdRelief(),
        MIN_LIVE_ABSOLUTE_THRESHOLD + 0.05);
    boolean absoluteThresholdPass = bestScore >= absoluteThreshold;
    double relaxedMarginFloor = Math.max(
        MIN_ABSOLUTE_MARGIN * adjustments.marginRelaxation(),
        MIN_ABSOLUTE_MARGIN - adjustments.thresholdRelief() * 0.4);
    relaxedMarginFloor = Math.max(0.04, relaxedMarginFloor);
    boolean absoluteMarginPass = absoluteMargin >= relaxedMarginFloor;

    double minConfidenceThreshold = Math.max(
        0.10,
        MIN_CONFIDENCE - (adjustments.borderlineQuality() ? 0.04 : 0.0)
            - adjustments.confidenceBoost() * 0.5);
    double strongConfidenceThreshold = Math.max(
        minConfidenceThreshold + 0.05,
        STRONG_CONFIDENCE - (adjustments.borderlineQuality() ? 0.05 : 0.0)
            - adjustments.confidenceBoost() * 0.5);

    boolean confidencePass = combinedConfidence >= minConfidenceThreshold;
    boolean strongConfidencePass = combinedConfidence >= strongConfidenceThreshold;
    boolean discriminativePass = discriminativeScore >= confidenceFloor;

    double relativeMarginPct = bestScore > 0 ? (bestScore - secondBest) / bestScore : 0.0;
    double requiredMarginPct = Math.max(
        MIN_RELATIVE_MARGIN_PCT * adjustments.marginRelaxation(),
        (profile.relativeMargin() / Math.max(bestScore, 1e-6)) * adjustments.marginRelaxation());

        if (strongRawPass && strongConfidencePass && relativeMarginPct >= requiredMarginPct) {
            return RecognitionDecision.accept(
                    profile.displayLabel(),
                    bestScore,
                    combinedConfidence,
                    absoluteMargin,
                    relativeMarginPct,
                    requiredMarginPct,
                    "Strong confidence match",
                    adjustments);
        }

        if (rawScorePass && absoluteThresholdPass && absoluteMarginPass && confidencePass) {
            return RecognitionDecision.accept(
                    profile.displayLabel(),
                    bestScore,
                    combinedConfidence,
                    absoluteMargin,
                    relativeMarginPct,
                    requiredMarginPct,
                    "Standard confidence match",
                    adjustments);
        }

        double nearThreshold = Math.max(
                absoluteThreshold - Math.max(NEAR_THRESHOLD_DELTA - adjustments.thresholdRelief(), 0.04),
                MIN_LIVE_ABSOLUTE_THRESHOLD);
        boolean nearThresholdEligible = bestScore >= nearThreshold;
        double nearConfidenceRequirement = Math.max(0.12,
                NEAR_THRESHOLD_CONFIDENCE + adjustments.confidenceBoost() * 0.5
                        - (adjustments.borderlineQuality() ? 0.04 : 0.0));

        if (!absoluteThresholdPass && nearThresholdEligible && absoluteMarginPass
                && relativeMarginPct >= requiredMarginPct * NEAR_THRESHOLD_MARGIN_FACTOR
                && combinedConfidence >= nearConfidenceRequirement) {
            return RecognitionDecision.accept(
                    profile.displayLabel(),
                    bestScore,
                    combinedConfidence,
                    absoluteMargin,
                    relativeMarginPct,
                    requiredMarginPct,
                    "Near-threshold margin match",
                    adjustments);
        }

        if (rawScorePass && absoluteThresholdPass && consistent
                && matchCount >= minimumConsistencyCount && combinedConfidence >= 0.10) {
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
                    reason,
                    adjustments);
        }

        if (rawScorePass && discriminativePass && absoluteMarginPass) {
            return RecognitionDecision.accept(
                    profile.displayLabel(),
                    bestScore,
                    combinedConfidence,
                    absoluteMargin,
                    relativeMarginPct,
                    requiredMarginPct,
                    "Discriminative match (low negative evidence)",
                    adjustments);
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
                    "Relative evidence override",
                    adjustments);
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
                reason,
                adjustments);
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
        private final double thresholdRelief;
        private final double marginRelaxation;
        private final double scaleRatio;
        private final double confidenceAdjustment;
        private final boolean borderlineQuality;

        private RecognitionDecision(boolean accepted, String label, double rawScore, double confidence,
                double margin, double relativeMarginPct, double requiredMarginPct, String reason,
                RecognitionConfidenceCalibrator.Calibration calibration) {
            this.accepted = accepted;
            this.label = label;
            this.rawScore = rawScore;
            this.confidence = confidence;
            this.margin = margin;
            this.relativeMarginPct = relativeMarginPct;
            this.requiredMarginPct = requiredMarginPct;
            this.reason = reason;
            RecognitionConfidenceCalibrator.Calibration source = calibration != null
                    ? calibration
                    : RecognitionConfidenceCalibrator.Calibration.noAdjustment();
            this.thresholdRelief = source.thresholdRelief();
            this.marginRelaxation = source.marginRelaxation();
            this.scaleRatio = source.normalizedScale();
            this.confidenceAdjustment = source.confidenceBoost();
            this.borderlineQuality = source.borderlineQuality();
        }

        static RecognitionDecision accept(String label, double rawScore, double confidence,
                double margin, double relativeMarginPct, double requiredMarginPct, String reason,
                RecognitionConfidenceCalibrator.Calibration calibration) {
            return new RecognitionDecision(true, label, rawScore, confidence, margin, relativeMarginPct,
                    requiredMarginPct, reason, calibration);
        }

        static RecognitionDecision reject(String label, double rawScore, double confidence,
                double margin, double relativeMarginPct, double requiredMarginPct, String reason,
                RecognitionConfidenceCalibrator.Calibration calibration) {
            return new RecognitionDecision(false, label, rawScore, confidence, margin, relativeMarginPct,
                    requiredMarginPct, reason, calibration);
        }

        static RecognitionDecision rejected(String reason) {
            return new RecognitionDecision(false, "unknown", 0.0, 0.0, 0.0, 0.0, 0.0, reason,
                    RecognitionConfidenceCalibrator.Calibration.noAdjustment());
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

        double thresholdRelief() {
            return thresholdRelief;
        }

        double marginRelaxation() {
            return marginRelaxation;
        }

        double scaleRatio() {
            return scaleRatio;
        }

        double confidenceAdjustment() {
            return confidenceAdjustment;
        }

        boolean borderlineQuality() {
            return borderlineQuality;
        }
    }
}
