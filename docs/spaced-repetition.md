# Thuật toán SM-2

Nguồn chân lý duy nhất là `ReviewService.applySpacedRepetition` trong
[ReviewService.java](../backend/src/main/java/com/flashmind/service/ReviewService.java).
Tham chiếu gốc: [SuperMemo 2](https://en.wikipedia.org/wiki/SuperMemo#Description_of_SM-2_algorithm).

## Thang điểm chất lượng

Người học tự chấm mức nhớ từ 0 đến 5 sau khi lật thẻ.

| Quality | Ý nghĩa | Tác động |
|---------|---------|----------|
| 0 | Quên hoàn toàn | Reset: `repetitionCount = 0`, `interval = 1` |
| 1 | Sai, mơ hồ | Reset |
| 2 | Sai, gần đúng | Reset |
| 3 | Đúng nhưng khó | Tăng interval, EF giảm |
| 4 | Đúng, hơi nghĩ | Tăng interval, EF giữ nguyên |
| 5 | Đúng dễ dàng | Tăng interval, EF tăng |

Ngưỡng đúng/sai là `quality >= 3`. Ngưỡng này cũng quyết định `correctCount` của
`StudySession`.

## Trình tự tính toán

Thứ tự các bước quan trọng — **interval được tính bằng EF cũ, EF chỉ cập nhật sau đó**.

```java
if (quality >= 3) {
    // 1a. Trả lời đúng: tăng khoảng cách
    if      (repetitionCount == 0) interval = 1;
    else if (repetitionCount == 1) interval = 6;
    else                           interval = round(interval * easinessFactor);
    repetitionCount += 1;
} else {
    // 1b. Trả lời sai: quay về đầu
    repetitionCount = 0;
    interval = 1;
}

// 2. Cập nhật easiness factor (áp dụng cho cả hai nhánh), sàn 1.3
easinessFactor = max(1.3, easinessFactor + 0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));

// 3. Lên lịch
nextReviewDate = hôm nay + interval ngày;
```

Giá trị khởi tạo của một `CardReview` mới: `interval = 0`, `easinessFactor = 2.5`,
`repetitionCount = 0`, `nextReviewDate = hôm nay`.

## Biến thiên của EF theo quality

| Quality | Thay đổi EF |
|---------|-------------|
| 5 | `+0.10` |
| 4 | `0` |
| 3 | `−0.14` |
| 2 | `−0.32` |
| 1 | `−0.54` |
| 0 | `−0.80` |

EF không bao giờ xuống dưới **1.3**. Không có trần trên.

## Ví dụ: luôn trả lời quality = 4

EF giữ nguyên 2.5 vì quality = 4 không đổi EF.

| Lần ôn | `repetitionCount` trước | interval mới | Ôn lại sau |
|--------|-------------------------|--------------|------------|
| 1 | 0 | 1 | 1 ngày |
| 2 | 1 | 6 | 6 ngày |
| 3 | 2 | round(6 × 2.5) = 15 | 15 ngày |
| 4 | 3 | round(15 × 2.5) = 38 | 38 ngày |
| 5 | 4 | round(38 × 2.5) = 95 | 95 ngày |

Sau lần thứ 5, `repetitionCount` đạt 5 → thẻ được coi là **đã thuộc**.

Nếu ở bất kỳ lần nào người học chấm dưới 3, `repetitionCount` về 0 và interval về 1 —
thẻ bắt đầu lại chu trình, nhưng EF đã giảm vẫn được giữ, nên các chu kỳ sau sẽ ngắn hơn.

## Ngưỡng thành thạo

```java
MASTERY_THRESHOLD = 5   // repetitionCount >= 5 → đã thuộc
```

Hằng số này **bị lặp lại ở hai nơi**: `ReviewService` (dùng cho `isMastered` trong response
submit) và `AnalyticsService` (dùng để đếm `masteredCards`). Đổi giá trị thì phải đổi cả hai.

## Khi thay đổi thuật toán

`ReviewServiceTest` có 9 test; các test SM-2 khóa cứng đúng những con số ở trên
(1 → 6 → 15, sàn EF 1.3, hành vi reset, ngưỡng thành thạo). Mọi thay đổi trong `applySpacedRepetition`
**bắt buộc phải cập nhật `ReviewServiceTest` cùng lúc**, và cập nhật cả tài liệu này.
