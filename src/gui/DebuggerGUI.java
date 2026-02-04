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

public class DebuggerGUI extends JFrame {
    private SourceCodePanel sourceCodePanel;
    private JList<String> callStackList;
    private JTree inspectorTree;
    private JTextArea outputArea;
    private JTextArea programOutputArea;
    private JButton stepOverButton;
    private JButton stepIntoButton;
    private JButton continueButton;
    private JButton stopButton;
    private JPanel ttqPanel;
    private DefaultListModel<String> variableHistoryModel;
    private JList<String> variableHistoryList;
    private DefaultListModel<String> methodCallsModel;
    private JList<String> methodCallsList;
    private JLabel currentVariableLabel;
    private JLabel currentSearchLabel;
    private DefaultListModel<String> callStackModel;
    private DefaultMutableTreeNode inspectorRoot;
    private DefaultTreeModel inspectorTreeModel;
    private DebuggerState state;
    private CommandInterpreter interpreter;
    private Map<Integer, String> sourceLines;
    private int currentLine = -1;
    private String currentSourceFile = "";
    private ThreadReference currentThread;
    private DebuggerCallback callback;
    private String trackedVariable = null;
    private List<VariableModification> currentVariableHistory = new ArrayList<>();
    private List<MethodCallInfo> currentMethodCalls = new ArrayList<>();
    private DefaultListModel<String> LastvariableHistoryModel= new DefaultListModel<>();

    public interface DebuggerCallback {
        CommandResult executeCommand(Command command);
        void placeBreakpoint(String file, int line);
        void stop();
    }

