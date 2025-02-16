package com.jithin.ai_content_platform.repository;

import com.jithin.ai_content_platform.model.Content;
import com.jithin.ai_content_platform.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ContentRepository extends JpaRepository<Content, Long> {
    List<Content> findByCategoryAndContentType(String category, String contentType);
    
    @Query("SELECT c FROM Content c WHERE c.rating >= :minRating AND c.category = :category")
    List<Content> findHighRatedContentByCategory(@Param("minRating") Integer minRating, @Param("category") String category);
    
    @Query("SELECT c FROM Content c WHERE c.user.id = :userId AND c.rating IS NOT NULL ORDER BY c.createdAt DESC")
    List<Content> findUserContentWithFeedback(@Param("userId") Long userId);
    List<Content> findByUserId(Long userId);

    long countByUser(User user);

    @Query("SELECT AVG(c.rating) FROM Content c WHERE c.user = :user AND c.rating IS NOT NULL")
    Double averageRatingByUser(@Param("user") User user);

    long countByUserAndCreatedAtBetween(User user, LocalDateTime start, LocalDateTime end);

    /**
     * Find all content by user ordered by creation date descending
     */
    List<Content> findByUserOrderByCreatedAtDesc(User user);

    /**
     * Find all content by user and category
     */
    List<Content> findByUserAndCategory(User user, String category);

   
    @Query("SELECT c FROM Content c WHERE c.user = :user")
    List<Content> findByUser(@Param("user") User user);

    /**
     * Find top performing content by user
     */
    @Query(value = "SELECT c.* FROM content c WHERE c.user_id = :#{#user.id} ORDER BY c.engagement DESC LIMIT 10", nativeQuery = true)
    List<Content> findTop10ByUserOrderByMetricsDesc(@Param("user") User user);

    /**
     * Find content by category ordered by metrics
     */
    @Query(value = "SELECT c.* FROM content c WHERE c.category = :category ORDER BY c.engagement DESC", nativeQuery = true)
    List<Content> findByCategoryOrderByMetricsDesc(@Param("category") String category);

    /**
     * Find the 100 most recent content items
     */
    List<Content> findTop100ByOrderByCreatedAtDesc();

    /**
     * Find content by publish hour and day of week
     */
    @Query(value = "SELECT * FROM content WHERE EXTRACT(HOUR FROM scheduled_publish_time) = :hour AND EXTRACT(DOW FROM scheduled_publish_time) = :dayOfWeek", nativeQuery = true)
    List<Content> findByPublishHourAndDayOfWeek(@Param("hour") int hour, @Param("dayOfWeek") int dayOfWeek);

    /**
     * Find all content variations associated with a specific A/B test
     */
    List<Content> findByTestId(String testId);

    /**
     * Find recent content by user
     */
    List<Content> findTop5ByUserOrderByCreatedAtDesc(User user);

    List<Content> findByContentBodyContaining(String contentBody);

    /**
     * Find content by region ordered by creation date descending
     */
    List<Content> findByRegionOrderByCreatedAtDesc(String region);

    @Query("SELECT AVG(c.engagement) FROM Content c WHERE c.user = :user AND c.engagement IS NOT NULL")
    Double averageEngagementByUser(@Param("user") User user);

    
    
    @Query("SELECT c FROM Content c WHERE c.createdAt >= :startTime ORDER BY c.createdAt DESC")
    List<Content> findContentFromLastNHours(@Param("startTime") LocalDateTime startTime);
    
    @Query(value = "SELECT * FROM content ORDER BY created_at DESC LIMIT :limit OFFSET :offset", 
           nativeQuery = true)
    List<Content> findAllWithPagination(@Param("offset") int offset, @Param("limit") int limit);
    
    List<Content> findByCategory(String category);
    
    @Query("SELECT c FROM Content c WHERE c.contentBody LIKE %:topic%")
    List<Content> findByTopic(@Param("topic") String topic);

    List<Content> findByKeywordsLike(String keywords);

    Page<Content> findAll(Pageable pageable);
    Page<Content> findByCategory(String category, Pageable pageable);
    Page<Content> findByUser(User user, Pageable pageable);
}