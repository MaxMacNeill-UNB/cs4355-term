package org.example.monitor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * saves the last known hashes to a json file, grouped by target id:
 */
public class HashStore{

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private Map<String, Map<String, String>> data = new TreeMap<>();

    public HashStore(Path file) throws IOException{
        this.file = file;
        if (Files.exists(file)){
            data = mapper.readValue(file.toFile(), new TypeReference<Map<String, Map<String, String>>>() {});
        }
    }

    //returns the saved hashes for a target
    public Map<String, String> load(String targetId){
        return data.get(targetId);
    }

    public void save(String targetId, Map<String, String> hashes) throws IOException{
        data.put(targetId, hashes);
        mapper.writeValue(file.toFile(), data);
    }
}