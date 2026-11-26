package org.example.monitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

/**
 * prints each alert and appends it to a log file with its timestamp.
 */
public class AlertLogger{

    private final Path logFile;

    public AlertLogger(Path logFile){
        this.logFile = logFile;
    }

    public void alert(String targetId, String type, String path) throws IOException{
        System.out.println("ALERT - " + type + " " + path + " [" + targetId + "]");
        String line = LocalDateTime.now().withNano(0) + " " + targetId + " " + type + " " + path + "\n";
        Files.writeString(logFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}