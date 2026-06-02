package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ai_provider_config")
public class AiProviderSetting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "provider_id")
    private Integer providerId;

    @Size(max = 80)
    @Column(nullable = false)
    private String name;

    @Size(max = 40)
    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private Integer enabled;

    @Size(max = 600)
    @Column(length = 600)
    private String endpoint;

    @Size(max = 1000)
    @Column(name = "api_key", length = 1000)
    private String apiKey;

    @Size(max = 80)
    @Column(name = "api_key_env")
    private String apiKeyEnv;

    @Size(max = 100)
    private String model;

    private Double temperature;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;
}
