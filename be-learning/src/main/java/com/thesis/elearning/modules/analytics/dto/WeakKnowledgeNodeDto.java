package com.thesis.elearning.modules.analytics.dto;

import java.math.BigDecimal;

/**
 * A knowledge node the cohort is weakest at.
 *
 * <p>Reads {@code knowledge_node_mastery}, which the grading stage materialises.
 * The table exists precisely because there is no exam-to-node link: without it,
 * attributing an attempt to a competency would mean re-joining questions,
 * papers and submissions on every dashboard load.
 *
 * @param nodeId         the {@code knowledge_nodes.node_id}.
 * @param nodeName       display name.
 * @param averageMastery mean mastery score across the students who have a row
 *                       for this node, as a percentage.
 * @param studentCount   how many students contributed to that average. Low
 *                       counts mean the figure is thin evidence and the row
 *                       sorts below better-attested ones at equal mastery.
 */
public record WeakKnowledgeNodeDto(
        Long nodeId, String nodeName, BigDecimal averageMastery, int studentCount) {}