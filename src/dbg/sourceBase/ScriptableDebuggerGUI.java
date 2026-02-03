package dbg.sourceBase;

import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import commands.*;
import models.*;
import gui.DebuggerGUI;
import timetravel.*;

import javax.swing.*;
import java.io.*;
import java.util.*;

/**
 * Debugger graphique basé sur les snapshots avec support des Time-Traveling Queries
 * Version corrigée pour gérer la disconnection de la VM en mode replay
 */
public class ScriptableDebuggerGUI {
    private Class debugClass;
    private VirtualMachine vm;
    private DebuggerState state;
    private CommandInterpreter interpreter;
    private DebuggerGUI gui;
    private boolean isVmRunning = true;

    public ScriptableDebuggerGUI() {
        this.interpreter = new CommandInterpreter();
    }

    public VirtualMachine connectAndLaunchVM() throws IOException,
            IllegalConnectorArgumentsException, VMStartException {
        LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager()
                .defaultConnector();
        Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
        arguments.get("main").setValue(debugClass.getName());
        arguments.get("options").setValue("-cp " + System.getProperty("java.class.path"));
        return launchingConnector.launch(arguments);
    }

    public void attachTo(Class debuggeeClass) {
        this.debugClass = debuggeeClass;

        // Créer l'interface graphique en premier
        SwingUtilities.invokeLater(() -> {
            gui = new DebuggerGUI();
            gui.setCallback(new DebuggerGUICallback());
            gui.setVisible(true);
            gui.appendOutput("=== Time-Traveling Debugger Started ===\n");
            gui.appendOutput("Starting target VM...\n");
            gui.enableControls(false); // Désactivé pendant la capture
        });

        try {
            vm = connectAndLaunchVM();
            captureProcessOutput();
            state = new DebuggerState(vm);

            // Configurer le callback de time-travel
            state.getTimelineManager().setCallback(snapshot -> {
                SwingUtilities.invokeLater(() -> {
                    gui.appendOutput("\n=== Time-Travel to Snapshot #" +
                            snapshot.getSnapshotId() + " ===\n");
                    gui.appendOutput("Location: " + snapshot.getSourceFile() +
                            ":" + snapshot.getLineNumber() + "\n");
                    gui.appendOutput("Method: " + snapshot.getMethodName() + "\n");

                    // Mettre à jour l'UI avec le snapshot (sans utiliser le thread)
                    updateGUIFromSnapshot(snapshot);
                });
            });

            enableClassPrepareRequest(vm);

            SwingUtilities.invokeLater(() -> {
                gui.appendOutput("Target VM started successfully\n");
                gui.appendOutput("Class: " + debugClass.getName() + "\n");
            });

            startDebugger();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            SwingUtilities.invokeLater(() -> {
                gui.appendOutput("ERROR: " + e.getMessage() + "\n");
            });
        }
    }

    public void enableClassPrepareRequest(VirtualMachine vm) {
        ClassPrepareRequest r = vm.eventRequestManager().createClassPrepareRequest();
        r.addClassFilter(debugClass.getName());
        r.enable();
    }

    public void startDebugger() throws InterruptedException, AbsentInformationException {
        // --- PHASE 1 : ENREGISTREMENT (automatique) ---
        SwingUtilities.invokeLater(() -> {
            gui.appendOutput("\n=== Phase 1: Recording Execution ===\n");
            gui.appendOutput("Capturing all execution steps...\n");
        });

        recordTrace();

        // --- PHASE 2 : MODE REPLAY ---
        SwingUtilities.invokeLater(() -> {
            gui.appendOutput("\n=== Phase 2: Replay Mode ===\n");
            gui.appendOutput("Program execution completed.\n");
            gui.appendOutput("Total snapshots captured: " +
                    state.getTimelineManager().getTimelineSize() + "\n");
            gui.appendOutput("\nYou can now navigate through the execution.\n");

            // Activer les contrôles et passer en mode replay
            gui.enableControls(true);
        });

        // Configurer la stratégie de replay
        state.setExecutionStrategy(new ReplayExecutionStrategy());

        // Se positionner au début de la timeline
        if (state.getTimelineManager().getTimelineSize() > 0) {
            state.getTimelineManager().travelToSnapshot(0);
        }
    }

    private void recordTrace() throws InterruptedException {
        int snapshotCount = 0;

        while (isVmRunning) {
            EventSet eventSet = vm.eventQueue().remove();

            for (Event event : eventSet) {
                if (event instanceof VMDisconnectEvent) {
                    SwingUtilities.invokeLater(() -> {
                        gui.appendOutput("VM Disconnected - Recording complete.\n");
                    });
                    isVmRunning = false;
                    break;
                }

                if (event instanceof ClassPrepareEvent) {
                    ClassPrepareEvent evt = (ClassPrepareEvent) event;
                    SwingUtilities.invokeLater(() -> {
                        gui.appendOutput("Class loaded: " + evt.referenceType().name() + "\n");
                    });
                    createAutoStepRequest(evt.thread());
                }

                if (event instanceof StepEvent || event instanceof BreakpointEvent) {
                    Location loc = ((LocatableEvent) event).location();
                    ThreadReference thread = ((LocatableEvent) event).thread();

                    ExecutionSnapshot snapshot = recordSnapshot(loc, thread);

                    if (snapshot != null) {
                        snapshotCount++;
                        if (snapshotCount % 10 == 0) {
                            int finalCount = snapshotCount;
                            SwingUtilities.invokeLater(() -> {
                                gui.appendOutput("Captured " + finalCount + " snapshots...\n");
                            });
                        }
                    }
                }
            }

            if (isVmRunning) {
                vm.resume();
            }
        }
    }

