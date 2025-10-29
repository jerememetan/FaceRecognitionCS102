package app.service.recognition;

import java.util.Collections;
import java.util.List;

/**
 * Immutable view of a person's recognition data (stored embeddings, derived
 * statistics, and decision thresholds).
 */
final class RecognitionProfile {

    private final String folderPath;
    private final String displayLabel;
    private final List<byte[]> embeddings;
    private final double[] centroid;
    private final double tightness;
    private final double absoluteThreshold;
    private final double relativeMargin;
    private final double standardDeviation;

    RecognitionProfile(
            String folderPath,
            String displayLabel,
            List<byte[]> embeddings,
            double[] centroid,
            double tightness,
            double absoluteThreshold,
            double relativeMargin,
            double standardDeviation) {
        this.folderPath = folderPath;
        this.displayLabel = displayLabel;
        this.embeddings = embeddings == null ? List.of() : List.copyOf(embeddings);
        this.centroid = centroid;
        this.tightness = tightness;
        this.absoluteThreshold = absoluteThreshold;
        this.relativeMargin = relativeMargin;
        this.standardDeviation = standardDeviation;
    }

    String folderPath() {
        return folderPath;
    }

    String displayLabel() {
        return displayLabel;
    }

    List<byte[]> embeddings() {
        return Collections.unmodifiableList(embeddings);
    }

    double[] centroid() {
        return centroid;
    }

    double tightness() {
        return tightness;
    }

    double absoluteThreshold() {
        return absoluteThreshold;
    }

    double relativeMargin() {
        return relativeMargin;
    }

    double standardDeviation() {
        return standardDeviation;
    }

    boolean hasEmbeddings() {
        return !embeddings.isEmpty();
    }
}
