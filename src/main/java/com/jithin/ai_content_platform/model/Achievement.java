// src/main/java/com/jithin/ai_content_platform/model/Achievement.java

package com.jithin.ai_content_platform.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Set;

@Entity
@Table(name = "achievements")
@Data
public class Achievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String description;

    @ManyToMany(mappedBy = "achievements")
    private Set<User> users;
}