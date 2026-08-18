# The SM-2 algorithm

The single source of truth is `ReviewService.applySpacedRepetition` in
[ReviewService.java](../backend/src/main/java/com/flashmind/service/ReviewService.java).
Original reference: [SuperMemo 2](https://en.wikipedia.org/wiki/SuperMemo#Description_of_SM-2_algorithm).

## The quality scale

After flipping a card, the learner grades their own recall from 0 to 5.

| Quality | Meaning | Effect |
|---------|---------|--------|
| 0 | Total blackout | Reset: `repetitionCount = 0`, `interval = 1` |
| 1 | Wrong, vague | Reset |
| 2 | Wrong, close | Reset |
| 3 | Correct but hard | Interval grows, EF drops |
| 4 | Correct, some thought | Interval grows, EF unchanged |
| 5 | Correct, easy | Interval grows, EF rises |

The correct/incorrect threshold is `quality >= 3`. The same threshold decides the
`correctCount` of a `StudySession`.

## Order of computation

The order of the steps matters — **the interval is computed with the old EF, and the EF is only
updated afterwards**.

```java
if (quality >= 3) {
    // 1a. Correct answer: stretch the interval
    if      (repetitionCount == 0) interval = 1;
    else if (repetitionCount == 1) interval = 6;
    else                           interval = round(interval * easinessFactor);
    repetitionCount += 1;
} else {
    // 1b. Wrong answer: back to the start
    repetitionCount = 0;
    interval = 1;
}

// 2. Update the easiness factor (both branches), floor 1.3
easinessFactor = max(1.3, easinessFactor + 0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));

// 3. Schedule
nextReviewDate = today + interval days;
```

The initial values of a new `CardReview`: `interval = 0`, `easinessFactor = 2.5`,
`repetitionCount = 0`, `nextReviewDate = today`.

## How EF moves with quality

| Quality | EF change |
|---------|-----------|
| 5 | `+0.10` |
| 4 | `0` |
| 3 | `−0.14` |
| 2 | `−0.32` |
| 1 | `−0.54` |
| 0 | `−0.80` |

EF never goes below **1.3**. There is no upper bound.

## Example: always answering quality = 4

EF stays at 2.5, because quality = 4 does not change it.

| Review | `repetitionCount` before | New interval | Next review in |
|--------|--------------------------|--------------|----------------|
| 1 | 0 | 1 | 1 day |
| 2 | 1 | 6 | 6 days |
| 3 | 2 | round(6 × 2.5) = 15 | 15 days |
| 4 | 3 | round(15 × 2.5) = 38 | 38 days |
| 5 | 4 | round(38 × 2.5) = 95 | 95 days |

After the fifth review `repetitionCount` reaches 5 → the card counts as **mastered**.

If at any point the learner grades below 3, `repetitionCount` goes back to 0 and the interval
back to 1 — the card restarts its cycle, but the EF it already lost is kept, so the following
cycles are shorter.

## The mastery threshold

```java
MASTERY_THRESHOLD = 5   // repetitionCount >= 5 → mastered
```

This constant is **duplicated in two places**: `ReviewService` (for `isMastered` in the submit
response) and `AnalyticsService` (to count `masteredCards`). Changing the value means changing
both.

## When you change the algorithm

`ReviewServiceTest` has 9 tests; the SM-2 ones hard-code exactly the numbers above
(1 → 6 → 15, the EF floor of 1.3, the reset behaviour, the mastery threshold). Any change to
`applySpacedRepetition` **must update `ReviewServiceTest` at the same time**, and update this
document too.
