package model;

import java.util.ArrayList;
import java.util.List;
import org.opencv.core.Rect;

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






