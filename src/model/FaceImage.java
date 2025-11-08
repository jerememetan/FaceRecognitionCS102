package model;

import java.time.*;

public class FaceImage {
    private String imagePath;
    private byte[] embedding;
    private double qualityScore;
    private LocalDateTime timestamp;

    public FaceImage(String imagePath, byte[] embedding){
        this.imagePath = imagePath;
        this.embedding = embedding;
        this.timestamp = LocalDateTime.now();
    }

    public void setQualityScore(double qualityScore) {
        this.qualityScore = qualityScore;
    }

    public String getImagePath() {
        return imagePath;
    }

    public byte[] getEmbedding() {
        return embedding;
    }

    public double getQualityScore() {
        return qualityScore;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    
}







