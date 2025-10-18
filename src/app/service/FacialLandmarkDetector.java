package app.service;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class FacialLandmarkDetector {

    private CascadeClassifier eyeDetector;
    private CascadeClassifier noseDetector;
    private CascadeClassifier mouthDetector;
    private boolean isInitialized = false;

    // Quality thresholds
    private static final double MIN_EYE_DISTANCE = 30.0; // Minimum pixels between eyes
    private static final double MAX_YAW_ANGLE = 20.0; // Maximum head rotation (degrees) - reduced from 25.0
    private static final double MAX_PITCH_ANGLE = 20.0; // Maximum head tilt (degrees)
    private static final double MIN_SYMMETRY_SCORE = 0.7; // Minimum face symmetry (0-1)

    public FacialLandmarkDetector() {
        initializeLandmarkDetector();
    }

    /**
     * Initialize cascade-based feature detectors.
     */
    private void initializeLandmarkDetector() {
        try {
            // Load eye detector (required)
            String eyePath = "data/resources/haarcascade_eye.xml";
            if (new java.io.File(eyePath).exists()) {
                eyeDetector = new CascadeClassifier(eyePath);
                if (!eyeDetector.empty()) {
                    isInitialized = true;
                    System.out.println("✓ Facial landmark detector (cascade-based) loaded successfully");
                }
            }

            // Try to load nose detector (optional)
            String nosePath = "data/resources/haarcascade_mcs_nose.xml";
            if (new java.io.File(nosePath).exists()) {
                noseDetector = new CascadeClassifier(nosePath);
                if (noseDetector.empty())
                    noseDetector = null;
            }

            // Try to load mouth detector (optional)
            String mouthPath = "data/resources/haarcascade_mcs_mouth.xml";
            if (new java.io.File(mouthPath).exists()) {
                mouthDetector = new CascadeClassifier(mouthPath);
                if (mouthDetector.empty())
                    mouthDetector = null;
            }

            if (!isInitialized) {
                System.err.println("✗ Eye detector not found - landmark detection unavailable");
            }
        } catch (Exception e) {
            System.err.println("✗ Failed to load facial landmark detector: " + e.getMessage());
            e.printStackTrace();
            isInitialized = false;
        }
    }

    /**
     * Detect facial landmarks on a face region.
     * 
     * @param faceImage The face image (grayscale or color)
     * @param faceRect  The bounding box of the face in the image
     * @return LandmarkResult containing detected landmarks and quality metrics
     */
    public LandmarkResult detectLandmarks(Mat faceImage, Rect faceRect) {
        if (!isInitialized) {
            return new LandmarkResult(false, "Landmark detector not initialized", null);
        }

        if (faceImage == null || faceImage.empty()) {
            return new LandmarkResult(false, "Empty face image", null);
        }

        try {
            // Convert to grayscale if needed
            Mat gray = new Mat();
            if (faceImage.channels() > 1) {
                Imgproc.cvtColor(faceImage, gray, Imgproc.COLOR_BGR2GRAY);
            } else {
                gray = faceImage.clone();
            }

            // Enhance image for better detection
            Imgproc.equalizeHist(gray, gray);

            // Detect eyes
            MatOfRect eyes = new MatOfRect();
            eyeDetector.detectMultiScale(gray, eyes, 1.1, 3, 0,
                    new Size(20, 20), new Size(gray.width() / 3, gray.height() / 3));

            Rect[] eyeArray = eyes.toArray();

            if (eyeArray.length < 2) {
                gray.release();
                eyes.release();
                return new LandmarkResult(false, "Could not detect both eyes", null);
            }

            // Sort eyes by x-coordinate to get left and right
            Arrays.sort(eyeArray, Comparator.comparingInt(rect -> rect.x));

            Rect leftEye = eyeArray[0];
            Rect rightEye = eyeArray[eyeArray.length - 1];

            // Calculate eye centers
            Point leftEyeCenter = new Point(
                    leftEye.x + leftEye.width / 2.0,
                    leftEye.y + leftEye.height / 2.0);
            Point rightEyeCenter = new Point(
                    rightEye.x + rightEye.width / 2.0,
                    rightEye.y + rightEye.height / 2.0);

            // Try to detect nose (optional)
            Point nosePoint = null;
            if (noseDetector != null) {
                MatOfRect noses = new MatOfRect();
                // Look for nose in lower half of face
                Rect noseRegion = new Rect(
                        0, gray.height() / 3,
                        gray.width(), gray.height() * 2 / 3);
                Mat noseROI = new Mat(gray, noseRegion);
                noseDetector.detectMultiScale(noseROI, noses, 1.1, 3, 0,
                        new Size(15, 15), new Size(gray.width() / 3, gray.height() / 3));

                Rect[] noseArray = noses.toArray();
                if (noseArray.length > 0) {
                    Rect nose = noseArray[0];
                    nosePoint = new Point(
                            nose.x + nose.width / 2.0,
                            noseRegion.y + nose.y + nose.height / 2.0);
                }
                noseROI.release();
                noses.release();
            }

            // Try to detect mouth (optional)
            Point mouthPoint = null;
            if (mouthDetector != null) {
                MatOfRect mouths = new MatOfRect();
                // Look for mouth in bottom half of face
                Rect mouthRegion = new Rect(
                        0, gray.height() / 2,
                        gray.width(), gray.height() / 2);
                Mat mouthROI = new Mat(gray, mouthRegion);
                mouthDetector.detectMultiScale(mouthROI, mouths, 1.1, 5, 0,
                        new Size(20, 10), new Size(gray.width() / 2, gray.height() / 3));

                Rect[] mouthArray = mouths.toArray();
                if (mouthArray.length > 0) {
                    Rect mouth = mouthArray[0];
                    mouthPoint = new Point(
                            mouth.x + mouth.width / 2.0,
                            mouthRegion.y + mouth.y + mouth.height / 2.0);
                }
                mouthROI.release();
                mouths.release();
            }

            // Create simplified landmarks as Point array
            List<Point> landmarkList = new ArrayList<>();
            landmarkList.add(leftEyeCenter); // 0: left eye
            landmarkList.add(rightEyeCenter); // 1: right eye
            if (nosePoint != null)
                landmarkList.add(nosePoint); // 2: nose (optional)
            if (mouthPoint != null)
                landmarkList.add(mouthPoint); // 3: mouth (optional)

            Point[] landmarks = landmarkList.toArray(new Point[0]);

            // Calculate quality metrics
            LandmarkQuality quality = calculateLandmarkQuality(landmarks, gray.size());

            gray.release();
            eyes.release();

            return new LandmarkResult(true, "Landmarks detected successfully",
                    new FacialLandmarks(landmarks, quality));

        } catch (Exception e) {
            System.err.println("Landmark detection error: " + e.getMessage());
            e.printStackTrace();
            return new LandmarkResult(false, "Detection error: " + e.getMessage(), null);
        }
    }

    /**
     * Calculate quality metrics based on detected landmarks.
     */
    private LandmarkQuality calculateLandmarkQuality(Point[] landmarks, Size imageSize) {
        if (landmarks.length < 2) {
            return new LandmarkQuality(false, 0, 0, 0, 0, 0, 0,
                    "Insufficient landmarks detected");
        }

        Point leftEye = landmarks[0];
        Point rightEye = landmarks[1];

        // Calculate eye distance
        double eyeDistance = euclideanDistance(leftEye, rightEye);

        // Calculate face pose angles
        PoseAngles pose = estimatePoseAngles(landmarks, imageSize);

        // Calculate face symmetry
        double symmetry = calculateSymmetryScore(landmarks, imageSize);

        // Determine overall quality
        boolean isGoodQuality = eyeDistance >= MIN_EYE_DISTANCE &&
                Math.abs(pose.yaw) <= MAX_YAW_ANGLE &&
                Math.abs(pose.pitch) <= MAX_PITCH_ANGLE &&
                symmetry >= MIN_SYMMETRY_SCORE;

        // Calculate overall quality score (0-100)
        double qualityScore = calculateOverallQualityScore(eyeDistance, pose, symmetry);

        // Generate feedback message
        String feedback = generateQualityFeedback(eyeDistance, pose, symmetry);

        return new LandmarkQuality(isGoodQuality, qualityScore, eyeDistance,
                pose.yaw, pose.pitch, pose.roll, symmetry, feedback);
    }

    /**
     * Calculate Euclidean distance between two points.
     */
    private double euclideanDistance(Point p1, Point p2) {
        double dx = p2.x - p1.x;
        double dy = p2.y - p1.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Estimate face pose angles from landmark points.
     */
    private PoseAngles estimatePoseAngles(Point[] landmarks, Size imageSize) {
        Point leftEye = landmarks[0];
        Point rightEye = landmarks[1];

        // Calculate roll (head rotation around z-axis)
        double dx = rightEye.x - leftEye.x;
        double dy = rightEye.y - leftEye.y;
        double roll = Math.toDegrees(Math.atan2(dy, dx));

        // Estimate yaw (left-right rotation) from eye positions
        double eyeCenterX = (leftEye.x + rightEye.x) / 2.0;
        double imageCenterX = imageSize.width / 2.0;
        double yaw = ((eyeCenterX - imageCenterX) / imageCenterX) * 30.0; // Scale to degrees

        // Estimate pitch (up-down tilt) from eye height
        double eyeCenterY = (leftEye.y + rightEye.y) / 2.0;
        double expectedEyeY = imageSize.height * 0.4; // Eyes typically at 40% height
        double pitch = ((eyeCenterY - expectedEyeY) / imageSize.height) * 30.0;

        // If we have nose/mouth, refine estimates
        if (landmarks.length >= 3) {
            Point nose = landmarks[2];
            double noseOffsetX = nose.x - eyeCenterX;
            yaw = Math.toDegrees(Math.atan(noseOffsetX / eyeCenterX)) * 2.0;
        }

        return new PoseAngles(yaw, pitch, roll);
    }

    /**
     * Calculate face symmetry score (0-1).
     */
    private double calculateSymmetryScore(Point[] landmarks, Size imageSize) {
        Point leftEye = landmarks[0];
        Point rightEye = landmarks[1];

        double centerX = imageSize.width / 2.0;

        // Check if eyes are symmetric around center
        double leftDist = Math.abs(leftEye.x - centerX);
        double rightDist = Math.abs(rightEye.x - centerX);

        double eyeSymmetry = 1.0 - Math.abs(leftDist - rightDist) / Math.max(leftDist, rightDist);

        // Check if eyes are at same height
        double heightDiff = Math.abs(leftEye.y - rightEye.y);
        double eyeDistance = euclideanDistance(leftEye, rightEye);
        double heightSymmetry = 1.0 - (heightDiff / eyeDistance);

        return (eyeSymmetry + heightSymmetry) / 2.0;
    }

    /**
     * Calculate overall quality score (0-100).
     */
    private double calculateOverallQualityScore(double eyeDistance, PoseAngles pose, double symmetry) {
        double eyeScore = Math.min(100, (eyeDistance / MIN_EYE_DISTANCE) * 25.0);
        double yawScore = Math.max(0, 25.0 - (Math.abs(pose.yaw) / MAX_YAW_ANGLE) * 25.0);
        double pitchScore = Math.max(0, 25.0 - (Math.abs(pose.pitch) / MAX_PITCH_ANGLE) * 25.0);
        double symmetryScore = symmetry * 25.0;

        return eyeScore + yawScore + pitchScore + symmetryScore;
    }

    /**
     * Generate human-readable feedback about landmark quality.
     */
    private String generateQualityFeedback(double eyeDistance, PoseAngles pose, double symmetry) {
        List<String> issues = new ArrayList<>();

        if (eyeDistance < MIN_EYE_DISTANCE) {
            issues.add("Face too small - move closer to camera");
        }

        if (Math.abs(pose.yaw) > MAX_YAW_ANGLE) {
            if (pose.yaw > 0) {
                issues.add("Face turned too far right - look straight at camera");
            } else {
                issues.add("Face turned too far left - look straight at camera");
            }
        }

        if (Math.abs(pose.pitch) > MAX_PITCH_ANGLE) {
            if (pose.pitch > 0) {
                issues.add("Head tilted too far down - look straight ahead");
            } else {
                issues.add("Head tilted too far up - look straight ahead");
            }
        }

        if (symmetry < MIN_SYMMETRY_SCORE) {
            issues.add("Face not centered - adjust position");
        }

        if (issues.isEmpty()) {
            return "Excellent face pose - ready for capture";
        } else {
            return String.join("; ", issues);
        }
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Result class for landmark detection operations.
     */
    public static class LandmarkResult {
        private final boolean success;
        private final String message;
        private final FacialLandmarks landmarks;

        public LandmarkResult(boolean success, String message, FacialLandmarks landmarks) {
            this.success = success;
            this.message = message;
            this.landmarks = landmarks;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public FacialLandmarks getLandmarks() {
            return landmarks;
        }
    }

    /**
     * Container for detected facial landmarks and quality metrics.
     */
    public static class FacialLandmarks {
        private final Point[] points;
        private final LandmarkQuality quality;

        public FacialLandmarks(Point[] points, LandmarkQuality quality) {
            this.points = points;
            this.quality = quality;
        }

        public Point[] getPoints() {
            return points;
        }

        public LandmarkQuality getQuality() {
            return quality;
        }
    }

    /**
     * Quality metrics for detected landmarks.
     */
    public static class LandmarkQuality {
        private final boolean isGoodQuality;
        private final double overallScore;
        private final double eyeDistance;
        private final double yawAngle;
        private final double pitchAngle;
        private final double rollAngle;
        private final double symmetryScore;
        private final String feedback;

        public LandmarkQuality(boolean isGoodQuality, double overallScore, double eyeDistance,
                double yawAngle, double pitchAngle, double rollAngle,
                double symmetryScore, String feedback) {
            this.isGoodQuality = isGoodQuality;
            this.overallScore = overallScore;
            this.eyeDistance = eyeDistance;
            this.yawAngle = yawAngle;
            this.pitchAngle = pitchAngle;
            this.rollAngle = rollAngle;
            this.symmetryScore = symmetryScore;
            this.feedback = feedback;
        }

        public boolean isGoodQuality() {
            return isGoodQuality;
        }

        public double getOverallScore() {
            return overallScore;
        }

        public double getEyeDistance() {
            return eyeDistance;
        }

        public double getYawAngle() {
            return yawAngle;
        }

        public double getPitchAngle() {
            return pitchAngle;
        }

        public double getRollAngle() {
            return rollAngle;
        }

        public double getSymmetryScore() {
            return symmetryScore;
        }

        public String getFeedback() {
            return feedback;
        }

        @Override
        public String toString() {
            return String.format("Quality: %s (Score: %.1f%%), Eye Distance: %.1f, " +
                    "Yaw: %.1f°, Pitch: %.1f°, Roll: %.1f°, Symmetry: %.2f - %s",
                    isGoodQuality ? "GOOD" : "POOR", overallScore, eyeDistance,
                    yawAngle, pitchAngle, rollAngle, symmetryScore, feedback);
        }
    }

    /**
     * Container for face pose angles.
     */
    private static class PoseAngles {
        final double yaw; // Left-right rotation
        final double pitch; // Up-down tilt
        final double roll; // Head rotation

        PoseAngles(double yaw, double pitch, double roll) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
        }
    }
}
