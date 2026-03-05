package io.themis.cli.util;

import io.themis.core.config.ThemisConfig;
import io.themis.core.io.JsonIo;

import java.io.IOException;
import java.nio.file.Path;

public class ConfigLoader {
    private final JsonIo jsonIo = new JsonIo();

    public ThemisConfig load(Path path) throws IOException {
        return jsonIo.read(path, ThemisConfig.class);
    }
}
