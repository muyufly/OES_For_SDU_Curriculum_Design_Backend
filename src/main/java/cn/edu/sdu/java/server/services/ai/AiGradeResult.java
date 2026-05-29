package cn.edu.sdu.java.server.services.ai;

import java.util.LinkedHashMap;
import java.util.Map;

public class AiGradeResult {
    private int score;
    private String reason;
    private double confidence;
    private String provider;
    private boolean cacheHit;
    private String rawResponse;

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isCacheHit() {
        return cacheHit;
    }

    public void setCacheHit(boolean cacheHit) {
        this.cacheHit = cacheHit;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("score", score);
        map.put("reason", reason);
        map.put("confidence", confidence);
        map.put("provider", provider);
        map.put("cacheHit", cacheHit);
        return map;
    }
}
