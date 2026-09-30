package com.bookshop.app.config;

import com.bookshop.app.model.dto.response.AvatarPresetResponse;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Binds app.avatars.preset from application.yml.
 * No DB query — purely config-driven.
 */
@Configuration
@ConfigurationProperties(prefix = "app.avatars")
@Getter
@Setter
public class AvatarConfig {

    private List<AvatarPresetResponse> preset;

    public boolean isValidKey(String key) {
        return preset != null && preset.stream().anyMatch(a -> a.getKey().equals(key));
    }

    public String getUrlByKey(String key) {
        if (preset == null) return null;
        return preset.stream()
                .filter(a -> a.getKey().equals(key))
                .map(AvatarPresetResponse::getUrl)
                .findFirst()
                .orElse(null);
    }
}
