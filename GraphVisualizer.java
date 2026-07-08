package com.network;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

import com.network.WeightedGraph.Edge;
import com.network.WeightedGraph.Graph;
import com.network.WeightedGraph.Node;
import com.network.WeightedGraph.PQNode;

/**
 * Interactive graph visualizer/editor.
 */
public class GraphVisualizer extends JFrame {

    // ---------- UI state ----------
    enum Mode {
        ADD_NODE, ADD_EDGE, MOVE, DELETE
    }

    private final Graph graph = new Graph();
    private Mode mode = Mode.MOVE;
    private Node pendingEdgeSource = null;
    private Node draggingNode = null;
    private Node selectedNode = null;
    private final Set<Node> selectedNeighbors = new HashSet<>();
    private final Set<Edge> selectedEdges = new HashSet<>();
    private final Set<Node> openNodes = new HashSet<>();
    private final Set<Node> closedNodes = new HashSet<>();
    private List<Node> finalPath = new ArrayList<>();
    private List<Node> finalPathNormal = new ArrayList<>();
    private static final double NODE_RADIUS = 20;
    private Node searchStartNode = null;
    private Node searchGoalNode = null;
    private final Set<Edge> finalPathEdges = new HashSet<>();
    private final Set<Edge> finalPathNormalEdges = new HashSet<>();

    public enum HeuristicMode {
        NORMAL, ADAPTED, SUGGESTED
    }

    private double zoom = 1.0;
    private final GraphPanel canvas;
    private final JLabel statusLabel = new JLabel("Mode: MOVE");

    private static final String SAVE_FILE = System.getProperty("user.home") + File.separator
            + "graph_visualizer_data.csv";

