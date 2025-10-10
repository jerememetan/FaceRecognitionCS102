package app.model;

import java.util.*;
import java.io.*;

public class FaceData {
    private String studentId;
    private List<FaceImage> images;
    private boolean isValid;

    public FaceData(String studentId) {
        this.studentId = studentId;
        this.images = new ArrayList<>();
        this.isValid = false;

        File folder = new File(getFolderPath());
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles(
                    (dir, name) -> name.toLowerCase().endsWith(".jpg"));
            if (files != null) {
                for (File file : files) {
                    FaceImage faceImage = new FaceImage(file.getAbsolutePath(), null);
                    images.add(faceImage);
                }
            }
        }
    }

    public boolean addImage(FaceImage faceImage) {
        if (images.size() >= 30) {
            return false;
        }
        images.add(faceImage);
        return true;
    }

    public boolean validateImages() {
        if (images.size() >= 10) {
            this.isValid = true;
        } else {
            this.isValid = false;
        }
        return this.isValid;
    }

    public String getFolderPath() {
        return "data/facedata/" + this.studentId + "/";
    }

    public String getStudentId() {
        return studentId;
    }

    public List<FaceImage> getImages() {
        return images;
    }

    public boolean isValid() {
        return isValid;
    }

    public int getImageCount() {
        return images.size();
    }

    public boolean hasImages() {
        return images != null && !images.isEmpty();
    }

    public void clearImages() {
        images.clear();
        isValid = false;
    }

    public boolean removeImage(FaceImage image) {
        boolean removed = images.remove(image);
        if (removed) {
            validateImages();
        }
        return removed;
    }

    @Override
    public String toString() {
        return String.format("FaceData{studentId='%s', imageCount=%d, isValid=%b}",
                studentId, images.size(), isValid);
    }
}
