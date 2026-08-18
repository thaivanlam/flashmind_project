package com.flashmind.service;

import com.flashmind.repository.CardReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * Every day at 00:00, caches every user's due-card list into Redis so the
     * /reviews/today endpoint can respond faster.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void cacheDailyDueCards() {
        log.info("Starting due-card caching for {}", LocalDate.now());
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

    /**
     * Every day at 03:00, purges orphaned reviews — card_reviews rows pointing at
     * deleted cards. The current delete paths clean up after themselves; this job only
     * clears older data left over from before that bug was fixed.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOrphanedReviews() {
        int deleted = cardReviewRepository.deleteOrphanedReviews();
        if (deleted > 0) {
            log.info("Purged {} orphaned reviews", deleted);
        }
    }
}
