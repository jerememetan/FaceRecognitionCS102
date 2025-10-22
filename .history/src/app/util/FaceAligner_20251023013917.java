package app.util;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
<<<<<<< HEAD
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
=======

public class FaceAligner {
    private CascadeClassifier eyeDetector;
    private CascadeClassifier noseDetector;
    private CascadeClassifier mouthDetector;
    private boolean isInitialized = false;
    
    private static final Point LEFT_EYE_TARGET = new Point(38.2946, 51.6963);
    private static final Point RIGHT_EYE_TARGET = new Point(73.5318, 51.5014);
    private static final Point NOSE_TARGET = new Point(56.0252, 71.7366);
    private static final Point LEFT_MOUTH_TARGET = new Point(41.5493, 92.3655);
    private static final Point RIGHT_MOUTH_TARGET = new Point(70.7299, 92.2041);
    
    private static final Size OUTPUT_SIZE = new Size(112, 112);
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9
    private boolean debugMode = false;

    public FaceAligner() {
        initialize();
    }

    private void initialize() {
        try {
            String eyeCascadePath = "data/resources/haarcascade_eye.xml";
            String noseCascadePath = "data/resources/haarcascade_mcs_nose.xml";
            String mouthCascadePath = "data/resources/haarcascade_mcs_mouth.xml";

            eyeDetector = new CascadeClassifier(eyeCascadePath);
            noseDetector = new CascadeClassifier(noseCascadePath);
            mouthDetector = new CascadeClassifier(mouthCascadePath);

            if (eyeDetector.empty()) {
                System.err.println("ERROR: Could not load eye cascade from: " + eyeCascadePath);
                isInitialized = false;
                return;
            }

            if (noseDetector.empty()) {
                System.out.println("⚠ Nose cascade not found at: " + noseCascadePath + " - will use heuristics");
            }
            if (mouthDetector.empty()) {
                System.out.println("⚠ Mouth cascade not found at: " + mouthCascadePath + " - will use heuristics");
            }

            System.out.println("✓ FaceAligner initialized with 5-point alignment support");
            isInitialized = true;

        } catch (Exception e) {
            System.err.println("Failed to initialize FaceAligner: " + e.getMessage());
            e.printStackTrace();
            isInitialized = false;
        }
    }

<<<<<<< HEAD
    /**
     * Aligns a face image using separate left/right eye detection with geometric
     * validation
     *
     * @param faceImage Input face ROI (already cropped to face bounding box)
     * @param faceRect  Original face bounding box (for reference)
     * @return Aligned 96x96 face image, or null if alignment fails
     */
=======
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9
    public Mat align(Mat faceImage, Rect faceRect) {
        if (!isInitialized) {
            return heuristicFallbackAlignment(faceImage);
        }

        if (faceImage == null || faceImage.empty()) {
            System.err.println("Empty face image provided to aligner");
            return null;
        }

<<<<<<< HEAD
        try {
            // Convert to grayscale for eye detection
=======
        if (faceImage.width() < 60 || faceImage.height() < 60) {
            return heuristicFallbackAlignment(faceImage);
        }

        try {
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9
            Mat gray = new Mat();
            if (faceImage.channels() == 3) {
                Imgproc.cvtColor(faceImage, gray, Imgproc.COLOR_BGR2GRAY);
            } else {
                gray = faceImage.clone();
            }

<<<<<<< HEAD
            // 1) Detect left and right eyes in constrained halves
            Rect leftHalf = new Rect(0, 0, faceImage.width() / 2, (int) (faceImage.height() * 0.7));
            Rect rightHalf = new Rect(faceImage.width() / 2, 0, faceImage.width() / 2,
                    (int) (faceImage.height() * 0.7));

            Mat grayLeft = new Mat(gray, leftHalf);
            Mat grayRight = new Mat(gray, rightHalf);

            MatOfRect leftEyes = new MatOfRect();
            MatOfRect rightEyes = new MatOfRect();

            leftEyeDetector.detectMultiScale(grayLeft, leftEyes, 1.05, 3, 0,
                    new Size(15, 15), new Size(100, 100));
            rightEyeDetector.detectMultiScale(grayRight, rightEyes, 1.05, 3, 0,
                    new Size(15, 15), new Size(100, 100));

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
            System.err.println("Eye detection failed: " + e.getMessage());
=======
            Point[] landmarks = detect5Landmarks(gray, faceImage.width(), faceImage.height());
            gray.release();

            if (landmarks != null && isValidLandmarkConfiguration(landmarks, faceImage.width(), faceImage.height())) {
                if (debugMode) {
                    System.out.println("✓ Detected valid 5 landmarks");
                }
                return alignUsing5Points(faceImage, landmarks);
            } else {
                if (debugMode) {
                    System.out.println("⚠ 5-point detection failed or invalid, using heuristics");
                }
                return heuristicFallbackAlignment(faceImage);
            }

        } catch (Exception e) {
            System.err.println("Alignment failed: " + e.getMessage());
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9
            return heuristicFallbackAlignment(faceImage);
        }
    }

    /**
<<<<<<< HEAD
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
=======
     * ✅ NEW: Validate landmark configuration to prevent extreme transforms
     */
    private boolean isValidLandmarkConfiguration(Point[] landmarks, int width, int height) {
        if (landmarks == null || landmarks.length < 3) {
            return false;
        }

        Point leftEye = landmarks[0];
        Point rightEye = landmarks[1];
        Point nose = landmarks[2];

        // Check all landmarks are within image bounds
        for (Point p : landmarks) {
            if (p.x < 0 || p.x >= width || p.y < 0 || p.y >= height) {
                return false;
            }
        }

        // Eye distance should be reasonable (20%-80% of width)
        double eyeDist = Math.hypot(rightEye.x - leftEye.x, rightEye.y - leftEye.y);
        if (eyeDist < width * 0.2 || eyeDist > width * 0.8) {
            return false;
        }

        // Eyes should be roughly horizontal (tilt < 30 degrees)
        double eyeAngle = Math.abs(Math.atan2(rightEye.y - leftEye.y, rightEye.x - leftEye.x));
        if (eyeAngle > Math.PI / 6) { // 30 degrees
            return false;
        }

        // Nose should be below eye line and centered
        double eyeMidY = (leftEye.y + rightEye.y) / 2.0;
        if (nose.y <= eyeMidY || nose.y > eyeMidY + eyeDist * 1.5) {
            return false;
        }

        return true;
    }

    private Point[] detect5Landmarks(Mat gray, int width, int height) {
        try {
            Rect topHalf = new Rect(0, 0, width, (int)(height * 0.6));
            Mat grayTop = new Mat(gray, topHalf);
            MatOfRect eyes = new MatOfRect();
            eyeDetector.detectMultiScale(grayTop, eyes, 1.1, 3, 0,
                    new Size(width * 0.1, height * 0.1),
                    new Size(width * 0.4, height * 0.4));

            Rect[] eyeArray = eyes.toArray();
            grayTop.release();
            eyes.release();

            if (eyeArray.length < 2) {
                return null;
            }

            Point leftEye = null, rightEye = null;
            if (eyeArray.length >= 2) {
                java.util.Arrays.sort(eyeArray, (a, b) ->
                    Integer.compare(b.width * b.height, a.width * a.height));
                
                Point eye1 = new Point(eyeArray[0].x + eyeArray[0].width/2.0,
                                      eyeArray[0].y + eyeArray[0].height/2.0);
                Point eye2 = new Point(eyeArray[1].x + eyeArray[1].width/2.0,
                                      eyeArray[1].y + eyeArray[1].height/2.0);
                
                if (eye1.x < eye2.x) {
                    leftEye = eye1;
                    rightEye = eye2;
                } else {
                    leftEye = eye2;
                    rightEye = eye1;
                }
            }

            if (leftEye == null || rightEye == null) {
                return null;
            }

            Point nose = detectNose(gray, width, height, leftEye, rightEye);
            Point[] mouth = detectMouthCorners(gray, width, height, leftEye, rightEye, nose);

            if (nose == null || mouth == null) {
                nose = estimateNose(leftEye, rightEye);
                mouth = estimateMouthCorners(leftEye, rightEye, nose);
            }

            return new Point[] {leftEye, rightEye, nose, mouth[0], mouth[1]};

        } catch (Exception e) {
            if (debugMode) {
                System.err.println("5-point detection error: " + e.getMessage());
            }
            return null;
        }
    }

    private Point detectNose(Mat gray, int width, int height, Point leftEye, Point rightEye) {
        if (noseDetector.empty()) {
            return estimateNose(leftEye, rightEye);
        }

        try {
            double eyeMidX = (leftEye.x + rightEye.x) / 2.0;
            double eyeMidY = (leftEye.y + rightEye.y) / 2.0;
            double eyeDist = rightEye.x - leftEye.x;

            int searchX = (int)Math.max(0, eyeMidX - eyeDist * 0.4);
            int searchY = (int)Math.max(0, eyeMidY);
            int searchW = (int)Math.min(width - searchX, eyeDist * 0.8);
            int searchH = (int)Math.min(height - searchY, eyeDist * 0.9);

            if (searchW < 20 || searchH < 20) {
                return estimateNose(leftEye, rightEye);
            }

            Rect noseRegion = new Rect(searchX, searchY, searchW, searchH);
            Mat grayNose = new Mat(gray, noseRegion);

            MatOfRect noses = new MatOfRect();
            noseDetector.detectMultiScale(grayNose, noses, 1.1, 3, 0,
                    new Size(searchW * 0.2, searchH * 0.2),
                    new Size(searchW * 0.6, searchH * 0.6));

            Rect[] noseArray = noses.toArray();
            grayNose.release();
            noses.release();

            if (noseArray.length > 0) {
                Rect bestNose = noseArray[0];
                return new Point(searchX + bestNose.x + bestNose.width/2.0,
                               searchY + bestNose.y + bestNose.height/2.0);
            }
        } catch (Exception e) {
            // Fall through to estimation
        }

        return estimateNose(leftEye, rightEye);
    }

    private Point[] detectMouthCorners(Mat gray, int width, int height,
                                      Point leftEye, Point rightEye, Point nose) {
        if (mouthDetector.empty()) {
            return estimateMouthCorners(leftEye, rightEye, nose);
        }

        try {
            double eyeMidX = (leftEye.x + rightEye.x) / 2.0;
            double eyeDist = rightEye.x - leftEye.x;

            int searchX = (int)Math.max(0, eyeMidX - eyeDist * 0.6);
            int searchY = (int)Math.max(0, nose.y + eyeDist * 0.2);
            int searchW = (int)Math.min(width - searchX, eyeDist * 1.2);
            int searchH = (int)Math.min(height - searchY, eyeDist * 0.7);

            if (searchW < 30 || searchH < 20) {
                return estimateMouthCorners(leftEye, rightEye, nose);
            }

            Rect mouthRegion = new Rect(searchX, searchY, searchW, searchH);
            Mat grayMouth = new Mat(gray, mouthRegion);

            MatOfRect mouths = new MatOfRect();
            mouthDetector.detectMultiScale(grayMouth, mouths, 1.1, 3, 0,
                    new Size(searchW * 0.3, searchH * 0.2),
                    new Size(searchW * 0.9, searchH * 0.8));

            Rect[] mouthArray = mouths.toArray();
            grayMouth.release();
            mouths.release();

            if (mouthArray.length > 0) {
                Rect mouth = mouthArray[0];
                double mouthCenterX = searchX + mouth.x + mouth.width/2.0;
                double mouthCenterY = searchY + mouth.y + mouth.height/2.0;
                
                Point leftMouth = new Point(mouthCenterX - mouth.width * 0.35, mouthCenterY);
                Point rightMouth = new Point(mouthCenterX + mouth.width * 0.35, mouthCenterY);
                return new Point[] {leftMouth, rightMouth};
            }
        } catch (Exception e) {
            // Fall through to estimation
        }

        return estimateMouthCorners(leftEye, rightEye, nose);
    }

    private Point estimateNose(Point leftEye, Point rightEye) {
        double eyeMidX = (leftEye.x + rightEye.x) / 2.0;
        double eyeMidY = (leftEye.y + rightEye.y) / 2.0;
        double eyeDist = Math.hypot(rightEye.x - leftEye.x, rightEye.y - leftEye.y);
        
        return new Point(eyeMidX, eyeMidY + eyeDist * 0.55);
    }

    private Point[] estimateMouthCorners(Point leftEye, Point rightEye, Point nose) {
        double eyeDist = Math.hypot(rightEye.x - leftEye.x, rightEye.y - leftEye.y);
        double mouthY = nose.y + eyeDist * 0.5;
        double mouthWidth = eyeDist * 0.7;
        double eyeMidX = (leftEye.x + rightEye.x) / 2.0;
        
        Point leftMouth = new Point(eyeMidX - mouthWidth/2.0, mouthY);
        Point rightMouth = new Point(eyeMidX + mouthWidth/2.0, mouthY);
        return new Point[] {leftMouth, rightMouth};
    }

    private Mat alignUsing5Points(Mat faceImage, Point[] landmarks) {
        try {
            if (landmarks == null || landmarks.length < 3) {
                return heuristicFallbackAlignment(faceImage);
            }

            MatOfPoint2f srcPoints = new MatOfPoint2f(
                landmarks[0], // left eye
                landmarks[1], // right eye
                landmarks[2]  // nose
            );

            MatOfPoint2f dstPoints = new MatOfPoint2f(
                LEFT_EYE_TARGET,
                RIGHT_EYE_TARGET,
                NOSE_TARGET
            );

            Mat transform = Imgproc.getAffineTransform(srcPoints, dstPoints);

            if (transform == null || transform.empty()) {
                srcPoints.release();
                dstPoints.release();
                return heuristicFallbackAlignment(faceImage);
            }

            // ✅ NEW: Check for extreme transform values
            double[] transformData = new double[6];
            transform.get(0, 0, transformData);

            // Scale factors should be reasonable (0.5 to 2.0)
            double scaleX = Math.sqrt(transformData[0] * transformData[0] + transformData[1] * transformData[1]);
            double scaleY = Math.sqrt(transformData[3] * transformData[3] + transformData[4] * transformData[4]);

            if (scaleX < 0.5 || scaleX > 2.0 || scaleY < 0.5 || scaleY > 2.0) {
                if (debugMode) {
                    System.out.println("⚠ Extreme transform detected (scaleX=" + String.format("%.2f", scaleX) +
                                     ", scaleY=" + String.format("%.2f", scaleY) + "), using fallback");
                }
                transform.release();
                srcPoints.release();
                dstPoints.release();
                return heuristicFallbackAlignment(faceImage);
            }

            Mat aligned = new Mat();
            Imgproc.warpAffine(faceImage, aligned, transform, OUTPUT_SIZE,
                    Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, new Scalar(0, 0, 0));

            transform.release();
            srcPoints.release();
            dstPoints.release();

            // ✅ NEW: Verify aligned image has valid pixel values
            if (!aligned.empty()) {
                Mat grayAligned = new Mat();
                if (aligned.channels() == 3) {
                    Imgproc.cvtColor(aligned, grayAligned, Imgproc.COLOR_BGR2GRAY);
                } else {
                    grayAligned = aligned.clone();
                }

                Core.MinMaxLocResult minMax = Core.minMaxLoc(grayAligned);
                grayAligned.release();

                if (minMax.minVal < -1.0 || minMax.maxVal > 256.0) {
                    if (debugMode) {
                        System.out.println("⚠ Invalid aligned pixel values (min=" + minMax.minVal +
                                         ", max=" + minMax.maxVal + "), using fallback");
                    }
                    aligned.release();
                    return heuristicFallbackAlignment(faceImage);
                }
            } else {
                if (debugMode) {
                    System.out.println("⚠ Empty aligned image, using fallback");
                }
                return heuristicFallbackAlignment(faceImage);
            }

            return aligned;

        } catch (Exception e) {
            System.err.println("5-point alignment failed: " + e.getMessage());
            if (debugMode) {
                e.printStackTrace();
            }
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9
            return heuristicFallbackAlignment(faceImage);
        }
    }

<<<<<<< HEAD
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
            double score = distanceFromCenter / halfRegion.width() +
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
=======
    private Mat heuristicFallbackAlignment(Mat faceImage) {
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9
        if (faceImage == null || faceImage.empty()) {
            return new Mat();
        }

        try {
            Mat aligned = new Mat();
            Imgproc.resize(faceImage, aligned, OUTPUT_SIZE, 0, 0, Imgproc.INTER_CUBIC);
            return aligned;
<<<<<<< HEAD
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
=======

        } catch (Exception e) {
            return new Mat();
        }
    }

>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9
    public void setDebugMode(boolean debug) {
        this.debugMode = debug;
    }

<<<<<<< HEAD
    /**
     * Check if aligner is properly initialized
     */
=======
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9
    public boolean isInitialized() {
        return isInitialized;
    }

<<<<<<< HEAD
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
=======
    public void release() {
        eyeDetector = null;
        noseDetector = null;
        mouthDetector = null;
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9
    }
}