    // Constructeur principal : initialise la fenêtre, la taille et les composants graphiques
    public DebuggerGUI() {
        super("Time-Traveling Debugger - Graphical Interface");
        sourceLines = new HashMap<>();
        initComponents();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1400, 900);
        setLocationRelativeTo(null);
    }

    // Configure l'agencement global des panneaux (gauche, centre, droite)
    private void initComponents() {
        setLayout(new BorderLayout(5, 5));
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        JPanel leftPanel = createLeftPanel();
        JPanel centerPanel = createCenterPanel();
        JPanel rightPanel = createRightPanel();

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

        JLabel statusBar = new JLabel(" Ready - Replay Mode");
        statusBar.setBorder(new BevelBorder(BevelBorder.LOWERED));
        add(statusBar, BorderLayout.SOUTH);
    }

    // Crée le panneau de gauche contenant les boutons de contrôle et le code source
    private JPanel createLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.add(createControlPanel(), BorderLayout.NORTH);
        panel.add(createSourceCodePanel(), BorderLayout.CENTER);
        return panel;
    }

    // Crée la barre d'outils avec les boutons Step Into, Step Over, Continue, Stop
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        panel.setBorder(new TitledBorder("Execution Control (Replay Mode)"));

        stepIntoButton = new JButton("Step Into");
        stepIntoButton.addActionListener(e -> executeStepInto());

        stepOverButton = new JButton("Step Over");
        stepOverButton.addActionListener(e -> executeStepOver());

        continueButton = new JButton("Continue");
        continueButton.addActionListener(e -> executeContinue());

        stopButton = new JButton("Stop");
        stopButton.addActionListener(e -> executeStop());
        stopButton.setBackground(new Color(220, 100, 100));

        panel.add(stepIntoButton);
        panel.add(stepOverButton);
        panel.add(continueButton);
        panel.add(stopButton);
        return panel;
    }

    // Initialise le composant d'affichage du code source et gère les clics pour les breakpoints
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

    // Crée le panneau central contenant la pile d'appels et l'inspecteur de variables
    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(250);
        splitPane.setTopComponent(createCallStackPanel());
        splitPane.setBottomComponent(createInspectorPanel());
        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    // Crée la liste affichant la Call Stack (pile d'exécution)
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
                    }
                }
            }
        });

        JPopupMenu callStackMenu = new JPopupMenu();
        JMenuItem findAllCallsItem = new JMenuItem("Find All Method Calls");
        findAllCallsItem.addActionListener(e -> findAllMethodCalls());
        JMenuItem findCallsToItem = new JMenuItem("Find Calls to This Method");
        findCallsToItem.addActionListener(e -> findCallsToSelectedMethod());
        callStackMenu.add(findAllCallsItem);
        callStackMenu.add(findCallsToItem);
        callStackList.setComponentPopupMenu(callStackMenu);

        panel.add(new JScrollPane(callStackList), BorderLayout.CENTER);
        return panel;
    }

    // Crée l'arbre (JTree) pour inspecter les variables locales et objets
    private JPanel createInspectorPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Inspector (Variables Tree)"));

        inspectorRoot = new DefaultMutableTreeNode("Variables");
        inspectorTreeModel = new DefaultTreeModel(inspectorRoot);
        inspectorTree = new JTree(inspectorTreeModel);
        inspectorTree.setFont(new Font("Monospaced", Font.PLAIN, 11));
        //
        JPopupMenu inspectorMenu = new JPopupMenu();
        JMenuItem followItem = new JMenuItem("Follow Variable");
        followItem.addActionListener(e -> followSelectedVariable());
        inspectorMenu.add(followItem);
        inspectorTree.setComponentPopupMenu(inspectorMenu);

        panel.add(new JScrollPane(inspectorTree), BorderLayout.CENTER);
        return panel;
    }

    // Crée le panneau de droite contenant les outils de Time Travel et les sorties console
    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(450);
        splitPane.setTopComponent(createTimeTravelingQueriesPanel());
        splitPane.setBottomComponent(createOutputPanel());
        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    // Crée les onglets pour le suivi des variables et des appels de méthodes
    private JPanel createTimeTravelingQueriesPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new TitledBorder("Time-Traveling Queries"));
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Variable Tracking", createVariableTrackingPanel());
        tabbedPane.addTab("Method Calls", createMethodCallsPanel());
        panel.add(tabbedPane, BorderLayout.CENTER);
        return panel;
    }

    // Crée l'interface pour suivre l'historique d'une variable spécifique
    private JPanel createVariableTrackingPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        JPanel headerPanel = new JPanel(new BorderLayout());

        currentVariableLabel = new JLabel("No variable selected");
        currentVariableLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        currentVariableLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        headerPanel.add(currentVariableLabel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton showAllButton = new JButton("Show All");
        showAllButton.addActionListener(e -> showAllTrackedVariables());
        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> clearVariableTracking());
        buttonPanel.add(showAllButton);
        buttonPanel.add(clearButton);
        headerPanel.add(buttonPanel, BorderLayout.EAST);
        panel.add(headerPanel, BorderLayout.NORTH);

        variableHistoryModel = new DefaultListModel<>();
        variableHistoryList = new JList<>(variableHistoryModel);
        variableHistoryList.setFont(new Font("Monospaced", Font.PLAIN, 11));
        variableHistoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        variableHistoryList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    timeTravelToSelectedModification();
                }
            }
        });

        panel.add(new JScrollPane(variableHistoryList), BorderLayout.CENTER);
        JLabel infoLabel = new JLabel("<html><i>Right-click on a variable in Inspector and select 'Follow Variable'</i></html>");
        infoLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.add(infoLabel, BorderLayout.SOUTH);
        return panel;
    }

    // Crée l'interface pour rechercher et lister les appels de méthodes
    private JPanel createMethodCallsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        JPanel headerPanel = new JPanel(new BorderLayout());

        currentSearchLabel = new JLabel("No search performed");
        currentSearchLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        currentSearchLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        headerPanel.add(currentSearchLabel, BorderLayout.CENTER);

        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> clearMethodCallsSearch());
        headerPanel.add(clearButton, BorderLayout.EAST);
        panel.add(headerPanel, BorderLayout.NORTH);

        methodCallsModel = new DefaultListModel<>();
        methodCallsList = new JList<>(methodCallsModel);
        methodCallsList.setFont(new Font("Monospaced", Font.PLAIN, 11));
        methodCallsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        methodCallsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    timeTravelToSelectedMethodCall();
                }
            }
        });

        panel.add(new JScrollPane(methodCallsList), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton findAllButton = new JButton("Find All Calls");
        findAllButton.addActionListener(e -> findAllMethodCalls());
        JButton findSpecificButton = new JButton("Find Calls To...");
        findSpecificButton.addActionListener(e -> findCallsToMethod());
        buttonPanel.add(findAllButton);
        buttonPanel.add(findSpecificButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }

    // Crée la zone de texte pour la sortie du programme et les logs du débogueur
    private JPanel createOutputPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JTabbedPane outputTabs = new JTabbedPane();

        programOutputArea = new JTextArea();
        programOutputArea.setEditable(false);
        programOutputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        programOutputArea.setBackground(Color.WHITE);
        outputTabs.addTab("Program Output", new JScrollPane(programOutputArea));

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        outputArea.setBackground(new Color(240, 240, 240));
        outputTabs.addTab("Debugger Messages", new JScrollPane(outputArea));

        panel.add(outputTabs, BorderLayout.CENTER);
        panel.setBorder(new TitledBorder("Output"));
        return panel;
    }

    // Actions des boutons de contrôle
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

    // Récupère la variable sélectionnée dans l'arbre et commence à la suivre
    private void followSelectedVariable() {
        TreePath path = inspectorTree.getSelectionPath();
        if (path == null) return;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        String varName = extractVariableName(node.toString());
        if (varName == null || varName.isEmpty()) {
            return;
        }

        try {
            trackedVariable = varName;
            currentVariableLabel.setText("Tracking: " + varName);
            updateVariableHistory();
        } catch (Exception e) {
        }
    }

    // Met à jour la liste des modifications de la variable suivie
    private void updateVariableHistory() {
        variableHistoryModel.clear();
        currentVariableHistory.clear();

        if (trackedVariable == null) return;

        try {
            List<VariableModification> history =
                    state.getTimelineManager().getVariableHistoryUpToCurrent(trackedVariable);
            currentVariableHistory = history;

            for (int i = 0; i < history.size(); i++) {
                VariableModification mod = history.get(i);
                String display = String.format("[%d] %s → %s (line %d, %s)",
                        i, mod.getOldValue(), mod.getNewValue(),
                        mod.getLineNumber(), mod.getMethodName());
                variableHistoryModel.addElement(display);

            }

            if (history.isEmpty()) {
                variableHistoryModel.addElement("(No modifications detected yet)");
            }
        } catch (Exception e) {
        }
    }

    // Navigue vers le snapshot correspondant à la modification sélectionnée
    private void timeTravelToSelectedModification() {
        int selectedIndex = variableHistoryList.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= currentVariableHistory.size()) return;

        VariableModification mod = currentVariableHistory.get(selectedIndex);
        try {
            TimeTravelCommand cmd = new TimeTravelCommand(mod.getSnapshotId());
            if (callback != null) {
                callback.executeCommand(cmd);
            }
        } catch (Exception e) {
        }
    }

    private void clearVariableTracking() {
        trackedVariable = null;
        currentVariableLabel.setText("No variable tracked");
        variableHistoryModel.clear();
        currentVariableHistory.clear();
    }

    private void refreshMethodCallsDisplay() {
        if (state == null || state.getTimelineManager() == null) return;

        String searchLabel = currentSearchLabel.getText();
        if (searchLabel.startsWith("Search: All method calls")) {
            findAllMethodCallsInternal(false);
        } else if (searchLabel.startsWith("Search: Calls to ")) {
            String methodName = searchLabel.substring("Search: Calls to ".length());
            methodName = methodName.replace("()", "").trim();
            findCallsToMethodByNameInternal(methodName, false);
        }
    }

    private void findAllMethodCallsInternal(boolean showMessage) {
        if (state == null || state.getTimelineManager() == null) return;

        methodCallsModel.clear();
        currentMethodCalls.clear();
        currentSearchLabel.setText("Search: All method calls");

        List<TimelineManager.MethodCallRecord> calls =
                state.getTimelineManager().getAllMethodCallsUpToCurrent();

        for (TimelineManager.MethodCallRecord call : calls) {
            currentMethodCalls.add(new MethodCallInfo(
                    call.getSnapshotId(), call.getMethodName(),
                    call.getLineNumber(), call.getSourceFile()));
        }

        for (int i = 0; i < currentMethodCalls.size(); i++) {
            MethodCallInfo info = currentMethodCalls.get(i);
            methodCallsModel.addElement(String.format("[%d] %s() at %s:%d",
                    i, info.methodName, info.sourceFile, info.lineNumber));
        }
    }

    private void findAllMethodCalls() {
        findAllMethodCallsInternal(true);
    }

    private void findCallsToSelectedMethod() {
        int selectedIndex = callStackList.getSelectedIndex();
        if (selectedIndex < 0 || state == null || state.getContext() == null) return;

        try {
            DebugFrame frame = state.getContext().getCallStack().getFrames().get(selectedIndex);
            findCallsToMethodByName(frame.getLocation().method().name());
        } catch (Exception e) {
        }
    }

    private void findCallsToMethod() {
        String methodName = JOptionPane.showInputDialog(this,
                "Enter method name to search:", "Find Method Calls",
                JOptionPane.QUESTION_MESSAGE);

        if (methodName != null && !methodName.trim().isEmpty()) {
            findCallsToMethodByName(methodName.trim());
        }
    }

    private void findCallsToMethodByName(String methodName) {
        findCallsToMethodByNameInternal(methodName, true);
    }

    private void findCallsToMethodByNameInternal(String methodName, boolean showMessage) {
        if (state == null || state.getTimelineManager() == null) return;

        methodCallsModel.clear();
        currentMethodCalls.clear();
        currentSearchLabel.setText("Search: Calls to " + methodName + "()");

        List<TimelineManager.MethodCallRecord> calls =
                state.getTimelineManager().getCallsToMethodUpToCurrent(methodName);

        for (TimelineManager.MethodCallRecord call : calls) {
            currentMethodCalls.add(new MethodCallInfo(
                    call.getSnapshotId(), call.getMethodName(),
                    call.getLineNumber(), call.getSourceFile()));
        }

        for (int i = 0; i < currentMethodCalls.size(); i++) {
            MethodCallInfo info = currentMethodCalls.get(i);
            methodCallsModel.addElement(String.format("[%d] %s() at %s:%d",
                    i, info.methodName, info.sourceFile, info.lineNumber));
        }
    }

    private void timeTravelToSelectedMethodCall() {
        int selectedIndex = methodCallsList.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= currentMethodCalls.size()) return;

        try {
            TimeTravelCommand cmd = new TimeTravelCommand(currentMethodCalls.get(selectedIndex).snapshotId);
            if (callback != null) {
                callback.executeCommand(cmd);
            }
        } catch (Exception e) {
        }
    }

    private void clearMethodCallsSearch() {
        methodCallsModel.clear();
        currentMethodCalls.clear();
        currentSearchLabel.setText("No search performed");
    }

    private void showAllTrackedVariables() {
        if (state == null) return;

        variableHistoryModel.clear();
        currentVariableHistory.clear();
        trackedVariable = null;
        currentVariableLabel.setText("All Variables with Modifications");

        Map<String, List<VariableModification>> allVars =
                state.getTimelineManager().getAllVariablesWithHistoryUpToCurrent();

        if (allVars.isEmpty()) {
            variableHistoryModel.addElement("(No variable modifications found yet)");
            return;
        }

        for (Map.Entry<String, List<VariableModification>> entry : allVars.entrySet()) {
            variableHistoryModel.addElement("━━━ " + entry.getKey() + " (" + entry.getValue().size() + " modifications) ━━━");
            for (int i = 0; i < entry.getValue().size(); i++) {
                VariableModification mod = entry.getValue().get(i);
                variableHistoryModel.addElement(String.format("  [%d] %s → %s (line %d, %s)",
                        i, mod.getOldValue(), mod.getNewValue(),
                        mod.getLineNumber(), mod.getMethodName()));
            }
            variableHistoryModel.addElement("");
            currentVariableHistory.addAll(entry.getValue());
        }
    }

    private String extractVariableName(String nodeText) {
        if (nodeText.contains(" = ")) {
            return nodeText.substring(0, nodeText.indexOf(" = ")).trim();
        }
        return nodeText.trim();
    }

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

    // Met à jour l'état complet de l'interface (code, pile, inspecteur) à partir du nouvel état du débogueur
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
                if (trackedVariable != null) {
                    updateVariableHistory();
                }
            } catch (AbsentInformationException e) {
            }
        }
    }

    // Charge le contenu du fichier source et surligne la ligne actuelle
    private void loadSourceCode(Location location) {
        try {
            String sourcePath = findSourceFile(location.declaringType().name(), location.sourceName());

            if (sourcePath != null) {
                List<String> lines = Files.readAllLines(Paths.get(sourcePath));
                sourceLines.clear();
                for (int i = 0; i < lines.size(); i++) {
                    sourceLines.put(i + 1, lines.get(i));
                }
                sourceCodePanel.setSourceLines(lines);
                sourceCodePanel.setCurrentLine(currentLine);
            } else {
                sourceCodePanel.setSourceLines(Arrays.asList("// Source code not available"));
            }
        } catch (Exception e) {
            sourceCodePanel.setSourceLines(Arrays.asList("// Error loading source code"));
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

    // Rafraîchit la liste de la Call Stack
    private void updateCallStack() {
        callStackModel.clear();
        if (state == null || state.getContext() == null) {
            return;
        }

        CallStack stack = state.getContext().getCallStack();
        if (stack != null) {
            List<DebugFrame> frames = stack.getFrames();
            for (int i = 0; i < frames.size(); i++) {
                callStackModel.addElement(String.format("[%d] %s", i, frames.get(i).toString()));
            }
            if (!frames.isEmpty()) {
                callStackList.setSelectedIndex(0);
            }
        }
    }

    private void updateInspector() {
        updateInspectorForFrame(0);
    }

    // Met à jour l'arbre des variables pour une frame spécifique de la pile
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
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(name + " = " + formatValueShort(value));

        if (depth > 5) {
            node.add(new DefaultMutableTreeNode("..."));
            return node;
        }

        if (value instanceof ObjectReference && !(value instanceof StringReference)) {
            ObjectReference obj = (ObjectReference) value;
            try {
                if (value instanceof ArrayReference) {
                    ArrayReference array = (ArrayReference) value;
                    for (int i = 0; i < Math.min(array.length(), 50); i++) {
                        node.add(createVariableNode("[" + i + "]", array.getValue(i), depth + 1));
                    }
                    if (array.length() > 50) {
                        node.add(new DefaultMutableTreeNode("..."));
                    }
                } else {
                    for (Field field : obj.referenceType().allFields()) {
                        try {
                            node.add(createVariableNode(field.name(), obj.getValue(field), depth + 1));
                        } catch (Exception e) {
                            node.add(new DefaultMutableTreeNode(field.name() + " = <inaccessible>"));
                        }
                    }
                }
            } catch (ObjectCollectedException e) {
                node.add(new DefaultMutableTreeNode("<collected>"));
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
            expandNode(tree, (DefaultMutableTreeNode) node.getChildAt(i), levelsRemaining - 1);
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

    public void setDebuggerState(DebuggerState state) {
        this.state = state;
    }

    public void enableControls(boolean enabled) {
        stepIntoButton.setEnabled(enabled);
        stepOverButton.setEnabled(enabled);
        continueButton.setEnabled(enabled);
    }

    public void appendProgramOutput(String text) {
        SwingUtilities.invokeLater(() -> {
            programOutputArea.append(text);
            programOutputArea.setCaretPosition(programOutputArea.getDocument().getLength());
        });
    }

    // Met à jour l'interface pour afficher l'état d'un snapshot passé (Time Travel)
    public void updateFromSnapshot(ExecutionSnapshot snapshot) {
        if (snapshot == null) return;

        try {
            currentSourceFile = snapshot.getSourceFile();
            currentLine = snapshot.getLineNumber();
            loadSourceCodeFromFile(currentSourceFile);
            sourceCodePanel.setCurrentLine(currentLine);
            updateCallStackFromSnapshot(snapshot);
            updateInspectorFromSnapshot(snapshot);
            updateProgramOutputFromSnapshot(snapshot);

            if (trackedVariable != null) {
                updateVariableHistory();
            }

            if (!currentMethodCalls.isEmpty()) {
                refreshMethodCallsDisplay();
            }
        } catch (Exception e) {
        }
    }

    private void loadSourceCodeFromFile(String fileName) {
        try {
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
            sourceCodePanel.setSourceLines(Arrays.asList("// Source code not available"));
        } catch (Exception e) {
            sourceCodePanel.setSourceLines(Arrays.asList("// Error loading source"));
        }
    }

    private void updateCallStackFromSnapshot(ExecutionSnapshot snapshot) {
        callStackModel.clear();
        if (snapshot.getCallStack() != null) {
            for (int i = 0; i < snapshot.getCallStack().size(); i++) {
                callStackModel.addElement("[" + i + "] " + snapshot.getCallStack().get(i));
            }
            if (!snapshot.getCallStack().isEmpty()) {
                callStackList.setSelectedIndex(0);
            }
        }
    }

    private void updateInspectorFromSnapshot(ExecutionSnapshot snapshot) {
        inspectorRoot.removeAllChildren();

        if (snapshot.getVariables() != null && !snapshot.getVariables().isEmpty()) {
            DefaultMutableTreeNode localsNode = new DefaultMutableTreeNode("Local Variables");
            List<Map.Entry<String, String>> sortedVars = new ArrayList<>(snapshot.getVariables().entrySet());
            sortedVars.sort(Map.Entry.comparingByKey());

            for (Map.Entry<String, String> entry : sortedVars) {
                localsNode.add(new DefaultMutableTreeNode(entry.getKey() + " = " + entry.getValue()));
            }
            inspectorRoot.add(localsNode);
        }

        inspectorTreeModel.reload();
        expandTree(inspectorTree, 2);
    }

    private void updateProgramOutputFromSnapshot(ExecutionSnapshot snapshot) {
        programOutputArea.setText("");
        programOutputArea.append(snapshot.getProgramOutputSoFar());
        programOutputArea.setCaretPosition(programOutputArea.getDocument().getLength());
    }
}