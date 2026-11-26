package org.example.plugin.source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Stub {@link SourcePlugin} for {@code file://} local filesystem resources.
 */
public class FileSourcePlugin implements SourcePlugin {

    private static final String PREFIX = "file://";

    @Override
    public String getScheme() {
        return "file";
    }

    @Override
    public byte[] fetch(String path) throws IOException {
        return Files.readAllBytes(toPath(path));
    }

    @Override
    public List<String> discover(
            String path,
            int depth,
            List<String> allowedDomains,
            List<String> allowedExtensions
    ) throws IOException {
        Path start = toPath(path);
        List<String> found = new ArrayList<>();

        if (Files.isRegularFile(start)){
            found.add(toUri(start));
        } 
        else if (Files.isDirectory(start)){
            try (Stream<Path> files = Files.walk(start, depth + 1)){
                for (Path p : files.filter(Files::isRegularFile).toList()){
                    if (matches(p, allowedExtensions)){
                        found.add(toUri(p));
                    }
                }
            }
        }
        return found;
    }

    @Override
    public boolean isAvailable(String path) throws IOException {
        return Files.exists(toPath(path));
    }

    private static Path toPath(String path){
        return Path.of(path.startsWith(PREFIX) ? path.substring(PREFIX.length()) : path);
    }

    private static String toUri(Path p){
        return PREFIX + p.toAbsolutePath().normalize().toString().replace("\\", "/");
    }

    private static boolean matches(Path p, List<String> extensions){
        if (extensions == null || extensions.isEmpty()){
            return true;
        }
        String name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
        for (String e : extensions){
            if (e.toLowerCase().replaceFirst("^\\.", "").equals(ext)){
                return true;
            }
        }
        return false;
    }
}
