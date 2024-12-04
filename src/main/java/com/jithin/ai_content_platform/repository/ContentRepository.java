package com.jithin.ai_content_platform.repository;

import com.jithin.ai_content_platform.model.Content;
import com.jithin.ai_content_platform.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ContentRepository extends JpaRepository<Content, Long> {
    List<Content> findByUserId(Long userId);

    long countByUser(User user);

    @Query("SELECT AVG(c.rating) FROM Content c WHERE c.user = :user AND c.rating IS NOT NULL")
    Double averageRatingByUser(@Param("user") User user);

    long countByUserAndCreatedDateBetween(User user, LocalDateTime start, LocalDateTime end);}