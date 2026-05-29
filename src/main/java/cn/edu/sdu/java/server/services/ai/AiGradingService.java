package cn.edu.sdu.java.server.services.ai;

import cn.edu.sdu.java.server.models.AiProviderSetting;
import cn.edu.sdu.java.server.models.StudentExamRecord;
import cn.edu.sdu.java.server.repositorys.AiProviderSettingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

@Service
public class AiGradingService {
    private static final Logger log = LoggerFactory.getLogger(AiGradingService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Object cacheLock = new Object();
    private final AiProviderSettingRepository aiProviderSettingRepository;

    public AiGradingService(AiProviderSettingRepository aiProviderSettingRepository) {
        this.aiProviderSettingRepository = aiProviderSettingRepository;
    }

    public List<Map<String, Object>> listProviders() {
        return loadConfigs().stream()
                .filter(AiProviderConfig::isEnabled)
                .map(config -> configToMap(config, false))
                .toList();
    }

    public List<Map<String, Object>> listAdminProviders() {
        List<AiProviderConfig> configs = loadConfigs();
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiProviderConfig config : configs) {
            result.add(configToMap(config, true));
        }
        return result;
    }

    public Map<String, Object> saveProvider(Integer id, Map<String, Object> form) {
        AiProviderConfig config = id == null || id <= 0
                ? new AiProviderConfig()
                : entityToConfig(aiProviderSettingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AI provider config does not exist")));
        fillConfig(config, form);
        validateConfig(config);
        AiProviderSetting saved = aiProviderSettingRepository.save(configToEntity(config, id));
        return configToMap(entityToConfig(saved), true);
    }

    public void deleteProvider(Integer id) {
        if (id == null || !aiProviderSettingRepository.existsById(id)) {
            throw new IllegalArgumentException("AI provider config does not exist");
        }
        aiProviderSettingRepository.deleteById(id);
    }

    public AiGradeResult grade(StudentExamRecord record, String providerName) {
        if (record == null || record.getQuestion() == null) {
            throw new IllegalArgumentException("record is required");
        }
        if (!"READ".equals(record.getQuestion().getQuestionType())) {
            throw new IllegalArgumentException("Only READ questions can use AI grading");
        }
        int maxScore = record.getQuestion().getScore() == null ? 0 : record.getQuestion().getScore();
        if (maxScore <= 0) {
            throw new IllegalArgumentException("question max score is invalid");
        }

        String cacheKey = cacheKey(record);
        AiGradeResult cached = readCache(cacheKey);
        if (cached != null) {
            cached.setCacheHit(true);
            return cached;
        }

        AiProviderConfig config = selectConfig(providerName);
        AiGradeResult result = isMock(config)
                ? mockGrade(record)
                : remoteGrade(record, config);
        result.setScore(Math.max(0, Math.min(maxScore, result.getScore())));
        result.setProvider(config.displayName());
        result.setCacheHit(false);
        writeCache(cacheKey, result);
        return result;
    }

    private AiGradeResult remoteGrade(StudentExamRecord record, AiProviderConfig config) {
        String apiKey = resolveApiKey(config);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("AI provider api key is missing: " + config.displayName());
        }
        if (config.endpoint == null || config.endpoint.isBlank()) {
            throw new IllegalArgumentException("AI provider endpoint is missing: " + config.displayName());
        }
        try {
            Map<String, Object> request = buildUnifiedGradeRequest(record);
            String userContent = mapper.writeValueAsString(request);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", config.model == null || config.model.isBlank() ? "gpt-4o-mini" : config.model);
            body.put("temperature", config.temperature == null ? 0.0 : config.temperature);
            body.put("max_tokens", config.maxTokens == null ? 500 : config.maxTokens);
            body.put("response_format", Map.of("type", "json_object"));
            body.put("messages", List.of(
                    Map.of("role", "system", "content", loadSkillPrompt()),
                    Map.of("role", "user", "content", userContent)
            ));

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(config.endpoint))
                    .timeout(Duration.ofSeconds(config.timeoutSeconds == null ? 30 : config.timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8));
            if (config.headers != null) {
                config.headers.forEach(builder::header);
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("AI provider returned HTTP " + response.statusCode() + ": " + response.body());
            }
            String content = extractChatContent(response.body());
            AiGradeResult result = parseGradeJson(content);
            result.setRawResponse(response.body());
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("AI grading request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI grading request interrupted", e);
        }
    }

    private Map<String, Object> buildUnifiedGradeRequest(StudentExamRecord record) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("task", "grade_subjective_answer");
        request.put("language", "zh-CN");
        request.put("question", safe(record.getQuestion().getContent()));
        request.put("referenceAnswer", safe(record.getQuestion().getAnswer()));
        request.put("studentAnswer", safe(record.getAnswer()));
        request.put("maxScore", record.getQuestion().getScore());
        request.put("rubric", "Give a fair integer score from 0 to maxScore. Award partial credit for semantically correct content. Return JSON only.");
        request.put("outputSchema", Map.of(
                "score", "integer 0..maxScore",
                "reason", "short grading rationale",
                "confidence", "number 0..1"
        ));
        return request;
    }

    private AiGradeResult mockGrade(StudentExamRecord record) {
        int maxScore = record.getQuestion().getScore() == null ? 0 : record.getQuestion().getScore();
        String reference = normalize(record.getQuestion().getAnswer());
        String answer = normalize(record.getAnswer());
        int score;
        String reason;
        if (answer.isBlank()) {
            score = 0;
            reason = "Mock grader: empty answer.";
        } else if (!reference.isBlank() && answer.contains(reference)) {
            score = maxScore;
            reason = "Mock grader: answer contains the reference answer.";
        } else {
            Set<String> refTokens = tokens(reference);
            Set<String> answerTokens = tokens(answer);
            if (refTokens.isEmpty()) {
                score = Math.max(0, maxScore / 2);
                reason = "Mock grader: no reference answer; gives conservative partial credit.";
            } else {
                long hit = refTokens.stream().filter(answerTokens::contains).count();
                score = (int) Math.round(maxScore * (hit * 1.0 / refTokens.size()));
                reason = "Mock grader: keyword overlap " + hit + "/" + refTokens.size() + ".";
            }
        }
        AiGradeResult result = new AiGradeResult();
        result.setScore(score);
        result.setReason(reason);
        result.setConfidence(0.72);
        return result;
    }

    private AiGradeResult parseGradeJson(String content) throws IOException {
        JsonNode node = mapper.readTree(content);
        AiGradeResult result = new AiGradeResult();
        result.setScore(node.path("score").asInt(0));
        result.setReason(node.path("reason").asText("AI returned no reason."));
        result.setConfidence(node.path("confidence").asDouble(0.5));
        return result;
    }

    private String extractChatContent(String body) throws IOException {
        JsonNode root = mapper.readTree(body);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new IllegalStateException("AI provider response does not contain choices[0].message.content");
        }
        return content.asText();
    }

    private AiProviderConfig selectConfig(String providerName) {
        List<AiProviderConfig> configs = loadConfigs().stream().filter(AiProviderConfig::isEnabled).toList();
        if (configs.isEmpty()) {
            return defaultMockConfig();
        }
        if (providerName != null && !providerName.isBlank()) {
            for (AiProviderConfig config : configs) {
                if (providerName.equalsIgnoreCase(config.displayName())) {
                    return config;
                }
            }
        }
        return configs.get(0);
    }

    private List<AiProviderConfig> loadConfigs() {
        seedDatabaseIfEmpty();
        List<AiProviderSetting> settings = aiProviderSettingRepository.findAllByOrderByProviderIdAsc();
        if (!settings.isEmpty()) {
            return settings.stream().map(this::entityToConfig).toList();
        }
        return List.of(defaultMockConfig());
    }

    private void seedDatabaseIfEmpty() {
        if (aiProviderSettingRepository.count() > 0) {
            return;
        }
        List<AiProviderConfig> configs = new ArrayList<>();
        for (String line : readConfigLines()) {
            if (line == null || line.isBlank() || line.trim().startsWith("#")) {
                continue;
            }
            try {
                AiProviderConfig config = mapper.readValue(line, AiProviderConfig.class);
                configs.add(config);
            } catch (Exception e) {
                log.error("Invalid AI config line: {}", line, e);
            }
        }
        if (configs.isEmpty()) {
            configs.add(defaultMockConfig());
        }
        for (AiProviderConfig config : configs) {
            validateConfig(config);
            aiProviderSettingRepository.save(configToEntity(config, null));
        }
    }

    private List<String> readConfigLines() {
        Path path = configPath();
        try {
            if (Files.exists(path)) {
                return Files.readAllLines(path, StandardCharsets.UTF_8);
            }
            ClassPathResource resource = new ClassPathResource("apiconfig.jsonl");
            if (resource.exists()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    return reader.lines().toList();
                }
            }
        } catch (Exception e) {
            log.error("Failed to read AI config", e);
        }
        return List.of();
    }

    private Path configPath() {
        String explicit = System.getProperty("oes.ai.config");
        if (explicit == null || explicit.isBlank()) {
            explicit = System.getenv("OES_AI_CONFIG");
        }
        return Path.of(explicit == null || explicit.isBlank() ? "apiconfig.jsonl" : explicit);
    }

    private AiProviderConfig defaultMockConfig() {
        AiProviderConfig config = new AiProviderConfig();
        config.name = "mock";
        config.provider = "mock";
        config.enabled = true;
        config.model = "local-keyword-rubric";
        return config;
    }

    private AiProviderConfig entityToConfig(AiProviderSetting setting) {
        AiProviderConfig config = new AiProviderConfig();
        config.id = setting.getProviderId();
        config.name = setting.getName();
        config.provider = setting.getProvider();
        config.enabled = setting.getEnabled() == null || setting.getEnabled() == 1;
        config.endpoint = setting.getEndpoint();
        config.apiKey = setting.getApiKey();
        config.apiKeyEnv = setting.getApiKeyEnv();
        config.model = setting.getModel();
        config.temperature = setting.getTemperature();
        config.maxTokens = setting.getMaxTokens();
        config.timeoutSeconds = setting.getTimeoutSeconds();
        return config;
    }

    private AiProviderSetting configToEntity(AiProviderConfig config, Integer id) {
        AiProviderSetting setting = id == null || id <= 0
                ? new AiProviderSetting()
                : aiProviderSettingRepository.findById(id).orElse(new AiProviderSetting());
        setting.setName(config.displayName());
        setting.setProvider(config.provider == null || config.provider.isBlank() ? "custom" : config.provider);
        setting.setEnabled(config.isEnabled() ? 1 : 0);
        setting.setEndpoint(config.endpoint);
        setting.setApiKey(config.apiKey);
        setting.setApiKeyEnv(config.apiKeyEnv);
        setting.setModel(config.model);
        setting.setTemperature(config.temperature);
        setting.setMaxTokens(config.maxTokens);
        setting.setTimeoutSeconds(config.timeoutSeconds);
        return setting;
    }

    private Map<String, Object> configToMap(AiProviderConfig config, boolean admin) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", config.id);
        map.put("name", config.displayName());
        map.put("provider", config.provider == null ? "mock" : config.provider);
        map.put("model", config.model);
        map.put("enabled", config.isEnabled());
        map.put("mock", isMock(config));
        if (admin) {
            map.put("endpoint", config.endpoint);
            map.put("apiKeyEnv", config.apiKeyEnv);
            map.put("apiKeyConfigured", config.apiKey != null && !config.apiKey.isBlank());
            map.put("temperature", config.temperature);
            map.put("maxTokens", config.maxTokens);
            map.put("timeoutSeconds", config.timeoutSeconds);
        }
        return map;
    }

    private void fillConfig(AiProviderConfig config, Map<String, Object> form) {
        config.name = text(form, "name");
        config.provider = text(form, "provider");
        config.enabled = bool(form, "enabled", true);
        config.endpoint = blankToNull(text(form, "endpoint"));
        String apiKey = text(form, "apiKey");
        if (apiKey != null && !apiKey.isBlank()) {
            config.apiKey = apiKey.trim();
        }
        config.apiKeyEnv = blankToNull(text(form, "apiKeyEnv"));
        config.model = blankToNull(text(form, "model"));
        config.temperature = doubleValue(form, "temperature", 0.0);
        config.maxTokens = intValue(form, "maxTokens", 500);
        config.timeoutSeconds = intValue(form, "timeoutSeconds", 30);
        if (config.headers == null) {
            config.headers = new HashMap<>();
        }
    }

    private void validateConfig(AiProviderConfig config) {
        if (config.name == null || config.name.isBlank()) {
            throw new IllegalArgumentException("名称不能为空");
        }
        if (config.provider == null || config.provider.isBlank()) {
            config.provider = "custom";
        }
        if (!isMock(config) && (config.endpoint == null || config.endpoint.isBlank())) {
            throw new IllegalArgumentException("非 mock API 必须填写 endpoint");
        }
        if (config.model == null || config.model.isBlank()) {
            config.model = isMock(config) ? "local-keyword-rubric" : "default";
        }
    }

    private String text(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? null : value.toString();
    }

    private Boolean bool(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map == null ? null : map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(value.toString()) || "1".equals(value.toString());
    }

    private Integer intValue(Map<String, Object> map, String key, int defaultValue) {
        Object value = map == null ? null : map.get(key);
        if (value == null || value.toString().isBlank()) return defaultValue;
        try {
            return (int) Double.parseDouble(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Double doubleValue(Map<String, Object> map, String key, double defaultValue) {
        Object value = map == null ? null : map.get(key);
        if (value == null || value.toString().isBlank()) return defaultValue;
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isMock(AiProviderConfig config) {
        String provider = config.provider == null ? "" : config.provider;
        return provider.isBlank() || "mock".equalsIgnoreCase(provider);
    }

    private String resolveApiKey(AiProviderConfig config) {
        if (config.apiKey != null && !config.apiKey.isBlank()) {
            return config.apiKey;
        }
        if (config.apiKeyEnv != null && !config.apiKeyEnv.isBlank()) {
            return System.getenv(config.apiKeyEnv);
        }
        return null;
    }

    private String loadSkillPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("ai-grading-skill.md");
            if (resource.exists()) {
                return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("AI grading skill prompt not found, using built-in prompt", e);
        }
        return "You are an exam grading assistant. Return JSON only with score, reason, confidence.";
    }

    private String cacheKey(StudentExamRecord record) {
        String payload = safe(record.getQuestion().getContent()) + "\n---\n"
                + safe(record.getQuestion().getAnswer()) + "\n---\n"
                + safe(record.getAnswer()) + "\n---\n"
                + record.getQuestion().getScore();
        return sha256(payload);
    }

    private AiGradeResult readCache(String key) {
        synchronized (cacheLock) {
            Path path = cachePath();
            if (!Files.exists(path)) {
                return null;
            }
            try {
                for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    if (line.isBlank()) continue;
                    JsonNode node = mapper.readTree(line);
                    if (key.equals(node.path("key").asText())) {
                        AiGradeResult result = mapper.treeToValue(node.path("result"), AiGradeResult.class);
                        result.setProvider(node.path("provider").asText(result.getProvider()));
                        return result;
                    }
                }
            } catch (Exception e) {
                log.error("Failed to read AI grade cache", e);
            }
            return null;
        }
    }

    private void writeCache(String key, AiGradeResult result) {
        synchronized (cacheLock) {
            try {
                Path path = cachePath();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("key", key);
                row.put("provider", result.getProvider());
                row.put("result", result.toMap());
                Files.writeString(path, mapper.writeValueAsString(row) + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        Files.exists(path)
                                ? new java.nio.file.OpenOption[]{java.nio.file.StandardOpenOption.APPEND}
                                : new java.nio.file.OpenOption[]{java.nio.file.StandardOpenOption.CREATE});
            } catch (Exception e) {
                log.error("Failed to write AI grade cache", e);
            }
        }
    }

    private Path cachePath() {
        String explicit = System.getProperty("oes.ai.cache");
        if (explicit == null || explicit.isBlank()) {
            explicit = System.getenv("OES_AI_CACHE");
        }
        return Path.of(explicit == null || explicit.isBlank() ? "ai-answer-cache.jsonl" : explicit);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private Set<String> tokens(String value) {
        Set<String> result = new LinkedHashSet<>();
        for (String token : value.split("[\\s,.;:!?，。；：！？、()（）]+")) {
            if (token.length() >= 2) {
                result.add(token);
            }
        }
        return result;
    }

    private String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT).trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
