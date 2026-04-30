package com.eyren.hWIDBan.fingerprint;

import java.util.LinkedHashMap;
import java.util.Map;

public class FingerprintData {

    private final Map<String, String> fields = new LinkedHashMap<>();
    private String hash;

    public FingerprintData put(String key, String value) {
        if (value != null && !value.isEmpty()) fields.put(key, value);
        return this;
    }

    public String get(String key) {
        return fields.get(key);
    }

    public Map<String, String> fields() {
        return fields;
    }

    public String hash() {
        return hash;
    }

    public void hash(String hash) {
        this.hash = hash;
    }

    public String rawString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (sb.length() > 0) sb.append('|');
            sb.append(e.getValue());
        }
        return sb.toString();
    }
}
