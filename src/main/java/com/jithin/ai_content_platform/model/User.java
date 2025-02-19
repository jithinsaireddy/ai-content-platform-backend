package com.jithin.ai_content_platform.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User implements UserDetails {

    private static final Logger logger = LoggerFactory.getLogger(User.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String preferences = "{}";

    @Column(nullable = false)
    private String industry;

    @Column(columnDefinition = "TEXT")
    private String writingStyleSample;

    @ManyToMany
    @JoinTable(
            name = "user_achievements",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "achievement_id")
    )
    private Set<Achievement> achievements = new HashSet<>();

    @Column
    private String subscriptionLevel; // e.g., "FREE", "PREMIUM", "ENTERPRISE"

    @Column(name = "created_at")
    @Temporal(TemporalType.TIMESTAMP)
    private java.util.Date createdAt;

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private java.util.Date updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = new java.util.Date();
        updatedAt = new java.util.Date();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new java.util.Date();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public Map<String, Object> getPreferences() {
        try {
            if (preferences == null || preferences.isEmpty()) {
                return new HashMap<>();
            }
            return objectMapper.readValue(preferences, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            logger.error("Error parsing user preferences: ", e);
            return new HashMap<>();
        }
    }

    public void setPreferences(Map<String, Object> preferences) {
        try {
            this.preferences = objectMapper.writeValueAsString(preferences);
        } catch (JsonProcessingException e) {
            logger.error("Error setting user preferences: ", e);
            this.preferences = "{}";
        }
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }
}
// Additional fields for personalization
// private String stylePreference;
// private int points;

// Roles and authorities can be added here
