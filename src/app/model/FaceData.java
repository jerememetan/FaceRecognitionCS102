package app.model;

import ConfigurationAndLogging.AppConfig;
import ConfigurationAndLogging.AppLogger;

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
    private String imageType = AppConfig.getInstance().getRecognitionImageFormat();

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
        Path preferred = Paths.get(basePath).resolve(folderName).toAbsolutePath().normalize();
        this.studentFolder = preferred;
    }

    private void loadExistingImages() {
        if (studentFolder == null) {
            return; 
        }
        File folder = studentFolder.toFile();
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles(
                    (dir, name) -> name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".png"));
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

        if (!safeName.isEmpty()) {
            safeName = safeName.replaceAll("\\s+", "_");
        }

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
        return addOrReplaceImage(faceImage);
    }

    public boolean addOrReplaceImage(FaceImage faceImage) {
        if (faceImage == null || faceImage.getImagePath() == null) {
            return false;
        }

        int existingIndex = indexOfImagePath(faceImage.getImagePath());
        if (existingIndex >= 0) {
            images.set(existingIndex, faceImage);
            validateImages();
            return true; 
        }

        if (images.size() >= 30) {
            return false; 
        }
        images.add(faceImage);
        validateImages();
        return true;
    }

    private int indexOfImagePath(String path) {
        if (path == null) return -1;
        String target = normalizePath(path);
        for (int i = 0; i < images.size(); i++) {
            FaceImage img = images.get(i);
            if (img != null && img.getImagePath() != null) {
                if (normalizePath(img.getImagePath()).equals(target)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String normalizePath(String p) {
        try {
            return new File(p).getAbsolutePath().replace('\\', '/').toLowerCase();
        } catch (Exception e) {
            return p.replace('\\', '/').toLowerCase();
        }
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
        // Ensure we always return a usable path; if somehow uninitialized, fall back to base path with built folder name
        if (studentFolder == null) {
            String basePath = AppConfig.getInstance().getDatabaseStoragePath();
            if (basePath == null || basePath.trim().isEmpty()) {
                basePath = ".";
            }
            String folderName = buildFolderName(studentId, studentName);
            studentFolder = Paths.get(basePath).resolve(folderName).toAbsolutePath().normalize();
        }
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
