package com.jithin.ai_content_platform.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@Table(name = "user_achievements")
public class UserAchievement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String achievementName;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private boolean isAchieved;

    @Column(name = "date_achieved")
    private LocalDateTime dateAchieved;
}
