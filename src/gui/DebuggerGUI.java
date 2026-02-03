package gui;

import commands.*;
import models.*;
import com.sun.jdi.*;
import com.sun.jdi.event.*;

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
    // Composants de l'interface
    private SourceCodePanel sourceCodePanel;
    private JList<String> callStackList;
    private JTree inspectorTree;
    private JTextArea outputArea;

    // Boutons de contrôle
    private JButton stepOverButton;
    private JButton stepIntoButton;
    private JButton continueButton;
    private JButton stopButton;

    // Modèles de données
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

    public interface DebuggerCallback {
        void executeCommand(Command command);
        void placeBreakpoint(String file, int line);
        void stop();

    }

    public DebuggerGUI() {
        super("Java Debugger - Graphical Interface");
        sourceLines = new HashMap<>();
        initComponents();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));


        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setDividerLocation(700);


        JPanel leftPanel = createLeftPanel();


        JPanel rightPanel = createRightPanel();

        mainSplit.setLeftComponent(leftPanel);
        mainSplit.setRightComponent(rightPanel);

        add(mainSplit, BorderLayout.CENTER);


        JLabel statusBar = new JLabel(" Ready");
        statusBar.setBorder(new BevelBorder(BevelBorder.LOWERED));
        add(statusBar, BorderLayout.SOUTH);
    }

    private JPanel createLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));


        JPanel controlPanel = createControlPanel();
        panel.add(controlPanel, BorderLayout.NORTH);


        JPanel sourcePanel = createSourceCodePanel();
        panel.add(sourcePanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        panel.setBorder(new TitledBorder("Execution Control"));


        stepIntoButton = new JButton("Step Into");
        stepIntoButton.setToolTipText("Execute next line, enter methods");
        stepIntoButton.addActionListener(e -> executeStepInto());


        stepOverButton = new JButton("Step Over");
        stepOverButton.setToolTipText("Execute next line, skip methods");
        stepOverButton.addActionListener(e -> executeStepOver());


        continueButton = new JButton("Continue");
        continueButton.setToolTipText("Continue execution until next breakpoint");
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


    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));


        JSplitPane topBottomSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);


        JSplitPane topSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        topSplit.setDividerLocation(200);

        JPanel callStackPanel = createCallStackPanel();
        JPanel inspectorPanel = createInspectorPanel();

        topSplit.setTopComponent(callStackPanel);
        topSplit.setBottomComponent(inspectorPanel);


        JPanel outputPanel = createOutputPanel();

        topBottomSplit.setTopComponent(topSplit);
        topBottomSplit.setBottomComponent(outputPanel);
        topBottomSplit.setDividerLocation(500);

        panel.add(topBottomSplit, BorderLayout.CENTER);

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

        JScrollPane scrollPane = new JScrollPane(callStackList);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createInspectorPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Inspector (Variables)"));

        inspectorRoot = new DefaultMutableTreeNode("Variables");
        inspectorTreeModel = new DefaultTreeModel(inspectorRoot);
        inspectorTree = new JTree(inspectorTreeModel);
        inspectorTree.setFont(new Font("Monospaced", Font.PLAIN, 11));

        JScrollPane scrollPane = new JScrollPane(inspectorTree);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createOutputPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Program Output"));

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        outputArea.setBackground(new Color(240, 240, 240));

        JScrollPane scrollPane = new JScrollPane(outputArea);
        scrollPane.setPreferredSize(new Dimension(450, 200));

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }



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



    public void updateDebuggerState(DebuggerState state, Location location, ThreadReference thread) {
        this.state = state;
        this.currentThread = thread;

        if (location != null) {
            try {
                currentSourceFile = location.sourceName();
                currentLine = location.lineNumber();

                // Mettre à jour tous les composants
                loadSourceCode(location);
                updateCallStack();
                updateInspector();

            } catch (AbsentInformationException e) {
                appendOutput("Warning: No debug information available\n");
            }
        }
    }

    private void loadSourceCode(Location location) {
        try {
            String className = location.declaringType().name();
            String fileName = location.sourceName();

            // Essayer de charger le fichier source
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

    private void highlightSourceCode(int line) {
        sourceCodePanel.setCurrentLine(line);
    }

    private String findSourceFile(String className, String fileName) {
        // Essayer de trouver le fichier source
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

                    for (int i = 0; i < length; i++) {
                        node.add(createVariableNode("[" + i + "]", array.getValue(i), depth + 1));
                    }

                }

                else {
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

    private void addVariableToTree(DefaultMutableTreeNode parent, Variable var) {
        String nodeText = var.getName() + " (" + var.getType() + ") = " + var.getValueAsString();
        DefaultMutableTreeNode varNode = new DefaultMutableTreeNode(nodeText);


        Value value = var.getValue();
        if (value instanceof ObjectReference && !(value instanceof StringReference)) {
            addObjectToTree(varNode, (ObjectReference) value);
        }

        parent.add(varNode);
    }

    private void addObjectToTree(DefaultMutableTreeNode parent, ObjectReference obj) {
        try {
            ReferenceType refType = obj.referenceType();
            for (Field field : refType.allFields()) {
                Value fieldValue = obj.getValue(field);
                String fieldText = field.name() + " (" + field.typeName() + ") = " +
                        (fieldValue != null ? fieldValue.toString() : "null");
                DefaultMutableTreeNode fieldNode = new DefaultMutableTreeNode(fieldText);


                if (fieldValue instanceof ObjectReference &&
                        !(fieldValue instanceof StringReference) &&
                        parent.getLevel() < 3) {
                    addObjectToTree(fieldNode, (ObjectReference) fieldValue);
                }

                parent.add(fieldNode);
            }
        } catch (Exception e) {

        }
    }

    private void expandTree(JTree tree, int levels) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        expandNode(tree, root, levels);
    }

    private void expandNode(JTree tree, DefaultMutableTreeNode node, int levelsRemaining) {
        if (levelsRemaining <= 0) return;

        tree.expandPath(new javax.swing.tree.TreePath(node.getPath()));

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
}