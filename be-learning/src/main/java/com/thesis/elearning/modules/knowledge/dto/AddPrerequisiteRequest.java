package com.thesis.elearning.modules.knowledge.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Body for linking one node as a prerequisite of another.
 *
 * <p>The pair is rejected by {@code PrerequisiteGraphValidator} before it is
 * persisted, so a circular dependency can never reach the table.
 *
 * @param prerequisiteNodeId the node that must be learned first.
 * @param dependentNodeId    the node being unlocked.
 */
public record AddPrerequisiteRequest(
        @NotNull(message = "Vui lòng chọn nút kiến thức tiên quyết")
                Long prerequisiteNodeId,

        @NotNull(message = "Vui lòng chọn nút kiến thức phụ thuộc")
                Long dependentNodeId) {}