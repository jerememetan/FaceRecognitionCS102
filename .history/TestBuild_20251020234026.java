import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.javacpp.Loader;

public class TestBuild {
    public static void main(String[] args) {
        Loader.load(opencv_core.class);
        System.out.println("OpenCV version: " + org.bytedeco.opencv.global.opencv_core.opencv_version());
    }
}