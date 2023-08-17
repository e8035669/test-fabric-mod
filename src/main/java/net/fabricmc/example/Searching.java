package net.fabricmc.example;

import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public abstract class Searching<Node extends SearchNode> {

    public abstract double heuristic(Node node1, Node node2);
    public boolean isEnd(Node node1, Node node2) {
        return heuristic(node1, node2) == 0.0;
    }

    public abstract Iterable<Node> findNeighbors(Node node);

    public abstract double getCostOf(Node node1, Node node2);

    public Optional<List<Node>> search(Node start, Node end) {
        PriorityQueue<PriSearchNode> frontier = new PriorityQueue<>();
        frontier.add(new PriSearchNode(0, start));
        Map<Node, Optional<Node>> cameFrom = new HashMap<>();
        Map<Node, Double> costSoFar = new HashMap<>();
        cameFrom.put(start, Optional.empty());
        costSoFar.put(start, 0.0);

        Optional<Node> endNode = Optional.empty();

        while (!frontier.isEmpty()) {
            PriSearchNode current = frontier.poll();

            if (cameFrom.size() > 500 || frontier.size() > 500) {
                break;
            }

            if (this.isEnd(current.node, end)) {
                endNode = Optional.of(current.node);
                break;
            }

            for (Node next : findNeighbors(current.node)) {
                double newCost = costSoFar.get(current.node) + getCostOf(current.node, next);
                if (!cameFrom.containsKey(next) || newCost < costSoFar.get(next)) {
                    costSoFar.put(next, newCost);
                    double priority = newCost + heuristic(next, end);
                    frontier.add(new PriSearchNode(priority, next));
                    cameFrom.put(next, Optional.of(current.node));
                }
            }
        }

        if (endNode.isEmpty()) {
            return Optional.empty();
        } else {
            List<Node> reversedPath = new ArrayList<>();
            reversedPath.add(endNode.get());

            Optional<Node> fromBlock = cameFrom.get(endNode.get());
            while (fromBlock.isPresent()) {
                reversedPath.add(fromBlock.get());
                fromBlock = cameFrom.get(fromBlock.get());
            }

            return Optional.of(Lists.reverse(reversedPath));
        }
    }

    private class PriSearchNode implements Comparable<PriSearchNode> {
        public double priority;
        public Node node;

        public PriSearchNode(double priority, Node node) {
            this.priority = priority;
            this.node = node;
        }

        @Override
        public int compareTo(@NotNull Searching.PriSearchNode o) {
            return Double.compare(this.priority, o.priority);
        }
    }
}
