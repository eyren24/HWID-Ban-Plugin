package com.eyren.hWIDBan.velocity;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Legge config.yml dalla data-directory usando SnakeYAML (shaded).
 * Supporta path puntati: getString("database.mysql.host", "localhost")
 */
@SuppressWarnings("unchecked")
public class VelocityConfig {

    private final Path   dataDirectory;
    private final Logger logger;
    private Map<String, Object> data = new HashMap<>();

    public VelocityConfig(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger        = logger;
    }

    public void load() {
        Path configPath = dataDirectory.resolve("config.yml");

        if (!Files.exists(configPath)) {
            try (InputStream def = getClass().getResourceAsStream("/config.yml")) {
                if (def != null) Files.copy(def, configPath);
                else logger.warn("Default config.yml not found in jar resources!");
            } catch (IOException e) {
                logger.error("Cannot copy default config: {}", e.getMessage(), e);
            }
        }

        if (Files.exists(configPath)) {
            try (InputStream in = Files.newInputStream(configPath)) {
                Object loaded = new Yaml().load(in);
                if (loaded instanceof Map) data = (Map<String, Object>) loaded;
            } catch (IOException e) {
                logger.error("Cannot load config.yml: {}", e.getMessage(), e);
            }
        }
    }

    /** Ricarica il file da disco senza riavviare il plugin. */
    public void reload() {
        data = new HashMap<>();
        load();
    }

    private Object get(String path) {
        String[] parts = path.split("\\.");
        Object current = data;
        for (String part : parts) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(part);
        }
        return current;
    }

    public String getString(String path, String def) {
        Object v = get(path);
        return v != null ? v.toString() : def;
    }

    public int getInt(String path, int def) {
        Object v = get(path);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v != null) try { return Integer.parseInt(v.toString()); } catch (NumberFormatException ignored) {}
        return def;
    }

    public long getLong(String path, long def) {
        Object v = get(path);
        if (v instanceof Number) return ((Number) v).longValue();
        if (v != null) try { return Long.parseLong(v.toString()); } catch (NumberFormatException ignored) {}
        return def;
    }

    public boolean getBoolean(String path, boolean def) {
        Object v = get(path);
        if (v instanceof Boolean) return (Boolean) v;
        if (v != null) return Boolean.parseBoolean(v.toString());
        return def;
    }

    /** Restituisce la lista di stringhe a path. Se non presente o non lista, ritorna {@code def}. */
    public List<String> getStringList(String path, List<String> def) {
        Object v = get(path);
        if (v instanceof List) {
            List<String> out = new ArrayList<>();
            for (Object o : (List<?>) v) if (o != null) out.add(o.toString());
            return out;
        }
        return def;
    }
}
