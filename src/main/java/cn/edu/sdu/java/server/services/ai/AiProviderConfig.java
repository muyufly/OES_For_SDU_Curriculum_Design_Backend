package cn.edu.sdu.java.server.services.ai;

import java.util.HashMap;
import java.util.Map;

public class AiProviderConfig {
    public Integer id;
    public String name;
    public String provider;
    public Boolean enabled;
    public String endpoint;
    public String apiKey;
    public String apiKeyEnv;
    public String model;
    public Double temperature;
    public Integer maxTokens;
    public Integer timeoutSeconds;
    public Map<String, String> headers = new HashMap<>();

    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public String displayName() {
        return name == null || name.isBlank() ? provider : name;
    }
}