    public GraphVisualizer() {
        super("Graph Visualizer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 650);
        setLocationRelativeTo(null);

        canvas = new GraphPanel();

        JScrollPane scrollPane = new JScrollPane(
                canvas,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        add(scrollPane, BorderLayout.CENTER);
        add(buildToolbar(), BorderLayout.NORTH);
        add(statusLabel, BorderLayout.SOUTH);

        canvas.addMouseListener(new CanvasMouseListener());
        canvas.addMouseMotionListener(new CanvasMouseMotionListener());

        canvas.addMouseWheelListener(e -> {
            if (e.getPreciseWheelRotation() < 0) {
                zoom *= 1.1;
            } else {
                zoom /= 1.1;
            }
            zoom = Math.max(0.1, Math.min(5.0, zoom));
            canvas.revalidate();
            canvas.repaint();
        });

        loadGraph();
        updateMaxSpeed();
        canvas.revalidate();
        canvas.repaint();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveGraph();
            }
        });
    }

    private void saveGraph() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(SAVE_FILE))) {
            for (Node n : graph.nodes) {
                writer.printf(Locale.US, "NODE;%s;%f;%f%n", n.id, n.x, n.y);
            }
            for (Edge e : graph.edges) {
                writer.printf(Locale.US, "EDGE;%s;%s;%f%n", e.from.id, e.to.id, e.speedLimit);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void loadGraph() {
        File file = new File(SAVE_FILE);
        if (!file.exists())
            return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty())
                    continue;
                String[] parts = line.split(";");
                if (parts[0].equals("NODE") && parts.length == 4) {
                    graph.addNode(new Node(parts[1], Double.parseDouble(parts[2]), Double.parseDouble(parts[3])));
                } else if (parts[0].equals("EDGE") && parts.length == 4) {
                    Node from = findNodeById(parts[1]);
                    Node to = findNodeById(parts[2]);
                    if (from != null && to != null) {
                        graph.addEdge(from, to, Double.parseDouble(parts[3]));
                    }
                }
            }
        } catch (IOException ex) {
            System.err.println("Failed to load graph: " + ex.getMessage());
        }
    }

    private JToolBar buildToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);

        JButton addNodeBtn = new JButton("Add Node");
        JButton addEdgeBtn = new JButton("Add Edge");
        JButton moveBtn = new JButton("Move");
        JButton deleteBtn = new JButton("Delete");
        JButton pruneBtn = new JButton("Prune Network");
        JButton randomNetworkBtn = new JButton("Random Network...");
        JButton aStarBtn = new JButton("A* Pathfinding");
        JButton analyzeBtn = new JButton("Analyze Heuristics");
        JButton zoomInBtn = new JButton("+");
        JButton zoomOutBtn = new JButton("-");
        JButton diameterBtn = new JButton("Find Diameter");

        diameterBtn.addActionListener(e -> findGraphDiameter());
        addNodeBtn.addActionListener(e -> setMode(Mode.ADD_NODE));
        addEdgeBtn.addActionListener(e -> setMode(Mode.ADD_EDGE));
        moveBtn.addActionListener(e -> setMode(Mode.MOVE));
        deleteBtn.addActionListener(e -> setMode(Mode.DELETE));
        pruneBtn.addActionListener(e -> pruneNetwork());
        randomNetworkBtn.addActionListener(e -> promptAndGenerateRandomNetwork());
        aStarBtn.addActionListener(e -> runAnimatedAStar());
        analyzeBtn.addActionListener(e -> analyzeAllHeuristics());
        zoomInBtn.addActionListener(e -> {
            zoom *= 1.2;
            canvas.revalidate();
            canvas.repaint();
        });
        zoomOutBtn.addActionListener(e -> {
            zoom /= 1.2;
            canvas.revalidate();
            canvas.repaint();
        });

        bar.add(diameterBtn);
        bar.add(addNodeBtn);
        bar.add(addEdgeBtn);
        bar.add(moveBtn);
        bar.add(deleteBtn);
        bar.addSeparator();
        bar.add(pruneBtn);
        bar.add(randomNetworkBtn);
        bar.addSeparator();
        bar.add(aStarBtn);
        bar.add(analyzeBtn);
        bar.addSeparator();
        bar.add(zoomInBtn);
        bar.add(zoomOutBtn);

        return bar;
    }

    private void setMode(Mode m) {
        mode = m;
        pendingEdgeSource = null;
        statusLabel.setText("Mode: " + m);
        canvas.highlightedNode = null;
        clearSelection();
        canvas.revalidate();
        canvas.repaint();
    }

    private void selectNode(Node n) {
        selectedNode = n;
        selectedEdges.clear();
        selectedNeighbors.clear();

        if (n != null) {
            for (Edge e : graph.edgesOf(n)) {
                selectedEdges.add(e);
                Node other = (e.from == n) ? e.to : e.from;
                selectedNeighbors.add(other);
            }
        }
        canvas.revalidate();
        canvas.repaint();
    }

    private void clearSelection() {
        selectedNode = null;
        selectedEdges.clear();
        selectedNeighbors.clear();
    }

    private void refreshSelectionAfterGraphChange() {
        if (selectedNode != null) {
            if (graph.nodes.contains(selectedNode)) {
                selectNode(selectedNode);
            } else {
                clearSelection();
            }
        }
    }

    private void pruneNetwork() {
        // 1. Build the adjacency map ONCE at the start
        Map<Node, List<Edge>> adjMap = buildAdjacencyMap();

        // 2. Iterate over a copy of the edges directly to avoid concurrent modification
        // and to ensure each edge is evaluated exactly once
        List<Edge> edgesToTest = new ArrayList<>(graph.edges);

        for (Edge e : edgesToTest) {
            Node source = e.from;
            Node target = e.to;

            // 3. Temporarily isolate the edge from the graph and our local map
            graph.edges.remove(e);
            if (adjMap.containsKey(source))
                adjMap.get(source).remove(e);
            if (!graph.directed && adjMap.containsKey(target)) {
                adjMap.get(target).remove(e);
            }

            // 4. CRITICAL: Use a silent, non-animated version of A* for background
            // computation!
            PathResult path = animatedAStar(source, target, HeuristicMode.NORMAL, false, adjMap);

            // 5. If there is no detour, or the detour is worse than 1.1x the original
            // weight, KEEP it.
            if (path.path == null || path.totalCost >= 1.1 * e.getWeight()) {
                graph.edges.add(e);
                if (adjMap.containsKey(source))
                    adjMap.get(source).add(e);
                if (!graph.directed && adjMap.containsKey(target)) {
                    adjMap.get(target).add(e);
                }
            }
        }

        // 6. Trigger UI updates exactly ONCE after the entire batch is complete
        updateMaxSpeed();
        saveGraph();
        canvas.revalidate();
        canvas.repaint();
    }

    private Node findNodeById(String id) {
        for (Node n : graph.nodes)
            if (n.id.equals(id))
                return n;
        return null;
    }

    private void promptAndGenerateRandomNetwork() {
        JTextField nField = new JTextField("10");
        JTextField mField = new JTextField("15");
        JSlider localitySlider = new JSlider(1, 10, 5);
        localitySlider.setMajorTickSpacing(3);
        localitySlider.setMinorTickSpacing(1);
        localitySlider.setPaintTicks(true);
        localitySlider.setPaintLabels(false);

        JLabel localityLabel = new JLabel(
                "Locality: " + localitySlider.getValue() + "  (1 = tight clusters, 10 = spread out)");
        localitySlider.addChangeListener(e -> localityLabel
                .setText("Locality: " + localitySlider.getValue() + "  (1 = tight clusters, 10 = spread out)"));

        JPanel panel = new JPanel(new GridLayout(5, 1, 5, 5));
        panel.add(new JLabel("Number of nodes (n):"));
        panel.add(nField);
        panel.add(new JLabel("Number of edges (m):"));
        panel.add(mField);
        panel.add(localityLabel);
        panel.add(localitySlider);

        int result = JOptionPane.showConfirmDialog(this, panel, "Generate Random Network",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION)
            return;

        int n, m;
        try {
            n = Integer.parseInt(nField.getText().trim());
            m = Integer.parseInt(mField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "n and m must be whole numbers.");
            return;
        }

        // Map slider 1–10 → scale multiplier 0.2× – 3.0×
        double localityMultiplier = 0.2 + (localitySlider.getValue() - 1) * (3.0 - 0.2) / 9.0;
        generateRandomNetwork(n, m, localityMultiplier);
    }

    private void generateRandomNetwork(int n, int m, double localityMultiplier) {
        graph.nodes.clear();
        graph.edges.clear();
        clearSelection();

        Random rnd = new Random();
        int width = 3000, height = 3000, margin = 40;

        for (int i = 0; i < n; i++) {
            double x = margin + rnd.nextDouble() * (width - 2 * margin);
            double y = margin + rnd.nextDouble() * (height - 2 * margin);
            graph.addNode(new Node("N" + i, x, y));
        }

        if (n < 2) {
            updateMaxSpeed();
            saveGraph();
            canvas.revalidate();
            canvas.repaint();
            return;
        }

        double baseScale = Math.sqrt((double) (width - 2 * margin) * (height - 2 * margin) / n)
                * localityMultiplier;

        // "Short" = any edge below baseScale in length.
        // shortEdges[j] = how many short edges node j currently has.
        int[] shortEdges = new int[n];

        // How strongly hub status expands a node's reach.
        // Each short edge adds 0.5× to the effective scale multiplier.
        // e.g. a node with 4 short edges has 3× the normal reach.
        final double hubFactor = 0.5;

        double[] cumWeights = new double[n];

        for (int i = 0; i < m; i++) {
            List<Node> nodes = graph.nodes;

            // ── Step 1: pick source 'a' weighted by hub status ──────────────────
            // Nodes with more short edges are more likely to be chosen as source.
            double sourceTotal = 0;
            for (int j = 0; j < n; j++)
                sourceTotal += 1 + shortEdges[j];
            double sr = rnd.nextDouble() * sourceTotal;
            int aIdx = n - 1;
            double cumS = 0;
            for (int j = 0; j < n; j++) {
                cumS += 1 + shortEdges[j];
                if (cumS >= sr) {
                    aIdx = j;
                    break;
                }
            }
            Node a = nodes.get(aIdx);

            // ── Step 2: pick target 'b' with distance-decay, hub-expanded reach ─
            // Hub nodes have a larger effective scale, making long edges more likely.
            double total = 0;
            for (int j = 0; j < n; j++) {
                Node candidate = nodes.get(j);
                if (candidate == a) {
                    cumWeights[j] = total;
                    continue;
                }
                double d = Graph.distance(a, candidate);
                double effectiveScale = baseScale * (1 + hubFactor * shortEdges[j]);
                total += Math.exp(-d / effectiveScale);
                cumWeights[j] = total;
            }

            double r = rnd.nextDouble() * total;
            Node b = nodes.get(n - 1);
            for (int j = 0; j < n; j++) {
                if (cumWeights[j] >= r) {
                    b = nodes.get(j);
                    break;
                }
            }

            double distance = Graph.distance(a, b);

            // Update short-edge counts before adding the edge
            if (distance < baseScale) {
                shortEdges[aIdx]++;
                int bIdx = nodes.indexOf(b);
                shortEdges[bIdx]++;
            }

            double speedLimit = generateSpeedLimit(distance, rnd);
            graph.addEdge(a, b, speedLimit);
        }

        updateMaxSpeed();
        saveGraph();
        canvas.revalidate();
        canvas.repaint();
    }

    private double generateSpeedLimit(double distance, Random rnd) {
        double r = rnd.nextDouble();
        if (distance < 300)
            return (r < 0.75) ? 30 : 50;
        else if (distance < 800) {
            if (r < 0.2)
                return 30;
            if (r < 0.8)
                return 50;
            return 80;
        } else if (distance < 1500) {
            if (r < 0.2)
                return 50;
            if (r < 0.75)
                return 80;
            return 100;
        } else {
            return (r < 0.15) ? 80 : 100;
        }
    }

    private double maxSpeed = 1.0;

    private void updateMaxSpeed() {
        maxSpeed = 1.0;
        for (Edge e : graph.edges) {
            maxSpeed = Math.max(maxSpeed, e.speedLimit);
        }
    }

    private double heuristic(Node node, Node goal, Node start, double tentative, HeuristicMode mode) {
        // 1. Base admissible Euclidean travel time estimate
        double hNormal = Graph.distance(node, goal) / maxSpeed;

        if (mode == HeuristicMode.NORMAL) {
            return hNormal;
        }

        double travelled = Graph.distance(start, node);

        // 2. Your Original ADAPTED Heuristic
        if (mode == HeuristicMode.ADAPTED) {
            if (travelled < 1e-6)
                return hNormal;
            return Graph.distance(node, goal) * (tentative / travelled);
        }

        // 3. SUGGESTED Heuristic (Clamped + Dynamic Blend)
        if (mode == HeuristicMode.SUGGESTED) {
            if (travelled < 25.0)
                return hNormal;

            double currentSlowness = tentative / travelled;
            double maxSlownessCap = 4.5 / maxSpeed; // Keep it highly aggressive/fast

            if (currentSlowness > maxSlownessCap) {
                currentSlowness = maxSlownessCap;
            }

            double hSuggested = Graph.distance(node, goal) * currentSlowness;

            // THE FIX: Never allow the heuristic to guess worse than 1.8x the ideal speed.
            // This physically prevents a 200% error spike from ever formatting.
            return Math.min(hSuggested, hNormal * 1.8);
        }

        return hNormal;
    }

    private List<Node> reconstructPath(Map<Node, Node> cameFrom, Node current) {
        List<Node> path = new ArrayList<>();
        path.add(current);
        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            path.add(current);
        }
        Collections.reverse(path);
        return path;
    }

    // Helper to generate a localized adjacency list map for extreme performance
    // speedups
    private Map<Node, List<Edge>> buildAdjacencyMap() {
        Map<Node, List<Edge>> adjMap = new HashMap<>();
        for (Node n : graph.nodes)
            adjMap.put(n, new ArrayList<>());
        for (Edge e : graph.edges) {
            adjMap.get(e.from).add(e);
            if (!graph.directed) {
                adjMap.get(e.to).add(e);
            }
        }
        return adjMap;
    }

    private void runAnimatedAStar() {
        openNodes.clear();
        closedNodes.clear();
        finalPath.clear();
        finalPathNormal.clear();
        updatePathEdges();
        repaint();

        String startId = JOptionPane.showInputDialog(this, "Start node ID:");
        if (startId == null)
            return;
        String goalId = JOptionPane.showInputDialog(this, "Goal node ID:");
        if (goalId == null)
            return;

        Node start = findNodeById(startId.trim());
        Node goal = findNodeById(goalId.trim());
        searchStartNode = start;
        searchGoalNode = goal;

        if (start == null || goal == null) {
            JOptionPane.showMessageDialog(this, "Invalid node IDs.");
            return;
        }

        new Thread(() -> {
            Map<Node, List<Edge>> adjMap = buildAdjacencyMap();

            PathResult normal = animatedAStar(start, goal, HeuristicMode.NORMAL, false, adjMap);

            openNodes.clear();
            closedNodes.clear();
            finalPath.clear();
            finalPathNormal.clear();

            PathResult adapted = animatedAStar(start, goal, HeuristicMode.SUGGESTED, true, adjMap);

            SwingUtilities.invokeLater(() -> {
                finalPath.clear();
                finalPathNormal.clear();
                if (adapted.path != null)
                    finalPath = adapted.path;
                if (normal.path != null)
                    finalPathNormal = normal.path;

                updatePathEdges();
                String message = "NORMAL A*\nNodes considered: " + normal.nodesConsidered + "\n" +
                        String.format("Travel time: %.2f\n\n", normal.totalCost) +
                        "Suggested A*\nNodes considered: " + adapted.nodesConsidered + "\n" +
                        String.format("Travel time: %.2f\n\n", adapted.totalCost) +
                        "Difference in nodes considered: " + (normal.nodesConsidered - adapted.nodesConsidered);

                JOptionPane.showMessageDialog(GraphVisualizer.this, message);
                canvas.repaint();
            });
        }).start();
    }

    private void analyzeAllHeuristics() {

        if (graph.nodes.size() < 2) {
            JOptionPane.showMessageDialog(this, "Need at least 2 nodes.");
            return;
        }

        final JDialog progress = new JDialog(this, "Analyzing...", false);
        progress.add(new JLabel("   Running 3-way A* performance analysis...   "));
        progress.pack();
        progress.setLocationRelativeTo(this);
        progress.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        progress.setVisible(true);

        new Thread(() -> {
            long startTime = System.currentTimeMillis();

            int pairsConsidered = 0, pairsSkipped = 0;
            int subOptAdapted = 0, subOptSuggested = 0;
            double maxErrorAdapted = 0.0, maxErrorSuggested = 0.0;

            long totalNormal = 0, totalAdapted = 0, totalSuggested = 0;

            // Buckets for path lengths (Short: 1-4, Med: 5-9, Long: 10+ hops)
            long countShort = 0, normShort = 0, adaptShort = 0, suggShort = 0;
            long countMed = 0, normMed = 0, adaptMed = 0, suggMed = 0;
            long countLong = 0, normLong = 0, adaptLong = 0, suggLong = 0;

            // NEW: Track sum of cost errors for each bucket to compute averages
            double errorAdaptShortSum = 0.0, errorSuggShortSum = 0.0;
            double errorAdaptMedSum = 0.0, errorSuggMedSum = 0.0;
            double errorAdaptLongSum = 0.0, errorSuggLongSum = 0.0;

            // Index 0: 0-10%, Index 1: 10-20% ... Index 10: >100%
            int[] adaptedDistribution = new int[11];
            int[] suggestedDistribution = new int[11];

            // To calculate percentages later, track how many were actually suboptimal
            int adaptedSuboptimalCount = 0;
            int suggestedSuboptimalCount = 0;

            List<Node> nodes = new ArrayList<>(graph.nodes);
            Map<Node, List<Edge>> adjMap = buildAdjacencyMap();

            for (Node start : nodes) {
                for (Node goal : nodes) {
                    if (start == goal)
                        continue;

                    // Execute all three variants sequentially
                    PathResult normal = animatedAStar(start, goal, HeuristicMode.NORMAL, false, adjMap);
                    PathResult adapted = animatedAStar(start, goal, HeuristicMode.ADAPTED, false, adjMap);
                    PathResult suggested = animatedAStar(start, goal, HeuristicMode.SUGGESTED, false, adjMap);

                    openNodes.clear();
                    closedNodes.clear();

                    // Ensure a path validly exists across all models to keep data fair
                    if (normal.path == null || adapted.path == null || suggested.path == null) {
                        pairsSkipped++;
                        continue;
                    }

                    int hops = normal.path.size() - 1;
                    if (hops <= 0) {
                        pairsSkipped++;
                        continue;
                    }

                    pairsConsidered++;
                    totalNormal += normal.nodesConsidered;
                    totalAdapted += adapted.nodesConsidered;
                    totalSuggested += suggested.nodesConsidered;

                    double optimalLength = normal.path.size();
                    double adaptedLength = adapted.path.size();
                    double suggestedLength = suggested.path.size();

                    // -- ADAPTED BUCKETING --
                    if (adaptedLength > optimalLength) {
                        adaptedSuboptimalCount++;
                        double errorPercent = ((adaptedLength - optimalLength) / optimalLength) * 100.0;

                        // Divide by 10 to get the bucket index, cap at index 10
                        int bucketIndex = Math.min((int) (errorPercent / 10), 10);
                        adaptedDistribution[bucketIndex]++;
                    }

                    // -- SUGGESTED BUCKETING --
                    if (suggestedLength > optimalLength) {
                        suggestedSuboptimalCount++;
                        double errorPercent = ((suggestedLength - optimalLength) / optimalLength) * 100.0;

                        int bucketIndex = Math.min((int) (errorPercent / 10), 10);
                        suggestedDistribution[bucketIndex]++;
                    }

                    // NEW: Calculate cost error percentages for this specific pair
                    // Guarding against negative values from minor floating-point inaccuracies
                    double errorAdapted = normal.totalCost > 0
                            ? Math.max(0.0, ((adapted.totalCost - normal.totalCost) / normal.totalCost) * 100.0)
                            : 0.0;
                    double errorSuggested = normal.totalCost > 0
                            ? Math.max(0.0, ((suggested.totalCost - normal.totalCost) / normal.totalCost) * 100.0)
                            : 0.0;

                    // Evaluate Global Optimality Loss against the ground truth (NORMAL)
                    if (adapted.totalCost > normal.totalCost + 1e-6) {
                        subOptAdapted++;
                        maxErrorAdapted = Math.max(maxErrorAdapted, errorAdapted);
                    }
                    if (suggested.totalCost > normal.totalCost + 1e-6) {
                        subOptSuggested++;
                        maxErrorSuggested = Math.max(maxErrorSuggested, errorSuggested);
                    }

                    // Sorting into distance buckets and accumulating cost errors
                    if (hops < 5) {
                        countShort++;
                        normShort += normal.nodesConsidered;
                        adaptShort += adapted.nodesConsidered;
                        suggShort += suggested.nodesConsidered;
                        errorAdaptShortSum += errorAdapted;
                        errorSuggShortSum += errorSuggested;
                    } else if (hops < 10) {
                        countMed++;
                        normMed += normal.nodesConsidered;
                        adaptMed += adapted.nodesConsidered;
                        suggMed += suggested.nodesConsidered;
                        errorAdaptMedSum += errorAdapted;
                        errorSuggMedSum += errorSuggested;
                    } else {
                        countLong++;
                        normLong += normal.nodesConsidered;
                        adaptLong += adapted.nodesConsidered;
                        suggLong += suggested.nodesConsidered;
                        errorAdaptLongSum += errorAdapted;
                        errorSuggLongSum += errorSuggested;
                    }
                }
            }

            long timetaken = System.currentTimeMillis() - startTime;

            // Build Report
            StringBuilder r = new StringBuilder();
            r.append(String.format("=== THREE-WAY HEURISTIC BENCHMARK (%d ms) ===\n\n", timetaken));
            r.append(String.format("Routes Analyzed: %,d  |  Skipped: %,d\n", pairsConsidered, pairsSkipped));
            r.append("------------------------------------------------------------\n");

            // Global Workload Summary
            r.append("GLOBAL SEARCH WORKLOAD (Total Nodes Looked At):\n");
            r.append(String.format("  * NORMAL (Baseline) : %,d nodes\n", totalNormal));
            r.append(String.format("  * ADAPTED (Yours)   : %,d nodes (Saved %.1f%% work)\n", totalAdapted,
                    getSavedPct(totalNormal, totalAdapted)));
            r.append(String.format("  * SUGGESTED (Mine)  : %,d nodes (Saved %.1f%% work)\n\n", totalSuggested,
                    getSavedPct(totalNormal, totalSuggested)));

            r.append("------------------------------------------------------------\n");

            // Path Accuracy / Optimality Summary
            r.append("ACCURACY & OPTIMALITY DEGRADATION:\n");
            r.append(String.format("  * ADAPTED   -> Suboptimal Routes: %d (%.2f%%) | Max Error: %.2f%% worse\n",
                    subOptAdapted, ((double) subOptAdapted / pairsConsidered) * 100, maxErrorAdapted));
            r.append(String.format("  * SUGGESTED -> Suboptimal Routes: %d (%.2f%%) | Max Error: %.2f%% worse\n\n",
                    subOptSuggested, ((double) subOptSuggested / pairsConsidered) * 100, maxErrorSuggested));

            r.append("------------------------------------------------------------\n");
            r.append("PERFORMANCE BY JOURNEY LENGTH:\n\n");

            // NEW: Pass the accumulated error sums into the reporting method
            appendBucketMetric(r, "Short Range (1-4 Hops)", countShort, normShort, adaptShort, suggShort,
                    errorAdaptShortSum, errorSuggShortSum);
            appendBucketMetric(r, "Mid Range (5-9 Hops)", countMed, normMed, adaptMed, suggMed, errorAdaptMedSum,
                    errorSuggMedSum);
            appendBucketMetric(r, "Long Range (10+ Hops)", countLong, normLong, adaptLong, suggLong, errorAdaptLongSum,
                    errorSuggLongSum);

            System.out.println("\n--- ERROR DISTRIBUTION (Suboptimal routes only) ---");

            System.out.println("ADAPTED Heuristic:");
            for (int i = 0; i <= 10; i++) {
                String label = (i == 10) ? ">100% slower  " : String.format("%3d-%-3d%% slower", i * 10, (i + 1) * 10);
                double pctOfSuboptimal = (adaptedDistribution[i] / (double) adaptedSuboptimalCount) * 100.0;
                System.out.printf("  %s : %6d routes (%5.2f%%)\n", label, adaptedDistribution[i], pctOfSuboptimal);
            }

            System.out.println("\nSUGGESTED Heuristic:");
            for (int i = 0; i <= 10; i++) {
                String label = (i == 10) ? ">100% slower  " : String.format("%3d-%-3d%% slower", i * 10, (i + 1) * 10);
                double pctOfSuboptimal = (suggestedDistribution[i] / (double) suggestedSuboptimalCount) * 100.0;
                System.out.printf("  %s : %6d routes (%5.2f%%)\n", label, suggestedDistribution[i], pctOfSuboptimal);
            }

            SwingUtilities.invokeLater(() -> {
                progress.setVisible(false);
                progress.dispose();

                JTextArea textArea = new JTextArea(r.toString());
                textArea.setEditable(false);
                textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

                JOptionPane.showMessageDialog(GraphVisualizer.this, new JScrollPane(textArea),
                        "Head-to-Head Heuristic Comparison", JOptionPane.INFORMATION_MESSAGE);
            });
        }).start();
    }

    private double getSavedPct(long base, long target) {
        if (base == 0)
            return 0;
        return ((double) (base - target) / base) * 100.0;
    }

    private void appendBucketMetric(StringBuilder sb, String bucketName, long count,
            long normNodes, long adaptNodes, long suggNodes,
            double adaptErrorSum, double suggErrorSum) {
        sb.append(bucketName).append(String.format(" (%,d routes):\n", count));
        if (count == 0) {
            sb.append("  No routes fell within this range.\n\n");
            return;
        }

        // Calculate average workload nodes looked at
        double avgNorm = (double) normNodes / count;
        double avgAdapt = (double) adaptNodes / count;
        double avgSugg = (double) suggNodes / count;

        // Calculate average path cost error percentage compared to baseline
        double avgAdaptError = adaptErrorSum / count;
        double avgSuggError = suggErrorSum / count;

        sb.append(String.format(
                "  * Avg Workload -> NORMAL: %.1f | ADAPTED: %.1f (Saved %.1f%%) | SUGGESTED: %.1f (Saved %.1f%%)\n",
                avgNorm, avgAdapt, getSavedPct(normNodes, adaptNodes), avgSugg, getSavedPct(normNodes, suggNodes)));
        sb.append(String.format("  * Avg Path Cost Error -> ADAPTED: %.3f%% longer | SUGGESTED: %.3f%% longer\n\n",
                avgAdaptError, avgSuggError));
    }

    private PathResult animatedAStar(Node start, Node goal, HeuristicMode mode, boolean animate,
            Map<Node, List<Edge>> adjMap) {
        Map<Node, Double> gScore = new HashMap<>();
        Map<Node, Double> fScore = new HashMap<>();
        Map<Node, Node> cameFrom = new HashMap<>();
        List<Node> expandedOrder = new ArrayList<>();
        int nodesConsidered = 0;

        PriorityQueue<PQNode> openSet = new PriorityQueue<>();
        gScore.put(start, 0.0);
        double startFScore = Graph.distance(start, goal) / maxSpeed;
        fScore.put(start, startFScore);

        openSet.add(new PQNode(start, startFScore));
        openNodes.add(start);

        while (!openSet.isEmpty()) {
            PQNode currentPQ = openSet.poll();
            Node current = currentPQ.node;
            double currentFScore = currentPQ.score;

            // Lazy deletion check: Skip if a better path to this node was already processed
            if (currentFScore > fScore.getOrDefault(current, Double.POSITIVE_INFINITY)) {
                continue;
            }

            expandedOrder.add(current);
            nodesConsidered++;
            openNodes.remove(current);
            closedNodes.add(current);

            if (animate) {
                SwingUtilities.invokeLater(canvas::repaint);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }

            if (current == goal) {
                return new PathResult(reconstructPath(cameFrom, current), expandedOrder, nodesConsidered,
                        gScore.get(current));
            }

            List<Edge> edgesToScan = adjMap.getOrDefault(current, Collections.emptyList());
            for (Edge e : edgesToScan) {
                Node neighbor = null;
                if (e.from == current)
                    neighbor = e.to;
                else if (!graph.directed && e.to == current)
                    neighbor = e.from;

                if (neighbor == null)
                    continue;

                double tentative = gScore.get(current) + e.getWeight();
                if (tentative < gScore.getOrDefault(neighbor, Double.POSITIVE_INFINITY)) {
                    cameFrom.put(neighbor, current);
                    gScore.put(neighbor, tentative);

                    double neighborFScore = tentative + heuristic(neighbor, goal, start, tentative, mode);
                    fScore.put(neighbor, neighborFScore);

                    openSet.add(new PQNode(neighbor, neighborFScore));
                    openNodes.add(neighbor);
                }
            }
        }

        return new PathResult(null, expandedOrder, nodesConsidered, 0);
    }

    private static class PathResult {
        List<Node> path;
        int nodesConsidered;
        double totalCost;

        PathResult(List<Node> path, List<Node> expandedOrder, int nodesConsidered, double totalCost) {
            this.path = path;
            this.nodesConsidered = nodesConsidered;
            this.totalCost = totalCost;
        }
    }

    private static class DiameterResult {
        Node from, to;
        double distance;

        DiameterResult(Node from, Node to, double distance) {
            this.from = from;
            this.to = to;
            this.distance = distance;
        }
    }

    private void findGraphDiameter() {
        if (graph.nodes.size() < 2) {
            JOptionPane.showMessageDialog(this, "Need at least 2 nodes.");
            return;
        }

        final JDialog progress = new JDialog(this, "Computing...", false);
        progress.add(new JLabel("  Calculating longest shortest path, please wait...  "));
        progress.pack();
        progress.setLocationRelativeTo(this);
        progress.setVisible(true);

        new Thread(() -> {
            DiameterResult result = computeDiameter();
            SwingUtilities.invokeLater(() -> {
                progress.setVisible(false);
                progress.dispose();
                if (result == null) {
                    JOptionPane.showMessageDialog(this, "No connected node pairs found.");
                } else {
                    JOptionPane.showMessageDialog(this, "Longest shortest path\n\n" + result.from.id + " -> " +
                            result.to.id + "\n\n" + String.format("Travel time: %.2f", result.distance));
                }
            });
        }).start();
    }

    private void updatePathEdges() {
        finalPathEdges.clear();
        finalPathNormalEdges.clear();

        // Convert ADAPTED path
        if (finalPath != null && !finalPath.isEmpty()) {
            for (int i = 0; i < finalPath.size() - 1; i++) {
                Node a = finalPath.get(i);
                Node b = finalPath.get(i + 1);
                for (Edge e : graph.edgesOf(a)) {
                    if ((e.from == a && e.to == b) || (!graph.directed && e.from == b && e.to == a)) {
                        finalPathEdges.add(e);
                    }
                }
            }
        }

        // Convert NORMAL path
        if (finalPathNormal != null && !finalPathNormal.isEmpty()) {
            for (int i = 0; i < finalPathNormal.size() - 1; i++) {
                Node a = finalPathNormal.get(i);
                Node b = finalPathNormal.get(i + 1);
                for (Edge e : graph.edgesOf(a)) {
                    if ((e.from == a && e.to == b) || (!graph.directed && e.from == b && e.to == a)) {
                        finalPathNormalEdges.add(e);
                    }
                }
            }
        }
    }

    private Map<Node, Double> dijkstra(Node start, Map<Node, List<Edge>> adjMap) {
        Map<Node, Double> dist = new HashMap<>();
        for (Node n : graph.nodes)
            dist.put(n, Double.POSITIVE_INFINITY);
        dist.put(start, 0.0);

        PriorityQueue<PQNode> pq = new PriorityQueue<>();
        pq.add(new PQNode(start, 0.0));

        while (!pq.isEmpty()) {
            PQNode currentPQ = pq.poll();
            Node current = currentPQ.node;
            double currentDist = currentPQ.score;

            if (currentDist > dist.get(current))
                continue;

            List<Edge> edgesToScan = adjMap.getOrDefault(current, Collections.emptyList());
            for (Edge e : edgesToScan) {
                Node neighbor = null;
                if (e.from == current)
                    neighbor = e.to;
                else if (!graph.directed && e.to == current)
                    neighbor = e.from;

                if (neighbor == null)
                    continue;

                double alt = currentDist + e.getWeight();
                if (alt < dist.get(neighbor)) {
                    dist.put(neighbor, alt);
                    pq.add(new PQNode(neighbor, alt));
                }
            }
        }
        return dist;
    }

    private DiameterResult computeDiameter() {
        Node bestA = null, bestB = null;
        double bestDistance = -1;

        Map<Node, List<Edge>> adjMap = buildAdjacencyMap();

        for (Node start : graph.nodes) {
            Map<Node, Double> distances = dijkstra(start, adjMap);
            for (Node end : graph.nodes) {
                if (start == end)
                    continue;
                double d = distances.get(end);
                if (Double.isInfinite(d))
                    continue;

                if (d > bestDistance) {
                    bestDistance = d;
                    bestA = start;
                    bestB = end;
                }
            }
        }
        return (bestA == null) ? null : new DiameterResult(bestA, bestB, bestDistance);
    }

    private Node nodeAt(Point p) {
        for (Node n : graph.nodes) {
            if (Point2DDistance(n.x, n.y, p.x, p.y) <= NODE_RADIUS)
                return n;
        }
        return null;
    }

    private static double Point2DDistance(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2, dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private Edge edgeNear(Point p) {
        for (Edge e : graph.edges) {
            double mx = (e.from.x + e.to.x) / 2;
            double my = (e.from.y + e.to.y) / 2;
            if (Point2DDistance(mx, my, p.x, p.y) <= 15)
                return e;
        }
        return null;
    }

    // ---------- Mouse handling ----------
    private class CanvasMouseListener extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            Point p = new Point((int) (e.getX() / zoom), (int) (e.getY() / zoom));
            switch (mode) {
                case ADD_NODE: {
                    if (nodeAt(p) != null)
                        return;
                    String id = JOptionPane.showInputDialog(GraphVisualizer.this, "Node id:");
                    if (id == null || id.trim().isEmpty())
                        return;
                    if (findNodeById(id.trim()) != null) {
                        JOptionPane.showMessageDialog(GraphVisualizer.this, "That id already exists.");
                        return;
                    }
                    graph.addNode(new Node(id.trim(), p.x, p.y));
                    saveGraph();
                    canvas.revalidate();
                    canvas.repaint();
                    break;
                }
                case ADD_EDGE: {
                    Node clicked = nodeAt(p);
                    if (clicked == null)
                        return;
                    if (pendingEdgeSource == null) {
                        pendingEdgeSource = clicked;
                        canvas.highlightedNode = clicked;
                        canvas.repaint();
                    } else {
                        if (clicked != pendingEdgeSource) {
                            String s = JOptionPane.showInputDialog(GraphVisualizer.this, "Speed limit:");
                            if (s != null) {
                                try {
                                    double speedLimit = Double.parseDouble(s.trim());
                                    graph.addEdge(pendingEdgeSource, clicked, speedLimit);
                                    updateMaxSpeed();
                                    saveGraph();
                                    refreshSelectionAfterGraphChange();
                                } catch (NumberFormatException ex) {
                                    JOptionPane.showMessageDialog(GraphVisualizer.this,
                                            "Invalid number, edge not added.");
                                }
                            }
                        }
                        pendingEdgeSource = null;
                        canvas.highlightedNode = null;
                        canvas.repaint();
                    }
                    break;
                }
                case MOVE:
                    draggingNode = nodeAt(p);
                    if (draggingNode != null)
                        selectNode(draggingNode);
                    else
                        clearSelection();
                    canvas.repaint();
                    break;
                case DELETE: {
                    Node n = nodeAt(p);
                    if (n != null) {
                        graph.removeNode(n);
                        saveGraph();
                        refreshSelectionAfterGraphChange();
                    } else {
                        Edge edge = edgeNear(p);
                        if (edge != null) {
                            graph.removeEdge(edge);
                            saveGraph();
                            refreshSelectionAfterGraphChange();
                        }
                    }
                    canvas.repaint();
                    break;
                }
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (draggingNode != null)
                saveGraph();
            draggingNode = null;
        }
    }

    private class CanvasMouseMotionListener extends MouseMotionAdapter {
        @Override
        public void mouseDragged(MouseEvent e) {
            if (mode == Mode.MOVE && draggingNode != null) {
                draggingNode.x = e.getX() / zoom;
                draggingNode.y = e.getY() / zoom;
                canvas.repaint();
            }
        }
    }

    // ---------- Drawing ----------
    private class GraphPanel extends JPanel {
        Node highlightedNode = null;

        GraphPanel() {
            setBackground(Color.WHITE);
        }

        @Override
        public Dimension getPreferredSize() {
            int width = 3000, height = 3000;
            for (Node n : graph.nodes) {
                width = Math.max(width, (int) n.x + 100);
                height = Math.max(height, (int) n.y + 100);
            }
            return new Dimension((int) (width * zoom), (int) (height * zoom));
        }

        @Override
        protected void paintComponent(Graphics g0) {

            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.scale(zoom, zoom);

            // Inside paintComponent(Graphics g0)
            for (Edge e : graph.edges) {
                boolean inAdapted = finalPathEdges.contains(e); // O(1) lookup
                boolean inNormal = finalPathNormalEdges.contains(e); // O(1) lookup
                boolean inSelection = selectedEdges.contains(e);

                if (inAdapted && inNormal) {
                    g.setColor(new Color(160, 32, 200));
                    g.setStroke(new BasicStroke(4));
                } else if (inAdapted) {
                    g.setColor(Color.RED);
                    g.setStroke(new BasicStroke(4));
                } else if (inNormal) {
                    g.setColor(new Color(0, 90, 220));
                    g.setStroke(new BasicStroke(4));
                } else if (inSelection) {
                    g.setColor(new Color(255, 140, 0));
                    g.setStroke(new BasicStroke(3));
                } else {
                    g.setColor(Color.GRAY);
                    g.setStroke(new BasicStroke(2));
                }

                g.draw(new Line2DDouble(e.from.x, e.from.y, e.to.x, e.to.y));
                double mx = (e.from.x + e.to.x) / 2;
                double my = (e.from.y + e.to.y) / 2;
                g.setColor(Color.BLACK);
                g.drawString(String.format("%.1f (%.0f)", e.getWeight(), e.speedLimit), (float) mx, (float) my);
            }

            for (Node n : graph.nodes) {
                Ellipse2D circle = new Ellipse2D.Double(n.x - NODE_RADIUS, n.y - NODE_RADIUS, NODE_RADIUS * 2,
                        NODE_RADIUS * 2);
                if (n == searchStartNode)
                    g.setColor(new Color(0, 180, 0));
                else if (n == searchGoalNode)
                    g.setColor(Color.RED);
                else if (finalPath.contains(n) && finalPathNormal.contains(n))
                    g.setColor(new Color(200, 140, 230));
                else if (finalPath.contains(n))
                    g.setColor(new Color(255, 120, 120));
                else if (finalPathNormal.contains(n))
                    g.setColor(new Color(140, 190, 255));
                else if (closedNodes.contains(n))
                    g.setColor(new Color(255, 170, 60));
                else if (openNodes.contains(n))
                    g.setColor(new Color(255, 255, 0));
                else if (n == highlightedNode)
                    g.setColor(new Color(255, 220, 130));
                else
                    g.setColor(new Color(160, 200, 255));

                g.fill(circle);
                g.setColor(Color.BLACK);
                g.draw(circle);

                if (n == selectedNode || selectedNeighbors.contains(n)) {
                    g.setColor(new Color(255, 140, 0));
                    g.setStroke(new BasicStroke(n == selectedNode ? 4 : 3));
                    g.draw(circle);
                    g.setStroke(new BasicStroke(2));
                }

                FontMetrics fm = g.getFontMetrics();
                int textWidth = fm.stringWidth(n.id);
                g.drawString(n.id, (float) (n.x - textWidth / 2.0), (float) (n.y + fm.getAscent() / 2.0 - 2));
            }
        }
    }

    private static class Line2DDouble extends java.awt.geom.Line2D.Double {
        Line2DDouble(double x1, double y1, double x2, double y2) {
            super(x1, y1, x2, y2);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GraphVisualizer().setVisible(true));
    }
}
