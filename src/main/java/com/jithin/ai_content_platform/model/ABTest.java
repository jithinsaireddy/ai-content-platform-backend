package com.jithin.ai_content_platform.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ABTest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;

    @Column(nullable = false)
    private String testKey;  // Unique identifier for the test

    @ElementCollection
    @CollectionTable(name = "ab_test_variants")
    @MapKeyColumn(name = "variant_name")
    @Column(name = "variant_value")
    private Map<String, String> variants = new HashMap<>();  // Map of variant names to their values

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    private TestStatus status = TestStatus.DRAFT;

    private double trafficAllocation = 100.0;  // Percentage of traffic included in test
    
    @Column(name = "winning_variant")
    private String winningVariant;

    public enum TestStatus {
        DRAFT,
        RUNNING,
        PAUSED,
        COMPLETED,
        ARCHIVED
    }
}
