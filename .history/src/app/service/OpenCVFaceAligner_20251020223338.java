package app.service;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.face.Face;
import org.opencv.face.Facemark;

import org.opencv.objdetect.CascadeClassifier;
import java.util.ArrayList;
import java.util.List;

public class OpenCVFaceAligner {
    private Facemark facemark;
    private boolean isInitialized = false;
    // Target eye positions for alignment (normalized 0-1)
    private static final double LEFT_EYE_X = 0.35;
    private static final double LEFT_EYE_Y = 0.35;
    private static final double RIGHT_EYE_X = 0.65;
    private static final double RIGHT_EYE_Y = 0.35;

public OpenCVFaceAligner() {
try {
String modelPath = "data/resources/lbfmodel.yaml";
if (new java.io.File(modelPath).exists()) {
// Create FacemarkLBF instance
facemark = Face.createFacemarkLBF();
facemark.loadModel(modelPath);
isInitialized = true;
System.out.println("✅ OpenCV FacemarkLBF loaded successfully");
} else {
System.err.println("❌ LBF model not found: " + modelPath);
isInitialized = false;
}
} catch (Exception e) {
System.err.println("❌ Failed to load OpenCV FacemarkLBF: " + e.getMessage())
System.err.println(" Make sure OpenCV contrib modules are included");
isInitialized = false;
}
}

public Mat alignFace(Mat faceImage, Rect faceRect) {
if (!isInitialized || faceImage == null || faceImage.empty()) {
return null;
}
try {
// Convert to grayscale for landmark detection
Mat gray = new Mat();
if (faceImage.channels() > 1) {
Imgproc.cvtColor(faceImage, gray, Imgproc.COLOR_BGR2GRAY);
} else {
gray = faceImage.clone();
}
// Prepare face rectangles for landmark detection
MatOfRect faces = new MatOfRect();
if (faceRect != null) {
faces.fromList(java.util.Arrays.asList(faceRect));
} else {
// Use entire image as face region
faces.fromList(java.util.Arrays.asList(
new Rect(0, 0, faceImage.width(), faceImage.height())
));
}
// Detect facial landmarks (68 points)
List<MatOfPoint2f> landmarks = new ArrayList<>();
boolean success = facemark.fit(gray, faces, landmarks);
gray.release();
faces.release();
if (!success || landmarks.isEmpty()) {
System.err.println("⚠️ Facial landmark detection failed");
return null;
}
// Extract landmark points
MatOfPoint2f landmarksMat = landmarks.get(0);
Point[] points = landmarksMat.toArray();
landmarksMat.release();
if (points.length &lt; 68) {
System.err.println("⚠️ Insufficient landmarks detected: " + points.length);
return null;
}
// Calculate eye centers
// Left eye: landmarks 36-41 (indices in 0-based array)
// Right eye: landmarks 42-47
Point leftEye = calculateEyeCenter(points, 36, 41);
Point rightEye = calculateEyeCenter(points, 42, 47);
if (leftEye == null || rightEye == null) {
System.err.println("⚠️ Could not calculate eye centers");
return null
}
// Calculate transformation matrix for alignment
Mat alignmentMatrix = getAlignmentTransform(leftEye, rightEye, faceImage.size());
// Apply affine transformation
Mat aligned = new Mat();
Imgproc.warpAffine(faceImage, aligned, alignmentMatrix, faceImage.size(),
Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE);
alignmentMatrix.release();
return aligned;
} catch (Exception e) {
System.err.println("❌ Face alignment failed: " + e.getMessage());
e.printStackTrace();
return null;
}
}

private Point calculateEyeCenter(Point[] points, int start, int end) {
try {
double sumX = 0, sumY = 0;
int count = 0;
for (int i = start; i <= end && i &lt; points.length; i++) {
sumX += points[i].x;
sumY += points[i].y;
count++;
}
if (count == 0) return null;
return new Point(sumX / count, sumY / count);
} catch (Exception e) {
return null;
}
}

    private Mat getAlignmentTransform(Point leftEye, Point rightEye, Size imageSize) {
        // Calculate angle between eyes
        double dx = rightEye.x - leftEye.x;
        double dy = rightEye.y - leftEye.y;
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        // Calculate current eye center
        Point eyesCenter = new Point(
                (leftEye.x + rightEye.x) / 2.0,
                (leftEye.y + rightEye.y) / 2.0);
        // Get rotation matrix to align eyes horizontally
        Mat M = Imgproc.getRotationMatrix2D(eyesCenter, angle, 1.0);
        // Calculate target eye positions
        double targetCenterX = imageSize.width * 0.5;
        double targetCenterY = imageSize.height * LEFT_EYE_Y; // Center eyes at y=0.35
        // Add translation to move eyes to target position
        double tx = targetCenterX - eyesCenter.x;
        double ty = targetCenterY - eyesCenter.y;
        double[] mData = new double[6];
        M.get(0, 0, mData);
        mData[2] += tx; // Update translation x
        M.put(0, 0, mData);
        M.get(1, 0, mData);
        mData[2] += ty; // Update translation y
        M.put(1, 0, mData);
        return M;
    }

    /**
     * Check if face aligner is ready to use.
     */
    public boolean isInitialized() {
        return isInitialized;
    }


    public void release() {
        if (facemark != null) {
    
            facemark = null;
        }
    }
}
