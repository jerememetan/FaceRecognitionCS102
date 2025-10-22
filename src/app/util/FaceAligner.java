package app.util;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple face aligner using only Haar cascade eye detection
 * NO external dependencies - uses standard OpenCV only
 */
public class FaceAligner {

    private CascadeClassifier leftEyeDetector;
    private CascadeClassifier rightEyeDetector;
    private boolean isInitialized = false;

    // Target eye positions for 96x96 aligned face
    // Left eye at (30, 35), right eye at (66, 35) - eyes on same horizontal line
    private static final Point LEFT_EYE_TARGET = new Point(30, 35);
    private static final Point RIGHT_EYE_TARGET = new Point(66, 35);
    private static final Size OUTPUT_SIZE = new Size(96, 96);

    // For debugging - enable to see eye detection results
    private boolean debugMode = false;

    public FaceAligner() {
        initialize();
    }

    private void initialize() {
        try {
            // Path to Haar cascades - included with OpenCV
            String leftEyeCascadePath = "data/resources/haarcascade_lefteye_2splits.xml";
            String rightEyeCascadePath = "data/resources/haarcascade_righteye_2splits.xml";

            // Fallback to generic eye cascade if specific ones don't exist
            if (new java.io.File(leftEyeCascadePath).exists() &&
                    new java.io.File(rightEyeCascadePath).exists()) {

                leftEyeDetector = new CascadeClassifier(leftEyeCascadePath);
                rightEyeDetector = new CascadeClassifier(rightEyeCascadePath);

                if (leftEyeDetector.empty() || rightEyeDetector.empty()) {
                    System.err.println("ERROR: Could not load left/right eye cascades");
                    isInitialized = false;
                } else {
                    System.out.println("✓ FaceAligner initialized with separate left/right eye detectors");
                    isInitialized = true;
                }
            } else {
                // Fallback to single eye detector
                String eyeCascadePath = "data/resources/haarcascade_eye.xml";
                leftEyeDetector = new CascadeClassifier(eyeCascadePath);
                rightEyeDetector = leftEyeDetector; // Use same detector for both

                if (leftEyeDetector.empty()) {
                    System.err.println("ERROR: Could not load eye cascade from: " + eyeCascadePath);
                    System.err.println("Make sure haarcascade_eye.xml is in data/resources/");
                    isInitialized = false;
                } else {
                    System.out.println("✓ FaceAligner initialized with fallback eye detector");
                    isInitialized = true;
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to initialize FaceAligner: " + e.getMessage());
            isInitialized = false;
        }
    }

    /**
     * Aligns a face image using separate left/right eye detection with geometric
     * validation
     *
     * @param faceImage Input face ROI (already cropped to face bounding box)
     * @param faceRect  Original face bounding box (for reference)
     * @return Aligned 96x96 face image, or null if alignment fails
     */
    public Mat align(Mat faceImage, Rect faceRect) {
        if (!isInitialized) {
            return heuristicFallbackAlignment(faceImage);
        }

        if (faceImage == null || faceImage.empty()) {
            System.err.println("Empty face image provided to aligner");
            return null;
        }

        try {
            // ✅ FIX: Validate image size BEFORE attempting eye detection
            if (faceImage.width() < 40 || faceImage.height() < 40) {
                if (debugMode) {
                    System.out.println("Face image too small for eye detection (" +
                        faceImage.width() + "x" + faceImage.height() + "), using heuristic alignment");
                }
                return heuristicFallbackAlignment(faceImage);
            }

            // Convert to grayscale for eye detection
            Mat gray = new Mat();
            if (faceImage.channels() == 3) {
                Imgproc.cvtColor(faceImage, gray, Imgproc.COLOR_BGR2GRAY);
            } else {
                gray = faceImage.clone();
            }

            // ✅ FIX: Ensure grayscale conversion succeeded and has correct depth
            if (gray.empty() || gray.depth() != CvType.CV_8U) {
                System.err.println("⚠️ Gray conversion failed or wrong depth");
                gray.release();
                return heuristicFallbackAlignment(faceImage);
            }

            // 1) Detect left and right eyes in constrained halves
            Rect leftHalf = new Rect(0, 0, faceImage.width() / 2, (int) (faceImage.height() * 0.7));
            Rect rightHalf = new Rect(faceImage.width() / 2, 0, faceImage.width() / 2,
                    (int) (faceImage.height() * 0.7));

            // ✅ FIX: Validate half regions are large enough
            if (leftHalf.width < 20 || leftHalf.height < 20 ||
                rightHalf.width < 20 || rightHalf.height < 20) {
                if (debugMode) {
                    System.out.println("Face halves too small for eye detection, using heuristic alignment");
                }
                gray.release();
                return heuristicFallbackAlignment(faceImage);
            }

            Mat grayLeft = new Mat(gray, leftHalf);
            Mat grayRight = new Mat(gray, rightHalf);

            MatOfRect leftEyes = new MatOfRect();
            MatOfRect rightEyes = new MatOfRect();

            // ✅ FIX: Wrap detectMultiScale in try-catch to handle edge cases gracefully
            try {
                leftEyeDetector.detectMultiScale(grayLeft, leftEyes, 1.05, 3, 0,
                        new Size(15, 15), new Size(100, 100));
            } catch (Exception e) {
                if (debugMode) {
                    System.out.println("Left eye detection failed: " + e.getMessage());
                }
            }

            try {
                rightEyeDetector.detectMultiScale(grayRight, rightEyes, 1.05, 3, 0,
                        new Size(15, 15), new Size(100, 100));
            } catch (Exception e) {
                if (debugMode) {
                    System.out.println("Right eye detection failed: " + e.getMessage());
                }
            }

            // Pick the most central candidate in each half
            Point leftEye = pickBestEyeCenter(leftEyes.toArray(), leftHalf);
            Point rightEye = pickBestEyeCenter(rightEyes.toArray(), rightHalf);

            // Clean up
            grayLeft.release();
            grayRight.release();
            leftEyes.release();
            rightEyes.release();
            gray.release();

            // 2) Validate geometry
            if (leftEye != null && rightEye != null) {
                double dx = rightEye.x - leftEye.x;
                double dy = rightEye.y - leftEye.y;
                double angle = Math.toDegrees(Math.atan2(dy, dx));
                double eyeDist = Math.hypot(dx, dy);
                double distRatio = eyeDist / faceImage.width();
                double vertRatio = Math.abs(dy) / faceImage.height();

                boolean geometryValid = Math.abs(angle) <= 15.0 &&
                        vertRatio <= 0.10 &&
                        distRatio >= 0.35 && distRatio <= 0.60 &&
                        leftEye.x < rightEye.x;

                if (geometryValid) {
                    if (debugMode) {
                        System.out.println("Eyes validated: Left=" + leftEye + ", Right=" + rightEye +
                                ", angle=" + String.format("%.1f", angle) +
                                ", distRatio=" + String.format("%.2f", distRatio));
                    }
                    return alignUsingEyes(faceImage, leftEye, rightEye);
                } else {
                    if (debugMode) {
                        System.out.println("Eye geometry invalid: angle=" + String.format("%.1f", angle) +
                                ", vertRatio=" + String.format("%.2f", vertRatio) +
                                ", distRatio=" + String.format("%.2f", distRatio));
                    }
                }
            } else {
                if (debugMode) {
                    System.out.println("Could not detect eyes in both halves");
                }
            }

            // Use fallback if eye detection/validation failed
            return heuristicFallbackAlignment(faceImage);

        } catch (Exception e) {
            
            if (debugMode) {
                System.err.println("Eye detection exception: " + e.getMessage());
            }
            return heuristicFallbackAlignment(faceImage);
        }
    }

    /**
     * Performs alignment given detected eye positions
     */
    private Mat alignUsingEyes(Mat faceImage, Point leftEye, Point rightEye) {
        try {
            // Use 3-point affine transformation (more robust than manual calculation)

            // Calculate third point (nose position estimate)
            Point noseEstimate = new Point(
                    (leftEye.x + rightEye.x) / 2.0,
                    (leftEye.y + rightEye.y) / 2.0 +
                            Math.abs(rightEye.x - leftEye.x) * 0.4 // Nose below eye midpoint
            );

            // Source points (detected landmarks)
            MatOfPoint2f srcPoints = new MatOfPoint2f(
                    leftEye,
                    rightEye,
                    noseEstimate);

            // Destination points (target positions in 96x96 image)
            MatOfPoint2f dstPoints = new MatOfPoint2f(
                    LEFT_EYE_TARGET,
                    RIGHT_EYE_TARGET,
                    new Point(48, 72) // Nose target position
            );

            // Compute affine transformation (handles rotation, scale, translation
            // correctly)
            Mat transformMatrix = Imgproc.getAffineTransform(srcPoints, dstPoints);

            // Apply transformation
            Mat aligned = new Mat();
            Imgproc.warpAffine(faceImage, aligned, transformMatrix, OUTPUT_SIZE,
                    Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE);

            transformMatrix.release();
            srcPoints.release();
            dstPoints.release();

            return aligned;

        } catch (Exception e) {
            System.err.println("Alignment transformation failed: " + e.getMessage());
            return heuristicFallbackAlignment(faceImage);
        }
    }

    /**
     * Pick the most central eye candidate in a face half
     */
    private Point pickBestEyeCenter(Rect[] eyes, Rect halfRegion) {
        if (eyes == null || eyes.length == 0) {
            return null;
        }

        Point bestCenter = null;
        double bestScore = Double.MAX_VALUE;

        // Calculate center of the half region
        Point halfCenter = new Point(
                halfRegion.x + halfRegion.width / 2.0,
                halfRegion.y + halfRegion.height / 2.0);

        for (Rect eye : eyes) {
            // Calculate eye center (adjust for half-region offset)
            Point eyeCenter = new Point(
                    halfRegion.x + eye.x + eye.width / 2.0,
                    halfRegion.y + eye.y + eye.height / 2.0);

            // Score based on distance from half center and size preference
            double distanceFromCenter = Math.hypot(
                    eyeCenter.x - halfCenter.x,
                    eyeCenter.y - halfCenter.y);

            // Prefer larger eyes, penalize distance from center
            double score = distanceFromCenter / halfRegion.width +
                    (100.0 - eye.width) / 100.0; // Prefer larger eyes

            if (score < bestScore) {
                bestScore = score;
                bestCenter = eyeCenter;
            }
        }

        return bestCenter;
    }

    /**
     * Fallback when eye detection fails - just center crop and resize
     */
    private Mat fallbackAlignment(Mat faceImage) {
        if (faceImage == null || faceImage.empty()) {
            return new Mat();
        }

        try {
            Mat aligned = new Mat();
            Imgproc.resize(faceImage, aligned, OUTPUT_SIZE, 0, 0, Imgproc.INTER_CUBIC);
            return aligned;
        } catch (Exception e) {
            System.err.println("Fallback alignment failed: " + e.getMessage());
            return faceImage.clone();
        }
    }

    /**
     * Heuristic fallback alignment using synthetic eye positions
     * Places "eyes" at canonical positions and applies same transform as real
     * alignment
     */
    private Mat heuristicFallbackAlignment(Mat faceImage) {
        if (faceImage == null || faceImage.empty()) {
            return new Mat();
        }

        try {
            // Heuristic synthetic eyes based on face proportions
            // Left eye at ~32% width, 36% height (from top-left)
            // Right eye at ~68% width, 36% height (from top-left)
            Point leftEyeGuess = new Point(
                    faceImage.width() * 0.32,
                    faceImage.height() * 0.36);
            Point rightEyeGuess = new Point(
                    faceImage.width() * 0.68,
                    faceImage.height() * 0.36);

            if (debugMode) {
                System.out.println("Using heuristic alignment: Left=" + leftEyeGuess +
                        ", Right=" + rightEyeGuess);
            }

            // Use same alignment transform as real eye detection
            return alignUsingEyes(faceImage, leftEyeGuess, rightEyeGuess);

        } catch (Exception e) {
            System.err.println("Heuristic fallback alignment failed: " + e.getMessage());
            // Ultimate fallback to plain resize
            try {
                Mat aligned = new Mat();
                Imgproc.resize(faceImage, aligned, OUTPUT_SIZE, 0, 0, Imgproc.INTER_CUBIC);
                return aligned;
            } catch (Exception e2) {
                System.err.println("Ultimate fallback failed: " + e2.getMessage());
                return faceImage.clone();
            }
        }
    }

    /**
     * Enable debug output for testing
     */
    public void setDebugMode(boolean debug) {
        this.debugMode = debug;
    }

    /**
     * Check if aligner is properly initialized
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Clean up resources
     */
    public void release() {
        if (leftEyeDetector != null && !leftEyeDetector.empty()) {
            // CascadeClassifier doesn't need explicit release in Java
            leftEyeDetector = null;
        }
        if (rightEyeDetector != null && !rightEyeDetector.empty() &&
                rightEyeDetector != leftEyeDetector) { // Don't release if it's the same object
            rightEyeDetector = null;
        }
    }
}
