package io.themis.core.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JsonIo {
    private final ObjectMapper mapper;

    public JsonIo() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public <T> T read(Path path, Class<T> clazz) throws IOException {
        return mapper.readValue(Files.newInputStream(path), clazz);
    }

    public void write(Path path, Object value) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        mapper.writeValue(Files.newOutputStream(path), value);
    }

    public String toJson(Object value) throws IOException {
        return mapper.writeValueAsString(value);
    }
}
