package com.jithin.ai_content_platform.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;
import java.util.HashMap;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Region {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code; // e.g., "US", "UK", "JP"

    @Column(nullable = false)
    private String name; // e.g., "United States", "United Kingdom", "Japan"

    @Column(nullable = false)
    private String primaryLanguage; // e.g., "en-US", "en-GB", "ja-JP"

    @ElementCollection
    private Map<String, String> culturalPreferences = new HashMap<>(); // Stores cultural-specific preferences

    @Column
    private String timeZone;

    @ElementCollection
    private Map<String, String> seoRequirements = new HashMap<>(); // Region-specific SEO requirements

    @ElementCollection
    private Map<String, String> contentGuidelines = new HashMap<>(); // Region-specific content guidelines

    @Column
    private boolean active = true;

    // Constructors for common use cases
    public Region(String code, String name, String primaryLanguage) {
        this.code = code;
        this.name = name;
        this.primaryLanguage = primaryLanguage;
    }

    // Helper methods
    public void addCulturalPreference(String key, String value) {
        this.culturalPreferences.put(key, value);
    }

    public void addSeoRequirement(String key, String value) {
        this.seoRequirements.put(key, value);
    }

    public void addContentGuideline(String key, String value) {
        this.contentGuidelines.put(key, value);
    }

    public Map<String, Object> getRegionalRequirements() {
        Map<String, Object> requirements = new HashMap<>();
        requirements.put("culturalPreferences", culturalPreferences);
        requirements.put("seoRequirements", seoRequirements);
        requirements.put("contentGuidelines", contentGuidelines);
        requirements.put("primaryLanguage", primaryLanguage);
        requirements.put("timeZone", timeZone);
        return requirements;
    }
}
