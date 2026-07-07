package com.network;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
/**
 * A simple weighted graph implementation with:
 *  - Node: a vertex with an id and (x, y) coordinates
 *  - Edge: a weighted connection between two nodes
 *  - distance(): Euclidean distance between two nodes' coordinates
 *  - getRandomNeighbor(): picks a random neighbor of a given node
 */
public class WeightedGraph {

    // ---------- Model ----------
    public static class Node {
        String id;
        double x, y; // canvas coordinates

        Node(String id, double x, double y) {
            this.id = id;
            this.x = x;
            this.y = y;
        }
    }

    public static class Edge {
        Node from, to;
        double speedLimit;

        Edge(Node from, Node to, double speedLimit) {
            this.from = from;
            this.to = to;
            this.speedLimit = speedLimit;
        }

        double getWeight() {
            return Graph.distance(from, to) / speedLimit;
        }
    }

    public static class Graph {
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        boolean directed = false;

        void addNode(Node n) {
            nodes.add(n);
        }

        void addEdge(Node a, Node b, double speedLimit) {
            edges.add(new Edge(a, b, speedLimit));
        }

        void removeNode(Node n) {
            nodes.remove(n);
            edges.removeIf(e -> e.from == n || e.to == n);
        }

        void removeEdge(Edge e) {
            edges.remove(e);
        }

        static double distance(Node a, Node b) {
            double dx = a.x - b.x, dy = a.y - b.y;
            return Math.sqrt(dx * dx + dy * dy);
        }

        List<Edge> edgesOf(Node n) {
            List<Edge> result = new ArrayList<>();
            for (Edge e : edges) {
                if (e.from == n || (!directed && e.to == n))
                    result.add(e);
            }
            return result;
        }

        Node randomNeighbor(Node n, Random rnd) {
            List<Edge> es = edgesOf(n);
            if (es.isEmpty())
                return null;
            Edge chosen = es.get(rnd.nextInt(es.size()));
            return chosen.from == n ? chosen.to : chosen.from;
        }
    }

    // Lightweight wrapper for dynamic PriorityQueue sorting without heap corruption
    public static class PQNode implements Comparable<PQNode> {
        final Node node;
        final double score;

        PQNode(Node node, double score) {
            this.node = node;
            this.score = score;
        }

        @Override
        public int compareTo(PQNode o) {
            return Double.compare(this.score, o.score);
        }
    }

}