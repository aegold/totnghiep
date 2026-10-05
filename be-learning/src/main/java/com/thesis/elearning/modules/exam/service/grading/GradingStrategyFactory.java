package com.thesis.elearning.modules.exam.service.grading;

import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import com.thesis.elearning.modules.question.domain.QuestionType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Resolves the strategy that grades a given renderer.
 *
 * <h2>Why the map is built from the injected beans</h2>
 * Strategies are discovered by type, not listed by hand. A hard-coded switch
 * would compile happily while a new renderer shipped with no grader, and the
 * omission would surface as a 500 during a live exam rather than at startup.
 * Building the index from whatever Spring injected means an unsupported type is
 * a startup failure instead.
 *
 * <p>A duplicate registration is likewise rejected here rather than silently
 * resolved to whichever bean came last.
 */
@Component
public class GradingStrategyFactory {

    private final Map<QuestionType, GradingStrategy> byType = new EnumMap<>(QuestionType.class);

    public GradingStrategyFactory(final List<GradingStrategy> strategies) {
        for (final GradingStrategy strategy : strategies) {
            final GradingStrategy clash = byType.put(strategy.supports(), strategy);
            if (clash != null) {
                throw new IllegalStateException(
                        "Hai chiến lược chấm điểm cùng phục vụ "
                                + strategy.supports()
                                + ": "
                                + clash.getClass().getSimpleName()
                                + " và "
                                + strategy.getClass().getSimpleName());
            }
        }
        for (final QuestionType type : QuestionType.values()) {
            if (!byType.containsKey(type)) {
                throw new IllegalStateException(
                        "Thiếu chiến lược chấm điểm cho loại câu hỏi: " + type);
            }
        }
    }

    /**
     * The grader for {@code type}.
     *
     * @throws AppException 400 when the type is outside the supported set. Kept
     *                      as a runtime failure with the shared Vietnamese
     *                      wording because a row authored before a renderer was
     *                      retired can still reach this point.
     */
    public GradingStrategy forType(final QuestionType type) {
        final GradingStrategy strategy = byType.get(type);
        if (strategy == null) {
            throw new AppException(
                    ErrorCode.BAD_REQUEST,
                    "Loại câu hỏi không được hỗ trợ trong hệ thống: " + type + ".");
        }
        return strategy;
    }

    /** How many renderers can currently be graded; used by the coverage test. */
    public int size() {
        return byType.size();
    }
}
