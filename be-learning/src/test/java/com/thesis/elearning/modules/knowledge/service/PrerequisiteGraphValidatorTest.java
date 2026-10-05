package com.thesis.elearning.modules.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link PrerequisiteGraphValidator}.
 *
 * <p>The curriculum graph must stay a DAG: if a learner is required to know A
 * before B and B before A, neither can ever be unlocked, so a cycle is a
 * correctness bug and not a cosmetic one.
 */
class PrerequisiteGraphValidatorTest {

    private final PrerequisiteGraphValidator validator = new PrerequisiteGraphValidator();

    /** Builds an adjacency map from {@code from -> to} pairs. */
    private static Map<Long, List<Long>> edges(final Long... pairs) {
        final Map<Long, List<Long>> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.computeIfAbsent(pairs[i], key -> new java.util.ArrayList<>())
                    .add(pairs[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("an empty graph is acyclic")
    void emptyGraph() {
        assertThat(validator.findCycle(Map.of())).isEmpty();
        assertThat(validator.isAcyclic(Map.of())).isTrue();
    }

    @Test
    @DisplayName("a single node with no edges is acyclic")
    void isolatedNode() {
        assertThat(validator.isAcyclic(Map.of(1L, List.of()))).isTrue();
    }

    @Test
    @DisplayName("a simple chain is acyclic")
    void chain() {
        assertThat(validator.isAcyclic(edges(1L, 2L, 2L, 3L, 3L, 4L))).isTrue();
    }

    @Test
    @DisplayName("a diamond (shared prerequisites) is acyclic")
    void diamond() {
        // 1 -> 2, 1 -> 3, 2 -> 4, 3 -> 4. Reaching 4 twice must NOT be read
        // as a cycle; the second visit happens from a finished branch.
        assertThat(validator.isAcyclic(edges(1L, 2L, 1L, 3L, 2L, 4L, 3L, 4L))).isTrue();
    }

    @Test
    @DisplayName("detects a two-node cycle")
    void twoNodeCycle() {
        final Optional<List<Long>> cycle = validator.findCycle(edges(1L, 2L, 2L, 1L));

        assertThat(cycle).isPresent();
        assertThat(cycle.get()).containsExactly(1L, 2L, 1L);
    }

    @Test
    @DisplayName("detects a longer cycle and names the whole chain")
    void threeNodeCycle() {
        final Optional<List<Long>> cycle = validator.findCycle(edges(1L, 2L, 2L, 3L, 3L, 1L));

        assertThat(cycle).isPresent();
        // The reported path starts at the entry point of the cycle and closes.
        assertThat(cycle.get()).startsWith(1L).endsWith(1L).hasSize(4);
    }

    @Test
    @DisplayName("detects a cycle that does not include the lowest id")
    void cycleInLaterBranch() {
        // 1 -> 2 is clean; 3 -> 4 -> 5 -> 3 is the real cycle.
        final Optional<List<Long>> cycle =
                validator.findCycle(edges(1L, 2L, 3L, 4L, 4L, 5L, 5L, 3L));

        assertThat(cycle).isPresent();
        assertThat(cycle.get()).containsExactly(3L, 4L, 5L, 3L);
    }

    @Test
    @DisplayName("a cycle reachable only through a long prefix still reports the cycle itself")
    void cycleAfterLongPrefix() {
        final Optional<List<Long>> cycle =
                validator.findCycle(edges(1L, 2L, 2L, 3L, 3L, 4L, 4L, 2L));

        assertThat(cycle).isPresent();
        // The 1 -> 2 prefix is a legitimate ancestor and must be excluded.
        assertThat(cycle.get()).doesNotContain(1L).containsExactly(2L, 3L, 4L, 2L);
    }

    @Test
    @DisplayName("the same graph always yields the same reported cycle")
    void deterministicReporting() {
        final Map<Long, List<Long>> graph = edges(1L, 2L, 2L, 3L, 3L, 1L, 2L, 4L);

        final Optional<List<Long>> first = validator.findCycle(graph);
        final Optional<List<Long>> second = validator.findCycle(graph);

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("recognises a self-loop")
    void selfLoop() {
        assertThat(validator.isSelfLoop(1L, 1L)).isTrue();
        assertThat(validator.isSelfLoop(1L, 2L)).isFalse();
    }

    @Test
    @DisplayName("collects every node referenced by the adjacency map")
    void allNodes() {
        final Map<Long, List<Long>> graph = new HashMap<>(edges(1L, 2L, 2L, 3L));

        assertThat(PrerequisiteGraphValidator.allNodes(graph))
                .containsExactlyInAnyOrder(1L, 2L, 3L);
    }
}