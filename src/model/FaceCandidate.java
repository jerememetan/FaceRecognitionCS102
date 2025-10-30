package model;

import org.opencv.core.Rect;

public class FaceCandidate {
    public final Rect rect;
    public final double confidence;

    public FaceCandidate(Rect rect, double confidence) {
        this.rect = rect;
        this.confidence = confidence;
    }
}






