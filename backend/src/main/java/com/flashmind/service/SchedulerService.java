package com.flashmind.service;

import com.flashmind.repository.CardReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulerService {

    private final CardReviewRepository cardReviewRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Mỗi ngày lúc 00:00, cache danh sách due cards của tất cả user vào Redis
     * giúp endpoint /reviews/today trả về nhanh hơn.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void cacheDailyDueCards() {
        log.info("Bắt đầu cache due cards cho ngày {}", LocalDate.now());
        List<Long> userIds = cardReviewRepository.findAllDistinctUserIds();
        int total = 0;
        for (Long userId : userIds) {
            List<Long> dueCardIds = cardReviewRepository.findDueCardIds(userId, LocalDate.now());
            if (!dueCardIds.isEmpty()) {
                redisTemplate.opsForValue().set(
                    "due_cards:" + userId,
                    dueCardIds,
                    Duration.ofHours(25)
                );
                total += dueCardIds.size();
            }
        }
        log.info("Cache xong {} due cards cho {} users", total, userIds.size());
    }
}
