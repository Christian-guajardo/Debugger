package gui;

import commands.*;
import models.*;
import timetravel.*;
import com.sun.jdi.*;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/**
 * Interface graphique du debugger avec support complet des Time-Traveling Queries
 * Implémente toutes les fonctionnalités demandées dans tp-gui.pdf et tp-ttq.pdf
 */
public class DebuggerGUI extends JFrame {
    // ========== Composants principaux ==========
    private SourceCodePanel sourceCodePanel;
    private JList<String> callStackList;
    private JTree inspectorTree;
    private JTextArea outputArea;
    private JTextArea programOutputArea;  // Séparé pour la sortie du programme

    // ========== Boutons de contrôle ==========
    private JButton stepOverButton;
    private JButton stepIntoButton;
    private JButton continueButton;
    private JButton stopButton;

    // ========== Panneaux Time-Traveling Queries ==========
    private JPanel ttqPanel;
    private DefaultListModel<String> variableHistoryModel;
    private JList<String> variableHistoryList;
    private DefaultListModel<String> methodCallsModel;
    private JList<String> methodCallsList;
    private JLabel currentVariableLabel;
    private JLabel currentSearchLabel;

    // ========== Modèles de données ==========
    private DefaultListModel<String> callStackModel;
    private DefaultMutableTreeNode inspectorRoot;
    private DefaultTreeModel inspectorTreeModel;

    // ========== État du debugger ==========
    private DebuggerState state;
    private CommandInterpreter interpreter;
    private Map<Integer, String> sourceLines;
    private int currentLine = -1;
    private String currentSourceFile = "";
    private ThreadReference currentThread;

    // ========== Callback ==========
    private DebuggerCallback callback;

    // ========== Données TTQ ==========
    private String trackedVariable = null;
    private List<VariableModification> currentVariableHistory = new ArrayList<>();
    private List<MethodCallInfo> currentMethodCalls = new ArrayList<>();

    public interface DebuggerCallback {
        CommandResult executeCommand(Command command);

        void placeBreakpoint(String file, int line);
        void stop();
    }

