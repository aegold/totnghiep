package com.thesis.elearning.modules.knowledge.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Depth-first cycle detection over the prerequisite graph.
 *
 * <h2>The rule being enforced</h2>
 * An edge {@code A -> B} reads "A must be mastered before B". The graph must stay
 * a DAG. If following edges can ever come back to a node already on the current
 * path, the curriculum is unsatisfiable: the learner would need A before B and B
 * before A, so neither could ever be unlocked.
 *
 * <h2>Why three colours and not a visited set</h2>
 * A single "seen" set detects a node visited twice but cannot distinguish
 * "reached again from a different branch" (harmless, converges) from "reached
 * again while still on the current stack" (a back edge, i.e. a cycle). The stack
 * colour is what makes the second case detectable.
 *
 * <p>Node ids are visited in sorted order so the reported cycle is
 * deterministic; otherwise the same invalid graph could produce different
 * messages on different runs, which makes a bug report unreproducible.
 */
@Component
public class PrerequisiteGraphValidator {

    /** WHITE = unvisited, GREY = on the current path, BLACK = fully explored. */
    private enum Colour {
        WHITE,
        GREY,
        BLACK
    }

    /** Mutable DFS bookkeeping for one validation pass. */
    private static final class Walk {
        private final Map<Long, Colour> colours = new HashMap<>();
        private final List<Long> path = new ArrayList<>();
        private final Deque<Long> cycle = new ArrayDeque<>();
    }

    /**
     * Finds the first prerequisite cycle in {@code edges}.
     *
     * @param edges adjacency list: node id to the nodes that depend on it.
     * @return the node ids forming the cycle in dependency order, or empty when
     *         the graph is acyclic. An empty result means SAFE, not "not run".
     */
    public Optional<List<Long>> findCycle(final Map<Long, List<Long>> edges) {

        final Walk walk = new Walk();
        for (final Long nodeId : sorted(edges.keySet())) {
            if (walk.colours.getOrDefault(nodeId, Colour.WHITE) == Colour.WHITE) {
                final Optional<List<Long>> cycle = visit(nodeId, edges, walk);
                if (cycle.isPresent()) {
                    return cycle;
                }
            }
        }
        return Optional.empty();
    }

    /** Recursive DFS. Returns the cycle when one is discovered from this node. */
    private Optional<List<Long>> visit(
            final Long nodeId,
            final Map<Long, List<Long>> edges,
            final Walk walk) {

        walk.colours.put(nodeId, Colour.GREY);
        walk.path.add(nodeId);

        final List<Long> dependents = edges.getOrDefault(nodeId, List.of());
        for (final Long dependent : sorted(dependents)) {
            final Colour colour = walk.colours.getOrDefault(dependent, Colour.WHITE);

            if (colour == Colour.GREY) {
                // Back edge: `dependent` is already on the current path.
                return Optional.of(extractCycle(walk.path, dependent));
            }
            if (colour == Colour.WHITE) {
                final Optional<List<Long>> cycle = visit(dependent, edges, walk);
                if (cycle.isPresent()) {
                    return cycle;
                }
            }
        }

        // Fully explored: no cycle reachable from here.
        walk.colours.put(nodeId, Colour.BLACK);
        walk.path.remove(walk.path.size() - 1);
        return Optional.empty();
    }

    /**
     * Extracts the cycle that closes at {@code start}.
     *
     * <p>Everything before {@code start} on the path is a legitimate ancestor
     * that merely leads into the cycle, so it is discarded.
     */
    private static List<Long> extractCycle(final List<Long> path, final Long start) {
        final int from = path.indexOf(start);
        if (from < 0) {
            return List.copyOf(path);
        }
        final List<Long> cycle = new ArrayList<>(path.subList(from, path.size()));
        // Close the loop visually so the message reads A -> B -> A.
        cycle.add(start);
        return List.copyOf(cycle);
    }

    /** Sorted copy, so traversal order (and therefore the report) is stable. */
    private static List<Long> sorted(final Iterable<Long> ids) {
        final List<Long> copy = new ArrayList<>();
        ids.forEach(copy::add);
        copy.sort(null);
        return copy;
    }

    /** Convenience predicate for callers that only need a yes/no answer. */
    public boolean isAcyclic(final Map<Long, List<Long>> edges) {
        return findCycle(edges).isEmpty();
    }

    /** Guard for the trivial self-loop the message has to name explicitly. */
    public boolean isSelfLoop(final Long prerequisiteId, final Long dependentId) {
        return prerequisiteId.equals(dependentId);
    }

    /** Distinct node ids referenced by an adjacency map. */
    public static Set<Long> allNodes(final Map<Long, List<Long>> edges) {
        final Set<Long> nodes = new HashSet<>(edges.keySet());
        edges.values().forEach(nodes::addAll);
        return nodes;
    }
}