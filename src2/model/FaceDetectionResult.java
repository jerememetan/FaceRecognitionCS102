package app.model;

import org.opencv.core.Rect;
import java.util.List;
import java.util.ArrayList;

public class FaceDetectionResult {
    private List<FaceCandidate> faces;

    public FaceDetectionResult(List<FaceCandidate> faces) {
        this.faces = faces != null ? faces : new ArrayList<>();
    }

    public boolean hasValidFace() {
        return !faces.isEmpty();
    }

    public Rect getBestFace() {
        return faces.isEmpty() ? null
                : faces.stream().max((a, b) -> Double.compare(a.confidence, b.confidence))
                        .get().rect;
    }

    public double getBestConfidence() {
        return faces.isEmpty() ? 0.0 : faces.stream().mapToDouble(f -> f.confidence).max().orElse(0.0);
    }

    public double getConfidence() {
        return getBestConfidence();
    }

    public List<FaceCandidate> getFaces() {
        return faces;
    }
}