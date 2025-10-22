package app.service;

import org.bytedeco.dlib.*;
import org.bytedeco.javacpp.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import static org.bytedeco.dlib.global.dlib.*;


public class DlibFaceAligner {
    private shape_predictor predictor;
    private boolean isInitialized = false;
    private static final Point2f LEFT_EYE_TARGET = new Point2f(0.35f, 0.35f);
    private static final Point2f RIGHT_EYE_TARGET = new Point2f(0.65f, 0.35f);

}