    private void createAutoStepRequest(ThreadReference thread) {
        StepRequest stepRequest = vm.eventRequestManager().createStepRequest(
                thread, StepRequest.STEP_LINE, StepRequest.STEP_INTO);

        // Exclure les packages Java système
        stepRequest.addClassExclusionFilter("java.*");
        stepRequest.addClassExclusionFilter("javax.*");
        stepRequest.addClassExclusionFilter("sun.*");
        stepRequest.addClassExclusionFilter("jdk.*");

        stepRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        stepRequest.enable();
    }

    private ExecutionSnapshot recordSnapshot(Location location, ThreadReference thread) {
        try {
            return state.getTimelineManager().recordSnapshot(location, thread);
        } catch (Exception e) {
            System.err.println("Error recording snapshot: " + e.getMessage());
            return null;
        }
    }

    /**
     * Mise à jour de l'UI à partir d'un snapshot (mode replay)
     * N'utilise PAS le ThreadReference car la VM est déconnectée
     */
    private void updateGUIFromSnapshot(ExecutionSnapshot snapshot) {
        if (snapshot == null) return;

        try {
            // En mode replay, on reconstruit le contexte à partir du snapshot
            // sans utiliser le thread JDI (qui n'est plus disponible)
            gui.updateFromSnapshot(snapshot);

        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                gui.appendOutput("Error updating GUI: " + e.getMessage() + "\n");
            });
        }
    }

    private void captureProcessOutput() {
        Process process = vm.process();

        new Thread(() -> {
            try (InputStreamReader isr = new InputStreamReader(process.getInputStream());
                 BufferedReader br = new BufferedReader(isr)) {

                String line;
                while ((line = br.readLine()) != null) {
                    String finalLine = line;
                    SwingUtilities.invokeLater(() -> {
                        gui.appendProgramOutput(finalLine + "\n");
                    });
                }
            } catch (IOException e) {
                // Stream fermé - normal à la fin
            }
        }).start();

        // Capturer aussi stderr
        new Thread(() -> {
            try (InputStreamReader isr = new InputStreamReader(process.getErrorStream());
                 BufferedReader br = new BufferedReader(isr)) {

                String line;
                while ((line = br.readLine()) != null) {
                    String finalLine = line;
                    SwingUtilities.invokeLater(() -> {
                        gui.appendProgramOutput("[ERROR] " + finalLine + "\n");
                    });
                }
            } catch (IOException e) {
                // Stream fermé - normal
            }
        }).start();
    }

    /**
     * Classe interne pour gérer les callbacks de l'interface graphique
     */
    private class DebuggerGUICallback implements DebuggerGUI.DebuggerCallback {

        @Override
        public void executeCommand(Command command) {
            try {
                CommandResult result = command.execute(state);

                if (!result.isSuccess()) {
                    SwingUtilities.invokeLater(() -> {
                        gui.appendOutput("ERROR: " + result.getMessage() + "\n");
                    });
                    return;
                }

                // Mettre à jour l'UI après l'exécution de la commande
                ExecutionSnapshot current = state.getTimelineManager().getCurrentSnapshot();
                if (current != null) {
                    SwingUtilities.invokeLater(() -> {
                        updateGUIFromSnapshot(current);
                    });
                }

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    gui.appendOutput("Error executing command: " + e.getMessage() + "\n");
                    e.printStackTrace();
                });
            }
        }

        @Override
        public void placeBreakpoint(String file, int line) {
            try {
                // En mode replay, les breakpoints sont simulés
                BreakCommand cmd = new BreakCommand(file, line);
                CommandResult result = cmd.execute(state);

                if (result.isSuccess()) {
                    SwingUtilities.invokeLater(() -> {
                        gui.appendOutput("Replay breakpoint set at " + file + ":" + line + "\n");
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        gui.appendOutput("Failed to set breakpoint: " + result.getMessage() + "\n");
                    });
                }

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    gui.appendOutput("Error setting breakpoint: " + e.getMessage() + "\n");
                });
            }
        }

        @Override
        public void stop() {
            isVmRunning = false;

            if (vm != null) {
                try {
                    vm.exit(0);
                } catch (Exception e) {
                    // VM déjà arrêtée
                }
            }

            SwingUtilities.invokeLater(() -> {
                gui.appendOutput("\n=== Debugger Stopped ===\n");
            });
        }
    }
}