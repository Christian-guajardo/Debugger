package dbg.sourceBase;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.*;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.StepRequest;
import timetravel.ExecutionSnapshot;
import timetravel.TimelineManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

public class TimeTravelDebuggerGUI extends JFrame {

    // --- LOGIQUE BACKEND ---
    private TimelineManager timelineManager;
    private VirtualMachine vm;
    private boolean isReplayMode = false;
    private String currentFileName = "";

    // --- COMPOSANTS UI ---
    private JTextPane sourceCodeArea;       // [POINT PDF] Source Code
    private JTree inspectorTree;            // [POINT PDF] Inspector (Arbre)
    private DefaultTreeModel treeModel;
    private JList<String> callStackList;    // [POINT PDF] Call Stack
    private DefaultListModel<String> stackModel;
    private JTextArea outputConsole;        // [POINT PDF] Output
    private JSlider timeSlider;             // Navigation Temporelle

    // Boutons de commande [POINT PDF]
    private JButton btnStepOver, btnStepInto, btnContinue, btnStop;

    // Surlignage [POINT PDF]
    private Object currentHighlightTag = null;
    private Highlighter.HighlightPainter currentLinePainter =
            new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 235, 59)); // Jaune

    public TimeTravelDebuggerGUI() {
        super("Debugger Graphique - TP 2025");
        this.timelineManager = new TimelineManager();

        setupUI();

        setSize(1200, 800);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    private void setupUI() {
        setLayout(new BorderLayout());

        // --- 1. BARRE D'OUTILS (COMMANDES) ---
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(new EmptyBorder(5, 5, 5, 5));

        btnStepInto = new JButton("STEP-IN");
        btnStepOver = new JButton("STEP-OVER");
        btnContinue = new JButton("CONTINUE");
        btnStop = new JButton("STOP");

        // Style simple
        btnStop.setForeground(Color.RED);

        toolbar.add(btnStepInto);
        toolbar.add(btnStepOver);
        toolbar.add(btnContinue);
        toolbar.addSeparator();
        toolbar.add(btnStop);

        toolbar.addSeparator();
        toolbar.add(new JLabel(" Timeline: "));
        timeSlider = new JSlider(0, 0, 0);
        timeSlider.setEnabled(false);
        toolbar.add(timeSlider);

        add(toolbar, BorderLayout.NORTH);

        // --- 2. CENTRE : CODE SOURCE & DONNÉES ---
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setResizeWeight(0.6); // 60% pour le code

        // --- GAUCHE : CODE SOURCE ---
        sourceCodeArea = new JTextPane();
        sourceCodeArea.setEditable(false);
        sourceCodeArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        sourceCodeArea.setText("// En attente du chargement de la classe...");

        // [POINT PDF] Gestion des Breakpoints (Clic dans la marge/texte)
        sourceCodeArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Double clic ou clic droit pour toggle breakpoint
                if (e.getClickCount() == 2 || SwingUtilities.isRightMouseButton(e)) {
                    toggleBreakpoint(e.getPoint());
                }
            }
        });

        JScrollPane sourceScroll = new JScrollPane(sourceCodeArea);
        sourceScroll.setBorder(BorderFactory.createTitledBorder("SOURCE CODE"));
        mainSplit.setLeftComponent(sourceScroll);

        // --- DROITE : STACK & INSPECTOR ---
        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

        // Call Stack
        stackModel = new DefaultListModel<>();
        callStackList = new JList<>(stackModel);
        JScrollPane stackScroll = new JScrollPane(callStackList);
        stackScroll.setBorder(BorderFactory.createTitledBorder("CALL STACK"));

        // Inspector (Arbre)
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Variables");
        treeModel = new DefaultTreeModel(root);
        inspectorTree = new JTree(treeModel);
        JScrollPane inspectorScroll = new JScrollPane(inspectorTree);
        inspectorScroll.setBorder(BorderFactory.createTitledBorder("INSPECTOR"));

        rightSplit.setTopComponent(stackScroll);
        rightSplit.setBottomComponent(inspectorScroll);
        rightSplit.setResizeWeight(0.5);
        mainSplit.setRightComponent(rightSplit);

        // --- 3. BAS : CONSOLE DE SORTIE ---
        outputConsole = new JTextArea();
        outputConsole.setEditable(false);
        outputConsole.setRows(6);
        outputConsole.setFont(new Font("Monospaced", Font.PLAIN, 12));
        outputConsole.setBackground(new Color(30, 30, 30));
        outputConsole.setForeground(Color.LIGHT_GRAY);

        JScrollPane consoleScroll = new JScrollPane(outputConsole);
        consoleScroll.setBorder(BorderFactory.createTitledBorder("OUTPUT"));

        // Assemblage vertical final
        JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        verticalSplit.setTopComponent(mainSplit);
        verticalSplit.setBottomComponent(consoleScroll);
        verticalSplit.setResizeWeight(0.8);

        add(verticalSplit, BorderLayout.CENTER);

        // --- LISTENERS ---
        setupListeners();
    }

    private void setupListeners() {
        // Slider Time Travel
        timeSlider.addChangeListener(e -> {
            if (!timeSlider.getValueIsAdjusting() && isReplayMode) {
                travelTo(timeSlider.getValue());
            }
        });

        // [POINT PDF] Clic sur Stack Frame -> Mise à jour vue
        callStackList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && isReplayMode) {
                // Dans une version avancée, on chargerait les variables de cette frame spécifique
                log("[UI] Focus sur la frame : " + callStackList.getSelectedValue());
            }
        });

        // Boutons (Simulation en mode Replay)
        btnStepInto.addActionListener(e -> {
            if (isReplayMode && timeSlider.getValue() < timeSlider.getMaximum())
                timeSlider.setValue(timeSlider.getValue() + 1);
        });

        btnStepOver.addActionListener(e -> {
            // Logique simplifiée : avancer de 1
            if (isReplayMode && timeSlider.getValue() < timeSlider.getMaximum())
                timeSlider.setValue(timeSlider.getValue() + 1);
        });

        btnContinue.addActionListener(e -> {
            if (isReplayMode) timeSlider.setValue(timeSlider.getMaximum());
        });

        btnStop.addActionListener(e -> System.exit(0));
    }

    // --- LOGIQUE DE LECTURE DE FICHIER ---

    private String readSourceFile(String fileName) {
        // Cherche le fichier dans plusieurs dossiers possibles
        String[] searchPaths = {
                ".",
                "src",
                "src/dbg/sourceBase",
                "dbg/sourceBase"
        };

        for (String path : searchPaths) {
            File f = new File(path, fileName);
            if (f.exists() && f.isFile()) {
                try {
                    return Files.readString(f.toPath());
                } catch (IOException e) {
                    return "// Erreur lecture : " + e.getMessage();
                }
            }
        }
        return "// Fichier introuvable : " + fileName + "\n// Placez le fichier .java à la racine du projet ou dans src.";
    }

    // --- LOGIQUE BREAKPOINT ---

    private void toggleBreakpoint(Point p) {
        int pos = sourceCodeArea.viewToModel2D(p);
        try {
            int line = getLineOfOffset(sourceCodeArea, pos);
            log("[BREAKPOINT] Toggle demandé à la ligne " + line);
            JOptionPane.showMessageDialog(this, "Breakpoint ajouté ligne " + line + " (Simulation GUI)");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private int getLineOfOffset(JTextPane comp, int offset) {
        return comp.getDocument().getDefaultRootElement().getElementIndex(offset) + 1;
    }

    // --- MOTEUR JDI (EXECUTION) ---

    public void launch(Class<?> debugClass) {
        setVisible(true);
        log("Démarrage du debugger sur : " + debugClass.getName());

        SwingWorker<Void, ExecutionSnapshot> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                // 1. Connexion
                vm = connect(debugClass);

                // 2. Préparation
                ClassPrepareRequest cpr = vm.eventRequestManager().createClassPrepareRequest();
                cpr.addClassFilter(debugClass.getName());
                cpr.enable();

                EventQueue queue = vm.eventQueue();
                boolean running = true;

                while (running) {
                    EventSet eventSet = queue.remove();
                    for (Event event : eventSet) {
                        if (event instanceof ClassPrepareEvent) {
                            // Au chargement de la classe, on active le traçage ligne par ligne
                            createStepRequest(vm, ((ClassPrepareEvent) event).thread());
                        }
                        else if (event instanceof StepEvent) {
                            StepEvent step = (StepEvent) event;
                            // Capture l'état
                            ExecutionSnapshot snap = timelineManager.recordSnapshot(step.location(), step.thread());
                            publish(snap); // Envoie à l'UI
                        }
                        else if (event instanceof VMDisconnectEvent) {
                            running = false;
                        }
                    }
                    eventSet.resume();
                }
                return null;
            }

            @Override
            protected void process(java.util.List<ExecutionSnapshot> chunks) {
                // Mise à jour live (optionnelle, ici on prend le dernier pour montrer l'avancée)
                if (!chunks.isEmpty()) {
                    ExecutionSnapshot last = chunks.get(chunks.size() - 1);
                    log("Enregistrement Snapshot #" + last.getSnapshotId() + " (Ligne " + last.getLineNumber() + ")");
                }
            }

            @Override
            protected void done() {
                log("=== Exécution terminée. Mode REPLAY activé ===");
                isReplayMode = true;
                int max = timelineManager.getTimelineSize() - 1;
                if (max >= 0) {
                    timeSlider.setEnabled(true);
                    timeSlider.setMaximum(max);
                    timeSlider.setValue(0); // Revient au début
                    travelTo(0);
                }
            }
        };

        worker.execute();
    }

    // --- MISE A JOUR VUE (LE COEUR DU GUI) ---

    private void travelTo(int index) {
        if (timelineManager.travelToSnapshot(index)) {
            updateView(timelineManager.getCurrentSnapshot());
        }
    }

    private void updateView(ExecutionSnapshot snap) {
        if (snap == null) return;

        // 1. Code Source
        String fName = snap.getSourceFile();
        if (!fName.equals(currentFileName)) {
            sourceCodeArea.setText(readSourceFile(fName));
            currentFileName = fName;
            sourceCodeArea.setCaretPosition(0);
        }
        highlightLine(snap.getLineNumber());

        // 2. Inspector (Variables)
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        root.removeAllChildren();
        root.setUserObject("this (" + snap.getMethodName() + ")");

        for (Map.Entry<String, String> entry : snap.getVariables().entrySet()) {
            // Création des feuilles pour les variables
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(entry.getKey() + " = " + entry.getValue());
            root.add(node);
        }
        treeModel.reload();
        for (int i = 0; i < inspectorTree.getRowCount(); i++) inspectorTree.expandRow(i);

        // 3. Call Stack
        stackModel.clear();
        for (String frame : snap.getCallStack()) {
            stackModel.addElement(frame);
        }
        if (!stackModel.isEmpty()) callStackList.setSelectedIndex(0);

        // 4. Slider Sync
        if (!timeSlider.getValueIsAdjusting()) {
            timeSlider.setValue(snap.getSnapshotId());
        }
    }

    // [POINT PDF] Surlignage de la ligne courante
    private void highlightLine(int lineNumber) {
        try {
            Highlighter h = sourceCodeArea.getHighlighter();
            h.removeAllHighlights();

            javax.swing.text.Element root = sourceCodeArea.getDocument().getDefaultRootElement();
            if (lineNumber > 0 && lineNumber <= root.getElementCount()) {
                javax.swing.text.Element lineElement = root.getElement(lineNumber - 1);
                int start = lineElement.getStartOffset();
                int end = lineElement.getEndOffset();

                h.addHighlight(start, end - 1, currentLinePainter);

                // Centre la vue sur la ligne
                sourceCodeArea.setCaretPosition(start);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void log(String msg) {
        outputConsole.append(msg + "\n");
        outputConsole.setCaretPosition(outputConsole.getDocument().getLength());
    }

    // --- HELPERS JDI ---

    private VirtualMachine connect(Class<?> debugClass) throws Exception {
        LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
        arguments.get("main").setValue(debugClass.getName());
        arguments.get("options").setValue("-cp " + System.getProperty("java.class.path"));
        return launchingConnector.launch(arguments);
    }

    private void createStepRequest(VirtualMachine vm, ThreadReference thread) {
        StepRequest stepRequest = vm.eventRequestManager().createStepRequest(thread, StepRequest.STEP_LINE, StepRequest.STEP_INTO);
        // Exclure les classes Java pour ne pas debuger String ou System.out
        stepRequest.addClassExclusionFilter("java.*");
        stepRequest.addClassExclusionFilter("javax.*");
        stepRequest.addClassExclusionFilter("sun.*");
        stepRequest.enable();
    }
}