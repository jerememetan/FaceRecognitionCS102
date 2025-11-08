package model;

import java.util.List;
import service.embedding.FaceEmbeddingGenerator;

public class FaceCaptureResult {
    private boolean success;
    private String message;
    private List<String> capturedImages;
    private FaceEmbeddingGenerator.BatchProcessingResult batchProcessingResult;

    public FaceCaptureResult(boolean success, String message, List<String> capturedImages,
            FaceEmbeddingGenerator.BatchProcessingResult batchProcessingResult) {
        this.success = success;
        this.message = message;
        this.capturedImages = capturedImages;
        this.batchProcessingResult = batchProcessingResult;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getCapturedImages() {
        return capturedImages;
    }

    public FaceEmbeddingGenerator.BatchProcessingResult getBatchProcessingResult() {
        return batchProcessingResult;
    }
}






