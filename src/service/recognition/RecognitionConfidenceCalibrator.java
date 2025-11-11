package service.recognition;

import config.AppConfig;

/**
 * Applies distance and quality-based compensations to recognition thresholds so
 * the system remains stable when the subject is closer or further away from
 * the camera.
 */
final class RecognitionConfidenceCalibrator {

    private static final double MAX_THRESHOLD_RELIEF = 0.18;
    private static final double MIN_MARGIN_RELAXATION = 0.55;
    private static final double MAX_CONFIDENCE_BOOST = 0.22;
    private static final double MAX_DISCRIMINATIVE_BOOST = 0.20;

    Calibration calibrate(RecognitionScorer.ScoreResult scores, RecognitionFrameMetrics metrics) {
        if (scores == null || metrics == null || scores.isEmpty()) {
            return Calibration.noAdjustment();
        }

        double baselineWidth = Math.max(64.0, AppConfig.getInstance().getRecognitionMinFaceWidthPx());
        double normalizedScale = clamp(metrics.normalizedScale(baselineWidth), 0.35, 1.75);
        double faceCoverage = clamp(metrics.paddedAreaRatio(), 0.0, 1.0);

        double farFactor = clamp(1.0 - normalizedScale, 0.0, 0.9);
        double nearFactor = clamp(normalizedScale - 1.35, 0.0, 0.6);

        double qualityScore = metrics.qualityScore();
        double qualityRelief = 0.0;
        if (qualityScore > 0.0) {
            double scaledQuality = clamp(qualityScore, 0.0, 100.0);
            if (scaledQuality < 65.0) {
                qualityRelief = (65.0 - scaledQuality) / 220.0;
            }
        }
        if (metrics.borderlineQuality()) {
            qualityRelief += 0.08;
        }

        double thresholdRelief = clamp(farFactor * 0.22 + qualityRelief * 0.18, 0.0, MAX_THRESHOLD_RELIEF);
        double marginRelaxation = clamp(1.0 - (farFactor * 0.35) - (metrics.borderlineQuality() ? 0.12 : 0.0),
                MIN_MARGIN_RELAXATION, 1.0);
        double confidenceBoost = clamp(farFactor * 0.25 + qualityRelief * 0.20 - nearFactor * 0.15,
                -0.18, MAX_CONFIDENCE_BOOST);
        double discriminativeBoost = clamp(farFactor * 0.30 + qualityRelief * 0.18 - nearFactor * 0.10,
                -0.15, MAX_DISCRIMINATIVE_BOOST);

        boolean adjustmentsApplied = thresholdRelief > 1e-6
                || Math.abs(1.0 - marginRelaxation) > 1e-6
                || Math.abs(confidenceBoost) > 1e-6
                || Math.abs(discriminativeBoost) > 1e-6;

        StringBuilder notes = new StringBuilder();
        if (adjustmentsApplied) {
            notes.append(String.format("scale=%.2f", normalizedScale));
            if (farFactor > 0.0) {
                notes.append(String.format(", farFactor=%.2f", farFactor));
            }
            if (nearFactor > 0.0) {
                notes.append(String.format(", nearFactor=%.2f", nearFactor));
            }
            if (qualityRelief > 0.0) {
                notes.append(String.format(", qualityRelief=%.2f", qualityRelief));
            }
        }

        return new Calibration(
                normalizedScale,
                faceCoverage,
                thresholdRelief,
                marginRelaxation,
                confidenceBoost,
                discriminativeBoost,
                metrics.borderlineQuality(),
                qualityScore,
                adjustmentsApplied,
                notes.toString());
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    static final class Calibration {
        private final double normalizedScale;
        private final double faceCoverage;
        private final double thresholdRelief;
        private final double marginRelaxation;
        private final double confidenceBoost;
        private final double discriminativeBoost;
        private final boolean borderlineQuality;
        private final double qualityScore;
        private final boolean adjusted;
        private final String notes;

        Calibration(double normalizedScale,
                double faceCoverage,
                double thresholdRelief,
                double marginRelaxation,
                double confidenceBoost,
                double discriminativeBoost,
                boolean borderlineQuality,
                double qualityScore,
                boolean adjusted,
                String notes) {
            this.normalizedScale = normalizedScale;
            this.faceCoverage = faceCoverage;
            this.thresholdRelief = thresholdRelief;
            this.marginRelaxation = marginRelaxation;
            this.confidenceBoost = confidenceBoost;
            this.discriminativeBoost = discriminativeBoost;
            this.borderlineQuality = borderlineQuality;
            this.qualityScore = qualityScore;
            this.adjusted = adjusted;
            this.notes = notes;
        }

        static Calibration noAdjustment() {
            return new Calibration(1.0, 0.0, 0.0, 1.0, 0.0, 0.0, false, 0.0, false, "");
        }

        double normalizedScale() {
            return normalizedScale;
        }

        double faceCoverage() {
            return faceCoverage;
        }

        double thresholdRelief() {
            return thresholdRelief;
        }

        double marginRelaxation() {
            return marginRelaxation;
        }

        double confidenceBoost() {
            return confidenceBoost;
        }

        double discriminativeBoost() {
            return discriminativeBoost;
        }

        boolean borderlineQuality() {
            return borderlineQuality;
        }

        double qualityScore() {
            return qualityScore;
        }

        boolean adjusted() {
            return adjusted;
        }

        String notes() {
            return notes;
        }
    }
}