    public DebuggerGUI() {
        super("Time-Traveling Debugger - Graphical Interface");
        sourceLines = new HashMap<>();
        initComponents();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1400, 900);
        setLocationRelativeTo(null);
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));

        // Panel principal avec 3 colonnes
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));

        // Colonne gauche : Source code + contrôles
        JPanel leftPanel = createLeftPanel();

        // Colonne centrale : Call stack + Inspector
        JPanel centerPanel = createCenterPanel();

        // Colonne droite : Time-Traveling Queries + Output
        JPanel rightPanel = createRightPanel();

        // Organisation horizontale
        JSplitPane leftCenterSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        leftCenterSplit.setLeftComponent(leftPanel);
        leftCenterSplit.setRightComponent(centerPanel);
        leftCenterSplit.setDividerLocation(600);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setLeftComponent(leftCenterSplit);
        mainSplit.setRightComponent(rightPanel);
        mainSplit.setDividerLocation(1000);

        mainPanel.add(mainSplit, BorderLayout.CENTER);
        add(mainPanel, BorderLayout.CENTER);

        // Barre de statut
        JLabel statusBar = new JLabel(" Ready - Replay Mode");
        statusBar.setBorder(new BevelBorder(BevelBorder.LOWERED));
        add(statusBar, BorderLayout.SOUTH);
    }

    // ========== PANEL GAUCHE : Source Code + Contrôles ==========
    private JPanel createLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        // Contrôles en haut
        JPanel controlPanel = createControlPanel();
        panel.add(controlPanel, BorderLayout.NORTH);

        // Source code au centre
        JPanel sourcePanel = createSourceCodePanel();
        panel.add(sourcePanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        panel.setBorder(new TitledBorder("Execution Control (Replay Mode)"));

        stepIntoButton = new JButton("Step Into");
        stepIntoButton.setToolTipText("Move to next snapshot");
        stepIntoButton.addActionListener(e -> executeStepInto());

        stepOverButton = new JButton("Step Over");
        stepOverButton.setToolTipText("Skip method calls at same level");
        stepOverButton.addActionListener(e -> executeStepOver());

        continueButton = new JButton("Continue");
        continueButton.setToolTipText("Continue to next breakpoint");
        continueButton.addActionListener(e -> executeContinue());

        stopButton = new JButton("Stop");
        stopButton.setToolTipText("Stop debugging");
        stopButton.addActionListener(e -> executeStop());
        stopButton.setBackground(new Color(220, 100, 100));

        panel.add(stepIntoButton);
        panel.add(stepOverButton);
        panel.add(continueButton);
        panel.add(stopButton);

        return panel;
    }

    private JPanel createSourceCodePanel() {
        JPanel panel = new JPanel(new BorderLayout());

        sourceCodePanel = new SourceCodePanel();
        sourceCodePanel.setBreakpointListener(lineNumber -> {
            if (callback != null && !currentSourceFile.isEmpty()) {
                callback.placeBreakpoint(currentSourceFile, lineNumber);
            }
        });

        panel.add(sourceCodePanel, BorderLayout.CENTER);

        return panel;
    }

    // ========== PANEL CENTRAL : Call Stack + Inspector ==========
    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(250);

        JPanel callStackPanel = createCallStackPanel();
        JPanel inspectorPanel = createInspectorPanel();

        splitPane.setTopComponent(callStackPanel);
        splitPane.setBottomComponent(inspectorPanel);

        panel.add(splitPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createCallStackPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Call Stack"));

        callStackModel = new DefaultListModel<>();
        callStackList = new JList<>(callStackModel);
        callStackList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        callStackList.setFont(new Font("Monospaced", Font.PLAIN, 11));

        callStackList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedIndex = callStackList.getSelectedIndex();
                if (selectedIndex >= 0 && state != null && state.getContext() != null) {
                    updateInspectorForFrame(selectedIndex);

                    try {
                        DebugFrame frame = state.getContext().getCallStack().getFrames().get(selectedIndex);
                        Location loc = frame.getLocation();
                        loadSourceCode(loc);
                        sourceCodePanel.setCurrentLine(loc.lineNumber());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });

        // Menu contextuel pour rechercher les appels
        JPopupMenu callStackMenu = new JPopupMenu();
        JMenuItem findAllCallsItem = new JMenuItem("Find All Method Calls");
        findAllCallsItem.addActionListener(e -> findAllMethodCalls());
        JMenuItem findCallsToItem = new JMenuItem("Find Calls to This Method");
        findCallsToItem.addActionListener(e -> findCallsToSelectedMethod());

        callStackMenu.add(findAllCallsItem);
        callStackMenu.add(findCallsToItem);
        callStackList.setComponentPopupMenu(callStackMenu);

        JScrollPane scrollPane = new JScrollPane(callStackList);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createInspectorPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Inspector (Variables Tree)"));

        inspectorRoot = new DefaultMutableTreeNode("Variables");
        inspectorTreeModel = new DefaultTreeModel(inspectorRoot);
        inspectorTree = new JTree(inspectorTreeModel);
        inspectorTree.setFont(new Font("Monospaced", Font.PLAIN, 11));

        // Menu contextuel pour suivre une variable
        JPopupMenu inspectorMenu = new JPopupMenu();
        JMenuItem followItem = new JMenuItem("Follow Variable");
        followItem.addActionListener(e -> followSelectedVariable());
        inspectorMenu.add(followItem);
        inspectorTree.setComponentPopupMenu(inspectorMenu);

        JScrollPane scrollPane = new JScrollPane(inspectorTree);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    // ========== PANEL DROIT : Time-Traveling Queries + Output ==========
    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(450);

        // Panneau TTQ en haut
        JPanel ttqPanel = createTimeTravelingQueriesPanel();

        // Output en bas
        JPanel outputPanel = createOutputPanel();

        splitPane.setTopComponent(ttqPanel);
        splitPane.setBottomComponent(outputPanel);

        panel.add(splitPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createTimeTravelingQueriesPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new TitledBorder("Time-Traveling Queries"));

        // Onglets pour séparer Variable Tracking et Method Calls
        JTabbedPane tabbedPane = new JTabbedPane();

        // Onglet 1 : Suivi de variables
        JPanel variablePanel = createVariableTrackingPanel();
        tabbedPane.addTab("Variable Tracking", variablePanel);

        // Onglet 2 : Recherche d'appels de méthodes
        JPanel methodPanel = createMethodCallsPanel();
        tabbedPane.addTab("Method Calls", methodPanel);

        panel.add(tabbedPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createVariableTrackingPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        // En-tête avec info sur la variable suivie
        JPanel headerPanel = new JPanel(new BorderLayout());
        currentVariableLabel = new JLabel("No variable selected");
        currentVariableLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        currentVariableLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        headerPanel.add(currentVariableLabel, BorderLayout.CENTER);

        // Panel avec boutons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton showAllButton = new JButton("Show All");
        showAllButton.setToolTipText("Show all tracked variables");
        showAllButton.addActionListener(e -> showAllTrackedVariables());

        JButton clearButton = new JButton("Clear");
        clearButton.setToolTipText("Clear display");
        clearButton.addActionListener(e -> clearVariableTracking());

        buttonPanel.add(showAllButton);
        buttonPanel.add(clearButton);
        headerPanel.add(buttonPanel, BorderLayout.EAST);

        panel.add(headerPanel, BorderLayout.NORTH);

        // Liste des modifications
        variableHistoryModel = new DefaultListModel<>();
        variableHistoryList = new JList<>(variableHistoryModel);
        variableHistoryList.setFont(new Font("Monospaced", Font.PLAIN, 11));
        variableHistoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Double-clic pour voyager dans le temps
        variableHistoryList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    timeTravelToSelectedModification();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(variableHistoryList);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Instructions
        JLabel infoLabel = new JLabel("<html><i>Right-click on a variable in Inspector and select 'Follow Variable'</i></html>");
        infoLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.add(infoLabel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createMethodCallsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        // En-tête avec info sur la recherche
        JPanel headerPanel = new JPanel(new BorderLayout());
        currentSearchLabel = new JLabel("No search performed");
        currentSearchLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        currentSearchLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        headerPanel.add(currentSearchLabel, BorderLayout.CENTER);

        JButton clearButton = new JButton("Clear");
        clearButton.setToolTipText("Clear search results");
        clearButton.addActionListener(e -> clearMethodCallsSearch());
        headerPanel.add(clearButton, BorderLayout.EAST);

        panel.add(headerPanel, BorderLayout.NORTH);

        // Liste des appels trouvés
        methodCallsModel = new DefaultListModel<>();
        methodCallsList = new JList<>(methodCallsModel);
        methodCallsList.setFont(new Font("Monospaced", Font.PLAIN, 11));
        methodCallsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Double-clic pour voyager dans le temps
        methodCallsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    timeTravelToSelectedMethodCall();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(methodCallsList);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Boutons de recherche
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton findAllButton = new JButton("Find All Calls");
        findAllButton.setToolTipText("Find all method calls in execution");
        findAllButton.addActionListener(e -> findAllMethodCalls());

        JButton findSpecificButton = new JButton("Find Calls To...");
        findSpecificButton.setToolTipText("Find calls to a specific method");
        findSpecificButton.addActionListener(e -> findCallsToMethod());

        buttonPanel.add(findAllButton);
        buttonPanel.add(findSpecificButton);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createOutputPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Onglets pour séparer sortie programme et messages debugger
        JTabbedPane outputTabs = new JTabbedPane();

        // Onglet 1: Sortie du programme SEULEMENT
        programOutputArea = new JTextArea();
        programOutputArea.setEditable(false);
        programOutputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        programOutputArea.setBackground(Color.WHITE);
        JScrollPane programScroll = new JScrollPane(programOutputArea);
        outputTabs.addTab("Program Output", programScroll);

        // Onglet 2: Messages du debugger
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        outputArea.setBackground(new Color(240, 240, 240));
        JScrollPane debuggerScroll = new JScrollPane(outputArea);
        outputTabs.addTab("Debugger Messages", debuggerScroll);

        panel.add(outputTabs, BorderLayout.CENTER);
        panel.setBorder(new TitledBorder("Output"));

        return panel;
    }

    // ========== ACTIONS DES CONTRÔLES ==========
    private void executeStepInto() {
        if (callback != null) {
            callback.executeCommand(new StepCommand());
        }
    }

    private void executeStepOver() {
        if (callback != null) {
            callback.executeCommand(new StepOverCommand());
        }
    }

    private void executeContinue() {
        if (callback != null) {
            callback.executeCommand(new ContinueCommand());
        }
    }

    private void executeStop() {
        if (callback != null) {
            callback.stop();
        }
        System.exit(0);
    }

    // ========== FONCTIONNALITÉS TIME-TRAVELING QUERIES ==========

    /**
     * Commence à suivre la variable sélectionnée dans l'inspector
     */
    private void followSelectedVariable() {
        TreePath path = inspectorTree.getSelectionPath();
        if (path == null) return;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        String nodeText = node.toString();

        // Extraire le nom de la variable
        String varName = extractVariableName(nodeText);
        if (varName == null || varName.isEmpty()) {
            appendOutput("Cannot track this variable\n");
            return;
        }

        try {



                trackedVariable = varName;
                currentVariableLabel.setText("Tracking: " + varName);
                appendOutput("Started tracking variable: " + varName + "\n");

                // Afficher l'historique
                updateVariableHistory();



        } catch (Exception e) {
            appendOutput("Error tracking variable: " + e.getMessage() + "\n");
        }
    }

    /**
     * Met à jour l'affichage de l'historique de la variable suivie
     */
    private void updateVariableHistory() {
        variableHistoryModel.clear();
        currentVariableHistory.clear();

        if (trackedVariable == null ) return;

        try {


            CommandResult result = null;
            if (callback != null) {
                result = callback.executeCommand(new ShowVariableHistoryCommand(trackedVariable));
            }
            if (result!=null&&result.isSuccess() && result.getData() instanceof List) {
                @SuppressWarnings("unchecked")
                List<VariableModification> history = (List<VariableModification>) result.getData();
                currentVariableHistory = history;

                for (int i = 0; i < history.size(); i++) {
                    VariableModification mod = history.get(i);
                    String display = String.format("[%d] %s → %s (line %d, %s)",
                            i,
                            mod.getOldValue(),
                            mod.getNewValue(),
                            mod.getLineNumber(),
                            mod.getMethodName());
                    variableHistoryModel.addElement(display);
                }

                if (history.isEmpty()) {
                    variableHistoryModel.addElement("(No modifications detected)");
                }
            }

        } catch (Exception e) {
            appendOutput("Error retrieving variable history: " + e.getMessage() + "\n");
        }
    }

    /**
     * Voyage dans le temps vers la modification sélectionnée
     */
    private void timeTravelToSelectedModification() {
        int selectedIndex = variableHistoryList.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= currentVariableHistory.size()) return;

        VariableModification mod = currentVariableHistory.get(selectedIndex);
        int snapshotId = mod.getSnapshotId();

        appendOutput("\n=== Time-traveling to modification #" + selectedIndex + " ===\n");
        appendOutput("Snapshot: " + snapshotId + "\n");

        try {
            TimeTravelCommand cmd = new TimeTravelCommand(snapshotId);
            callback.executeCommand(cmd);
        } catch (Exception e) {
            appendOutput("Error during time-travel: " + e.getMessage() + "\n");
        }
    }

    /**
     * Arrête le suivi de la variable actuelle
     */
    private void clearVariableTracking() {
        trackedVariable = null;
        currentVariableLabel.setText("No variable tracked");
        variableHistoryModel.clear();
        currentVariableHistory.clear();
        appendOutput("Variable tracking cleared\n");
    }

    /**
     * Recherche tous les appels de méthodes dans l'exécution
     */
    private void findAllMethodCalls() {
        if (state == null || state.getTimelineManager() == null) return;

        methodCallsModel.clear();
        currentMethodCalls.clear();
        currentSearchLabel.setText("Search: All method calls");

        appendOutput("\n=== Searching all method calls ===\n");

        // Utiliser les données collectées automatiquement par le TimelineManager
        List<TimelineManager.MethodCallRecord> calls =
                state.getTimelineManager().getAllMethodCalls();

        // Convertir en MethodCallInfo pour l'affichage
        for (TimelineManager.MethodCallRecord call : calls) {
            MethodCallInfo info = new MethodCallInfo(
                    call.getSnapshotId(),
                    call.getMethodName(),
                    call.getLineNumber(),
                    call.getSourceFile()
            );
            currentMethodCalls.add(info);
        }

        // Afficher les résultats
        for (int i = 0; i < currentMethodCalls.size(); i++) {
            MethodCallInfo info = currentMethodCalls.get(i);
            String display = String.format("[%d] %s() at %s:%d",
                    i,
                    info.methodName,
                    info.sourceFile,
                    info.lineNumber);
            methodCallsModel.addElement(display);
        }

        appendOutput("Found " + currentMethodCalls.size() + " method calls\n");
    }

    /**
     * Recherche les appels à la méthode sélectionnée dans la call stack
     */
    private void findCallsToSelectedMethod() {
        int selectedIndex = callStackList.getSelectedIndex();
        if (selectedIndex < 0 || state == null || state.getContext() == null) return;

        try {
            DebugFrame frame = state.getContext().getCallStack().getFrames().get(selectedIndex);
            String methodName = frame.getLocation().method().name();

            findCallsToMethodByName(methodName);

        } catch (Exception e) {
            appendOutput("Error finding calls: " + e.getMessage() + "\n");
        }
    }

    /**
     * Demande le nom d'une méthode et recherche ses appels
     */
    private void findCallsToMethod() {
        String methodName = JOptionPane.showInputDialog(
                this,
                "Enter method name to search:",
                "Find Method Calls",
                JOptionPane.QUESTION_MESSAGE
        );

        if (methodName != null && !methodName.trim().isEmpty()) {
            findCallsToMethodByName(methodName.trim());
        }
    }

    /**
     * Recherche tous les appels à une méthode spécifique
     */
    private void findCallsToMethodByName(String methodName) {
        if (state == null || state.getTimelineManager() == null) return;

        methodCallsModel.clear();
        currentMethodCalls.clear();
        currentSearchLabel.setText("Search: Calls to " + methodName + "()");

        appendOutput("\n=== Searching calls to " + methodName + "() ===\n");

        // Utiliser les données collectées automatiquement par le TimelineManager
        List<TimelineManager.MethodCallRecord> calls =
                state.getTimelineManager().getCallsToMethod(methodName);

        // Convertir en MethodCallInfo pour l'affichage
        for (TimelineManager.MethodCallRecord call : calls) {
            MethodCallInfo info = new MethodCallInfo(
                    call.getSnapshotId(),
                    call.getMethodName(),
                    call.getLineNumber(),
                    call.getSourceFile()
            );
            currentMethodCalls.add(info);
        }

        // Afficher les résultats
        for (int i = 0; i < currentMethodCalls.size(); i++) {
            MethodCallInfo info = currentMethodCalls.get(i);
            String display = String.format("[%d] %s() at %s:%d",
                    i,
                    info.methodName,
                    info.sourceFile,
                    info.lineNumber);
            methodCallsModel.addElement(display);
        }

        appendOutput("Found " + currentMethodCalls.size() + " calls to " + methodName + "()\n");
    }

    /**
     * Voyage dans le temps vers l'appel de méthode sélectionné
     */
    private void timeTravelToSelectedMethodCall() {
        int selectedIndex = methodCallsList.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= currentMethodCalls.size()) return;

        MethodCallInfo info = currentMethodCalls.get(selectedIndex);
        int snapshotId = info.snapshotId;

        appendOutput("\n=== Time-traveling to method call #" + selectedIndex + " ===\n");
        appendOutput("Snapshot: " + snapshotId + "\n");

        try {
            TimeTravelCommand cmd = new TimeTravelCommand(snapshotId);
            callback.executeCommand(cmd);
        } catch (Exception e) {
            appendOutput("Error during time-travel: " + e.getMessage() + "\n");
        }
    }

    /**
     * Efface les résultats de recherche d'appels
     */
    private void clearMethodCallsSearch() {
        methodCallsModel.clear();
        currentMethodCalls.clear();
        currentSearchLabel.setText("No search performed");
        appendOutput("Method calls search cleared\n");
    }

    // ========== UTILITAIRES ==========

    /**
     * Extrait le nom de variable d'un nœud de l'arbre inspector
     */
    private String extractVariableName(String nodeText) {
        if (nodeText.contains(" = ")) {
            return nodeText.substring(0, nodeText.indexOf(" = ")).trim();
        }
        return nodeText.trim();
    }

    /**
     * Classe interne pour stocker les informations d'un appel de méthode
     */
    private static class MethodCallInfo {
        int snapshotId;
        String methodName;
        int lineNumber;
        String sourceFile;

        MethodCallInfo(int snapshotId, String methodName, int lineNumber, String sourceFile) {
            this.snapshotId = snapshotId;
            this.methodName = methodName;
            this.lineNumber = lineNumber;
            this.sourceFile = sourceFile;
        }
    }

    // ========== MISE À JOUR DE L'INTERFACE ==========

    public void updateDebuggerState(DebuggerState state, Location location, ThreadReference thread) {
        this.state = state;
        this.currentThread = thread;

        if (location != null) {
            try {
                currentSourceFile = location.sourceName();
                currentLine = location.lineNumber();

                loadSourceCode(location);
                updateCallStack();
                updateInspector();

                // Mettre à jour l'historique de la variable si on en suit une
                if (trackedVariable != null) {
                    updateVariableHistory();
                }

            } catch (AbsentInformationException e) {
                appendOutput("Warning: No debug information available\n");
            }
        }
    }

    private void loadSourceCode(Location location) {
        try {
            String className = location.declaringType().name();
            String fileName = location.sourceName();

            String sourcePath = findSourceFile(className, fileName);

            if (sourcePath != null) {
                List<String> lines = Files.readAllLines(Paths.get(sourcePath));
                sourceLines.clear();

                for (int i = 0; i < lines.size(); i++) {
                    sourceLines.put(i + 1, lines.get(i));
                }

                sourceCodePanel.setSourceLines(lines);
                sourceCodePanel.setCurrentLine(currentLine);
            } else {
                List<String> errorLines = Arrays.asList(
                        "// Source code not available",
                        "// Method: " + location.method().name(),
                        "// Line: " + currentLine
                );
                sourceCodePanel.setSourceLines(errorLines);
            }

        } catch (Exception e) {
            List<String> errorLines = Arrays.asList(
                    "// Error loading source code",
                    "// " + e.getMessage()
            );
            sourceCodePanel.setSourceLines(errorLines);
        }
    }

    private String findSourceFile(String className, String fileName) {
        String[] possiblePaths = {
                "src/dbg/sourceBase/" + fileName,
                "src/" + fileName,
                "../src/dbg/sourceBase/" + fileName,
                fileName
        };

        for (String path : possiblePaths) {
            if (Files.exists(Paths.get(path))) {
                return path;
            }
        }

        return null;
    }

    private void updateCallStack() {
        callStackModel.clear();

        if (state == null || state.getContext() == null) {
            return;
        }

        CallStack stack = state.getContext().getCallStack();
        if (stack != null) {
            List<DebugFrame> frames = stack.getFrames();
            for (int i = 0; i < frames.size(); i++) {
                DebugFrame frame = frames.get(i);
                callStackModel.addElement(String.format("[%d] %s", i, frame.toString()));
            }

            if (!frames.isEmpty()) {
                callStackList.setSelectedIndex(0);
            }
        }
    }

    private void updateInspector() {
        updateInspectorForFrame(0);
    }

    private void updateInspectorForFrame(int frameIndex) {
        inspectorRoot.removeAllChildren();

        if (state == null || state.getContext() == null) {
            inspectorTreeModel.reload();
            return;
        }

        CallStack stack = state.getContext().getCallStack();
        if (stack != null && frameIndex < stack.getFrames().size()) {
            DebugFrame frame = stack.getFrames().get(frameIndex);

            DefaultMutableTreeNode localsNode = new DefaultMutableTreeNode("Local Variables");
            for (Variable var : frame.getTemporaries()) {
                localsNode.add(createVariableNode(var.getName(), var.getValue(), 0));
            }
            inspectorRoot.add(localsNode);

            ObjectReference receiver = frame.getReceiver();
            if (receiver != null) {
                inspectorRoot.add(createVariableNode("this", receiver, 0));
            }
        }

        inspectorTreeModel.reload();
        expandTree(inspectorTree, 2);
    }

    private DefaultMutableTreeNode createVariableNode(String name, Value value, int depth) {
        String display = name + " = " + formatValueShort(value);
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(display);

        if (depth > 5) {
            node.add(new DefaultMutableTreeNode("... (depth limit reached)"));
            return node;
        }

        if (value instanceof ObjectReference && !(value instanceof StringReference)) {
            ObjectReference obj = (ObjectReference) value;

            try {
                if (value instanceof ArrayReference) {
                    ArrayReference array = (ArrayReference) value;
                    int length = array.length();

                    for (int i = 0; i < Math.min(length, 50); i++) {
                        node.add(createVariableNode("[" + i + "]", array.getValue(i), depth + 1));
                    }

                    if (length > 50) {
                        node.add(new DefaultMutableTreeNode("... (" + (length - 50) + " more elements)"));
                    }
                } else {
                    ReferenceType type = obj.referenceType();
                    for (Field field : type.allFields()) {
                        try {
                            Value fieldValue = obj.getValue(field);
                            node.add(createVariableNode(field.name(), fieldValue, depth + 1));
                        } catch (Exception e) {
                            node.add(new DefaultMutableTreeNode(field.name() + " = <inaccessible>"));
                        }
                    }
                }
            } catch (ObjectCollectedException e) {
                node.add(new DefaultMutableTreeNode("<collected by GC>"));
            }
        }

        return node;
    }

    private String formatValueShort(Value v) {
        if (v == null) return "null";
        if (v instanceof StringReference) return "\"" + ((StringReference) v).value() + "\"";
        if (v instanceof PrimitiveValue) return v.toString();
        if (v instanceof ArrayReference) return "Array[" + ((ArrayReference) v).length() + "]";
        if (v instanceof ObjectReference) {
            String typeName = v.type().name();
            if (typeName.contains(".")) {
                typeName = typeName.substring(typeName.lastIndexOf(".") + 1);
            }
            return typeName + " (id=" + ((ObjectReference) v).uniqueID() + ")";
        }
        return v.toString();
    }

    private void expandTree(JTree tree, int levels) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        expandNode(tree, root, levels);
    }

    private void expandNode(JTree tree, DefaultMutableTreeNode node, int levelsRemaining) {
        if (levelsRemaining <= 0) return;

        tree.expandPath(new TreePath(node.getPath()));

        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            expandNode(tree, child, levelsRemaining - 1);
        }
    }

    public void appendOutput(String text) {
        SwingUtilities.invokeLater(() -> {
            outputArea.append(text);
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
        });
    }

    public void setCallback(DebuggerCallback callback) {
        this.callback = callback;
    }

    public void enableControls(boolean enabled) {
        stepIntoButton.setEnabled(enabled);
        stepOverButton.setEnabled(enabled);
        continueButton.setEnabled(enabled);
    }

    // ========== NOUVELLES MÉTHODES POUR MODE REPLAY ==========

    /**
     * Ajoute du texte dans l'onglet "Program Output" SEULEMENT
     * Utilisé pour System.out.println du programme debuggé
     */
    public void appendProgramOutput(String text) {
        SwingUtilities.invokeLater(() -> {
            programOutputArea.append(text);
            programOutputArea.setCaretPosition(programOutputArea.getDocument().getLength());
        });
    }

    /**
     * Met à jour l'UI à partir d'un snapshot (mode replay)
     * Utilisé quand la VM est déconnectée et qu'on ne peut plus utiliser ThreadReference
     */
    public void updateFromSnapshot(ExecutionSnapshot snapshot) {
        if (snapshot == null) return;

        try {
            // 1. Mettre à jour le source code
            currentSourceFile = snapshot.getSourceFile();
            currentLine = snapshot.getLineNumber();
            loadSourceCodeFromFile(currentSourceFile);
            sourceCodePanel.setCurrentLine(currentLine);

            // 2. Mettre à jour la call stack depuis le snapshot
            updateCallStackFromSnapshot(snapshot);

            // 3. Mettre à jour l'inspector depuis le snapshot
            updateInspectorFromSnapshot(snapshot);

            // 4. Mettre à jour le program output pour ce snapshot
            updateProgramOutputFromSnapshot(snapshot);

        } catch (Exception e) {
            appendOutput("Error in updateFromSnapshot: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    /**
     * Charge le code source à partir du nom de fichier
     */
    private void loadSourceCodeFromFile(String fileName) {
        try {
            // Chercher le fichier dans plusieurs emplacements possibles
            String[] possiblePaths = {
                    "src/dbg/sourceBase/" + fileName,
                    "src/" + fileName,
                    "../src/dbg/sourceBase/" + fileName,
                    "dbg/sourceBase/" + fileName,
                    fileName
            };

            for (String path : possiblePaths) {
                if (Files.exists(Paths.get(path))) {
                    List<String> lines = Files.readAllLines(Paths.get(path));
                    sourceLines.clear();
                    for (int i = 0; i < lines.size(); i++) {
                        sourceLines.put(i + 1, lines.get(i));
                    }
                    sourceCodePanel.setSourceLines(lines);
                    return;
                }
            }

            // Si aucun fichier trouvé
            List<String> errorLines = Arrays.asList(
                    "// Source code not available: " + fileName,
                    "// Current line: " + currentLine,
                    "",
                    "// Please ensure source files are in one of:",
                    "//   - src/dbg/sourceBase/",
                    "//   - src/",
                    "//   - Current directory"
            );
            sourceCodePanel.setSourceLines(errorLines);

        } catch (Exception e) {
            List<String> errorLines = Arrays.asList(
                    "// Error loading source code: " + fileName,
                    "// " + e.getMessage()
            );
            sourceCodePanel.setSourceLines(errorLines);
        }
    }

    /**
     * Met à jour la call stack depuis le snapshot
     */
    private void updateCallStackFromSnapshot(ExecutionSnapshot snapshot) {
        callStackModel.clear();

        if (snapshot.getCallStack() != null) {
            List<String> stack = snapshot.getCallStack();
            for (int i = 0; i < stack.size(); i++) {
                callStackModel.addElement("[" + i + "] " + stack.get(i));
            }

            if (!stack.isEmpty()) {
                callStackList.setSelectedIndex(0);
            }
        }
    }

    /**
     * Met à jour l'inspector depuis le snapshot
     */
    private void updateInspectorFromSnapshot(ExecutionSnapshot snapshot) {
        inspectorRoot.removeAllChildren();

        if (snapshot.getVariables() != null && !snapshot.getVariables().isEmpty()) {
            DefaultMutableTreeNode localsNode = new DefaultMutableTreeNode("Local Variables");

            // Trier les variables par nom pour un affichage cohérent
            List<Map.Entry<String, String>> sortedVars = new ArrayList<>(snapshot.getVariables().entrySet());
            sortedVars.sort(Map.Entry.comparingByKey());

            for (Map.Entry<String, String> entry : sortedVars) {
                String varDisplay = entry.getKey() + " = " + entry.getValue();
                localsNode.add(new DefaultMutableTreeNode(varDisplay));
            }

            inspectorRoot.add(localsNode);
        }

        inspectorTreeModel.reload();
        expandTree(inspectorTree, 2);
    }

    /**
     * Affiche la sortie du programme jusqu'à ce snapshot
     */
    private void updateProgramOutputFromSnapshot(ExecutionSnapshot snapshot) {
        programOutputArea.setText("");
        programOutputArea.append(snapshot.getProgramOutputSoFar());
        programOutputArea.setCaretPosition(programOutputArea.getDocument().getLength());
    }

    /**
     * Affiche toutes les variables trackées avec leurs modifications
     */
    private void showAllTrackedVariables() {
        if (state == null) return;

        variableHistoryModel.clear();
        currentVariableHistory.clear();
        trackedVariable = null;
        currentVariableLabel.setText("All Variables with Modifications");

        Map<String, List<VariableModification>> allVars =
                state.getTimelineManager().getAllVariablesWithHistory();

        if (allVars.isEmpty()) {
            variableHistoryModel.addElement("(No variable modifications found)");
            appendOutput("No variable modifications found\n");
            return;
        }

        // Afficher toutes les variables avec leurs modifications
        int totalMods = 0;
        for (Map.Entry<String, List<VariableModification>> entry : allVars.entrySet()) {
            String varName = entry.getKey();
            List<VariableModification> mods = entry.getValue();

            // En-tête de variable
            variableHistoryModel.addElement("━━━ " + varName + " (" + mods.size() + " modifications) ━━━");

            // Ajouter les modifications
            for (int i = 0; i < mods.size(); i++) {
                VariableModification mod = mods.get(i);
                String display = String.format("  [%d] %s → %s (line %d, %s)",
                        i,
                        mod.getOldValue(),
                        mod.getNewValue(),
                        mod.getLineNumber(),
                        mod.getMethodName());
                variableHistoryModel.addElement(display);
            }

            variableHistoryModel.addElement("");
            currentVariableHistory.addAll(mods);
            totalMods += mods.size();
        }

        appendOutput("Showing " + allVars.size() + " variables with " + totalMods + " total modifications\n");
    }
}