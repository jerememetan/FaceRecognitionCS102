import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.javacpp.Loader;

public class TestBuild {
    public static void main(String[] args) {
        // Load native libraries
        Loader.load(opencv_core.class);

        // Print OpenCV version
        System.out.println("OpenCV version: " + opencv_core.OPENCV_VERSION);
    }
}