package app.model;

import ConfigurationAndLogging.AppConfig;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FaceData {
    private String studentId;
    private String studentName;
    private List<FaceImage> images;
    private boolean isValid;
    private Path studentFolder;

    public FaceData(String studentId, String studentName) {
        this.studentId = studentId;
        this.studentName = studentName != null ? studentName : "";
        this.images = new ArrayList<>();
        this.isValid = false;

        initializeStudentFolder();
        loadExistingImages();
    }

    private void initializeStudentFolder() {
        String basePath = AppConfig.getInstance().getDatabaseStoragePath();
        if (basePath == null || basePath.trim().isEmpty()) {
            basePath = ".";
        }

        String folderName = buildFolderName(studentId, studentName);
        this.studentFolder = Paths.get(basePath).resolve(folderName);
    }

    private void loadExistingImages() {
        File folder = studentFolder.toFile();
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

    private String buildFolderName(String id, String name) {
        String safeId = id != null ? id.trim().replaceAll("[\\\\/:*?\"<>|]", "") : "";
        String safeName = name != null ? name.trim().replaceAll("[\\\\/:*?\"<>|]", "") : "";

        StringBuilder combined = new StringBuilder();
        if (!safeId.isEmpty()) {
            combined.append(safeId);
        }
        if (!safeName.isEmpty()) {
            if (combined.length() > 0) {
                combined.append("_");
            }
            combined.append(safeName);
        }

        String result = combined.toString().trim();
        return result.isEmpty() ? "unknown" : result;
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
        return studentFolder.toAbsolutePath().toString();
    }

    public String getStudentId() {
        return studentId;
    }

    public String getStudentName() {
        return studentName;
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
        return String.format("FaceData{studentId='%s', studentName='%s', folder='%s', imageCount=%d, isValid=%b}",
                studentId, studentName, studentFolder, images.size(), isValid);
    }
}
