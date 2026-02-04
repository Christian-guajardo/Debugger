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

    // Lance la Machine Virtuelle (VM) et se connecte à la classe cible
    public VirtualMachine connectAndLaunchVM() throws IOException,
            IllegalConnectorArgumentsException, VMStartException {
        LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
        arguments.get("main").setValue(debugClass.getName());
        arguments.get("options").setValue("-cp " + System.getProperty("java.class.path"));
        return launchingConnector.launch(arguments);
    }

    // Point d'entrée : attache le débogueur à une classe et lance l'interface
    public void attachTo(Class debuggeeClass) {
        this.debugClass = debuggeeClass;

        SwingUtilities.invokeLater(() -> {
            gui = new DebuggerGUI();
            gui.setCallback(new DebuggerGUICallback());
            gui.setVisible(true);
            gui.appendOutput("=== Time-Traveling Debugger Started ===\n");
            gui.appendOutput("Starting target VM...\n");
            gui.enableControls(false);
        });

        try {
            vm = connectAndLaunchVM();
            captureProcessOutput();
            state = new DebuggerState(vm);

            SwingUtilities.invokeLater(() -> {
                gui.setDebuggerState(state);
            });

            state.getTimelineManager().setCallback(snapshot -> {
                SwingUtilities.invokeLater(() -> {
                    gui.appendOutput("\n=== Time-Travel to Snapshot #" +
                            snapshot.getSnapshotId() + " ===\n");
                    gui.appendOutput("Location: " + snapshot.getSourceFile() +
                            ":" + snapshot.getLineNumber() + "\n");
                    updateGUIFromSnapshot(snapshot);
                });
            });

            enableClassPrepareRequest(vm);

            SwingUtilities.invokeLater(() -> {
                gui.appendOutput("Target VM started\n");
                gui.appendOutput("Class: " + debugClass.getName() + "\n");
            });

            startDebugger();

        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                gui.appendOutput("ERROR: " + e.getMessage() + "\n");
            });
        }
    }

    // Configure la requête pour être notifié quand la classe cible est chargée
    public void enableClassPrepareRequest(VirtualMachine vm) {
        ClassPrepareRequest r = vm.eventRequestManager().createClassPrepareRequest();
        r.addClassFilter(debugClass.getName());
        r.enable();
    }

    // Démarre le processus de débogage : enregistrement d'abord, puis mode lecture
    public void startDebugger() throws InterruptedException, AbsentInformationException {
        SwingUtilities.invokeLater(() -> {
            gui.appendOutput("\n=== Phase 1: Recording ===\n");
        });

        recordTrace();

        SwingUtilities.invokeLater(() -> {
            gui.appendOutput("\n=== Phase 2: Replay Mode ===\n");
            gui.appendOutput("Snapshots captured: " +
                    state.getTimelineManager().getTimelineSize() + "\n");

            int varCount = state.getTimelineManager().getTrackedVariablesWithModificationsCount();
            int modifCount = state.getTimelineManager().getAllVariablesWithHistory().values()
                    .stream().mapToInt(List::size).sum();
            int methodCallCount = state.getTimelineManager().getAllMethodCalls().size();

            gui.appendOutput("\nStatistics:\n");
            gui.appendOutput("- Variables tracked: " + varCount + "\n");
            gui.appendOutput("- Variable modifications: " + modifCount + "\n");
            gui.appendOutput("- Method calls: " + methodCallCount + "\n");
            gui.appendOutput("\nReady to navigate.\n");

            gui.enableControls(true);
        });

        state.setExecutionStrategy(new ReplayExecutionStrategy());

        if (state.getTimelineManager().getTimelineSize() > 0) {
            state.getTimelineManager().travelToSnapshot(0);
        }
    }

    // Boucle principale d'enregistrement : capture les événements et crée des snapshots
    private void recordTrace() throws InterruptedException {
        int snapshotCount = 0;

        while (isVmRunning) {
            EventSet eventSet = vm.eventQueue().remove();

            for (Event event : eventSet) {
                if (event instanceof VMDisconnectEvent) {
                    SwingUtilities.invokeLater(() -> {
                        gui.appendOutput("VM Disconnected\n");
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
                                gui.appendOutput("Captured " + finalCount + " snapshots\n");
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

    // Configure le pas-à-pas automatique (Step Into) pour suivre toute l'exécution
    private void createAutoStepRequest(ThreadReference thread) {
        StepRequest stepRequest = vm.eventRequestManager().createStepRequest(
                thread, StepRequest.STEP_LINE, StepRequest.STEP_INTO);

        stepRequest.addClassExclusionFilter("java.*");
        stepRequest.addClassExclusionFilter("javax.*");
        stepRequest.addClassExclusionFilter("sun.*");
        stepRequest.addClassExclusionFilter("jdk.*");

        stepRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        stepRequest.enable();
    }

    // Enregistre un instantané de l'état actuel via le TimelineManager
    private ExecutionSnapshot recordSnapshot(Location location, ThreadReference thread) {
        try {
            return state.getTimelineManager().recordSnapshot(location, thread);
        } catch (Exception e) {
            return null;
        }
    }

    private void updateGUIFromSnapshot(ExecutionSnapshot snapshot) {
        if (snapshot == null) return;

        try {
            gui.updateFromSnapshot(snapshot);
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                gui.appendOutput("Error updating GUI: " + e.getMessage() + "\n");
            });
        }
    }

    // Capture la sortie standard et d'erreur du processus cible pour l'afficher
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
                    state.getTimelineManager().appendProgramOutput(finalLine + "\n");
                }
            } catch (IOException e) {
            }
        }).start();

        new Thread(() -> {
            try (InputStreamReader isr = new InputStreamReader(process.getErrorStream());
                 BufferedReader br = new BufferedReader(isr)) {

                String line;
                while ((line = br.readLine()) != null) {
                    String finalLine = line;
                    SwingUtilities.invokeLater(() -> {
                        gui.appendProgramOutput("[ERROR] " + finalLine + "\n");
                    });
                    state.getTimelineManager().appendProgramOutput("[ERROR] " + finalLine + "\n");
                }
            } catch (IOException e) {
            }
        }).start();
    }

    private class DebuggerGUICallback implements DebuggerGUI.DebuggerCallback {

        @Override
        public CommandResult executeCommand(Command command) {
            try {
                CommandResult result = command.execute(state);

                if (!result.isSuccess()) {
                    SwingUtilities.invokeLater(() -> {
                        gui.appendOutput("ERROR: " + result.getMessage() + "\n");
                    });
                    return result;
                }

                ExecutionSnapshot current = state.getTimelineManager().getCurrentSnapshot();
                if (current != null) {
                    SwingUtilities.invokeLater(() -> {
                        updateGUIFromSnapshot(current);
                    });
                }
                return result;

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    gui.appendOutput("Error: " + e.getMessage() + "\n");
                });
                return CommandResult.error(e.getMessage());
            }
        }

        @Override
        public void placeBreakpoint(String file, int line) {
            try {
                BreakCommand cmd = new BreakCommand(file, line);
                CommandResult result = cmd.execute(state);

                if (result.isSuccess()) {
                    SwingUtilities.invokeLater(() -> {
                        gui.appendOutput("Breakpoint set at " + file + ":" + line + "\n");
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        gui.appendOutput("Failed to set breakpoint\n");
                    });
                }

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    gui.appendOutput("Error setting breakpoint\n");
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
                }
            }

            SwingUtilities.invokeLater(() -> {
                gui.appendOutput("\n=== Debugger Stopped ===\n");
            });
        }
    }
}