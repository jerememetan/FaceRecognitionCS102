package app.test;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.File;

public class ImageDimensionChecker {
    public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        if (args.length == 0) {
            System.out.println("Usage: java ImageDimensionChecker <image_path_or_folder>");
            return;
        }

        File target = new File(args[0]);

        if (target.isDirectory()) {
            File[] images = target.listFiles((dir, name) -> name.toLowerCase().endsWith(".jpg"));
            if (images != null) {
                System.out.println("Checking " + images.length + " images in " + target.getName());
                System.out.println("=".repeat(60));
                for (File img : images) {
                    checkImage(img);
                }
            }
        } else if (target.isFile()) {
            checkImage(target);
        }
    }

    private static void checkImage(File imageFile) {
        Mat img = Imgcodecs.imread(imageFile.getAbsolutePath());
        if (!img.empty()) {
            System.out.printf("%s: %dx%d (%d channels, size=%d bytes)%n",
                    imageFile.getName(),
                    img.width(),
                    img.height(),
                    img.channels(),
                    imageFile.length());
        } else {
            System.out.println(imageFile.getName() + ": Failed to read");
        }
        img.release();
    }
}
