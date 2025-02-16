package com.jithin.ai_content_platform.repository;

import com.jithin.ai_content_platform.model.ChatHistory;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import java.util.List;

public interface ChatHistoryRepository extends CrudRepository<ChatHistory, String>, PagingAndSortingRepository<ChatHistory, String> {
    List<ChatHistory> findByUserIdOrderByTimestampDesc(String userId);
    List<ChatHistory> findByUserIdOrderByTimestampDesc(String userId, org.springframework.data.domain.Pageable pageable);
}
