package com.thesis.elearning.modules.analytics.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The four score bands a mark sheet is summarised into.
 *
 * <h2>Why this enum owns the Vietnamese labels</h2>
 * The histogram query must emit all four rows even when a band is empty - a
 * chart that silently loses its first bar draws a shorter axis and misleads the
 * teacher reading it. Getting those zero rows out of SQL is easiest when the
 * query groups by a small integer and returns that integer, not a label. The
 * wording then lives here, in Java, where it is directly assertable.
 *
 * <h2>Boundaries are half-open, so every score lands in exactly one band</h2>
 * Each band owns {@code [lowerInclusive, upperExclusive)}; the top band is the
 * single exception and is closed, because a perfect score must not fall outside
 * the whole scale. A scheme that closed both ends would double-count exactly 40,
 * 60 and 80 - the three values a teacher is most likely to test with - and the
 * histogram would then total more submissions than were handed in.
 *
 * <p>The SQL mirrors these boundaries through {@link #WEAK_UPPER},
 * {@link #AVERAGE_UPPER} and {@link #GOOD_UPPER}, which the service binds as
 * parameters rather than as literals. Two views of one rule exist here on
 * purpose - {@link #POOL} feeds the query's row skeleton, and {@link #of}
 * reproduces the {@code CASE} clause so the rule can be asserted without a
 * database.
 *
 * <p>Labels are stated on the ten-point scale because that is how Vietnamese
 * schools report marks: "Yếu / Kém [0 - 4.0 điểm]" and "0% - 39.99%" are the
 * same band in two registers.
 */
public enum ScoreBucket {

    /** 0 - 39.99%. */
    WEAK("Yếu / Kém [0 - 4.0 điểm]", 0, 40),

    /** 40 - 59.99%. */
    AVERAGE("Trung bình [4.0 - 6.0 điểm]", 40, 60),

    /** 60 - 79.99%. */
    GOOD("Khá [6.0 - 8.0 điểm]", 60, 80),

    /** 80 - 100%. Closed at the top, so a perfect score cannot escape the scale. */
    EXCELLENT("Giỏi / Xuất sắc [8.0 - 10.0 điểm]", 80, 101);

    /** Exclusive upper bound of {@link #WEAK}; the {@code < 40} test in SQL. */
    public static final BigDecimal WEAK_UPPER = BigDecimal.valueOf(40);

    /** Exclusive upper bound of {@link #AVERAGE}; the {@code < 60} test in SQL. */
    public static final BigDecimal AVERAGE_UPPER = BigDecimal.valueOf(60);

    /** Exclusive upper bound of {@link #GOOD}; the {@code < 80} test in SQL. */
    public static final BigDecimal GOOD_UPPER = BigDecimal.valueOf(80);

    /**
     * Every band in ascending order.
     *
     * <p>Its length is the number of rows the query's {@code UNION ALL}
     * skeleton must produce, which {@code AnalyticsServiceTest} asserts against
     * the query text so the two cannot drift apart unnoticed.
     */
    public static final ScoreBucket[] POOL = values();

    private final String label;
    private final int lowerInclusive;
    private final int upperExclusive;

    ScoreBucket(final String label, final int lowerInclusive, final int upperExclusive) {
        this.label = label;
        this.lowerInclusive = lowerInclusive;
        this.upperExclusive = upperExclusive;
    }

    /** Vietnamese label rendered on the chart axis. Safe to render directly. */
    public String label() {
        return label;
    }

    /** Inclusive lower bound, as a percentage. */
    public int lowerInclusive() {
        return lowerInclusive;
    }

    /** Exclusive upper bound, as a percentage. */
    public int upperExclusive() {
        return upperExclusive;
    }

    /** Ordinal used as the SQL grouping key. */
    public int index() {
        return ordinal();
    }

    /**
     * Resolves the band a percentage belongs to, mirroring the SQL {@code CASE}.
     *
     * @param percentage score as a percentage; {@code null} counts as zero.
     * @return the owning band, never {@code null}.
     */
    public static ScoreBucket of(final BigDecimal percentage) {
        final double value = percentage == null ? 0d : percentage.doubleValue();
        for (final ScoreBucket bucket : POOL) {
            // The top band is closed so exactly 100 - and any value a rounding
            // artefact pushes a hair past it - still lands on a visible bar.
            if (value < bucket.upperExclusive || bucket == EXCELLENT) {
                return bucket;
            }
        }
        return EXCELLENT;
    }

    /** Resolves a band from the integer the query grouped on. */
    public static ScoreBucket ofIndex(final int index) {
        if (index < 0 || index >= POOL.length) {
            throw new IllegalArgumentException("Không tìm thấy nhóm điểm có chỉ số: " + index);
        }
        return POOL[index];
    }

    /**
     * Converts a percentage to the ten-point scale the labels speak in.
     *
     * <p>Only ever used for rendering: {@code 35} becomes {@code 3.5}. Kept out of
     * the comparison path so the arithmetic stays scale-free across papers worth
     * different totals.
     */
    public static BigDecimal toTenPointScale(final BigDecimal percentage) {
        return (percentage == null ? BigDecimal.ZERO : percentage)
                .divide(BigDecimal.TEN, 1, RoundingMode.HALF_UP);
    }
}