package reportingandexport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class util {
    public static long countFilesInFolder(String exportedFolderPath, String extension) throws IOException{
        Path dir = Paths.get(exportedFolderPath);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Provided path is not a directory: " + exportedFolderPath);
        }

        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith("." + extension))
                    .count();
        }
    }
}
