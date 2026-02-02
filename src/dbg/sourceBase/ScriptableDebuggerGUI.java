package dbg.sourceBase;

import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import commands.*;
import models.*;
import gui.DebuggerGUI;

import javax.swing.*;
import java.io.*;
import java.util.*;

public class ScriptableDebuggerGUI implements DebuggerGUI.DebuggerCallback {
    private Class debugClass;
    private VirtualMachine vm;
    private DebuggerState state;
    private CommandInterpreter interpreter;
    private DebuggerGUI gui;
    private boolean shouldContinue = false;
    private volatile boolean isRunning = true;

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

        // Créer l'interface graphique
        SwingUtilities.invokeLater(() -> {
            gui = new DebuggerGUI();
            gui.setCallback(this);
            gui.setVisible(true);
            gui.appendOutput("=== Graphical Debugger Started ===\n");
            gui.appendOutput("Starting target VM...\n");
        });

        try {
            vm = connectAndLaunchVM();
            captureProcessOutput();
            state = new DebuggerState(vm);
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

    private void handleBreakpoint(BreakpointEvent event) throws IncompatibleThreadStateException, AbsentInformationException {
        Location loc = event.location();

        SwingUtilities.invokeLater(() -> {
            gui.appendOutput("\n=== Breakpoint hit ===\n");
            try {
                gui.appendOutput("Location: " + loc.sourceName() + ":" + loc.lineNumber() + "\n");
                gui.appendOutput("Method: " + loc.method().name() + "\n");
            } catch (AbsentInformationException e) {
                gui.appendOutput("Location information not available\n");
            }
        });

        // Vérifier si c'est un breakpoint spécial
        String key = loc.sourceName() + ":" + loc.lineNumber();
        Breakpoint bp = state.getBreakpoints().get(key);

        if (bp != null) {
            bp.incrementHitCount();

            if (!bp.shouldStop()) {
                SwingUtilities.invokeLater(() -> {
                    gui.appendOutput("Breakpoint condition not met, continuing...\n");
                });
                return;
            }

            if (bp.getType() == Breakpoint.BreakpointType.ONCE) {
                bp.getRequest().disable();
                state.getBreakpoints().remove(key);
                SwingUtilities.invokeLater(() -> {
                    gui.appendOutput("One-time breakpoint removed\n");
                });
            }
        }

        // Mettre à jour l'interface
        updateGUI(event.location(), event.thread());

        // Attendre la prochaine commande
        waitForCommand();
    }

    private void handleMethodEntry(MethodEntryEvent event) throws IncompatibleThreadStateException {
        Method method = event.method();

        for (String methodName : state.getMethodBreakpoints().keySet()) {
            if (method.name().equals(methodName)) {
                SwingUtilities.invokeLater(() -> {
                    gui.appendOutput("\n=== Method entry: " + method.name() + " ===\n");
                    gui.appendOutput("Class: " + method.declaringType().name() + "\n");
                });

                updateGUI(event.location(), event.thread());
                waitForCommand();
                return;
            }
        }
    }

    private void setInitialBreakpoint() {
        for (ReferenceType type : vm.allClasses()) {
            if (type.name().equals(debugClass.getName())) {
                try {
                    String sourceFile = type.sourceName();
                    List<Location> locations = type.locationsOfLine(14);

                    if (!locations.isEmpty()) {
                        Location loc = locations.get(0);
                        BreakpointRequest req = vm.eventRequestManager()
                                .createBreakpointRequest(loc);
                        req.enable();

                        SwingUtilities.invokeLater(() -> {
                            gui.appendOutput("Initial breakpoint set at " + sourceFile + ":14\n");
                        });
                        return;
                    }
                } catch (AbsentInformationException e) {
                    SwingUtilities.invokeLater(() -> {
                        gui.appendOutput("Warning: No debug info available\n");
                    });
                }
            }
        }
    }

    public void startDebugger() throws InterruptedException, AbsentInformationException {
        while (isRunning) {
            EventSet eventSet = vm.eventQueue().remove();

            for (Event event : eventSet) {
                try {
                    if (event instanceof VMDisconnectEvent) {
                        SwingUtilities.invokeLater(() -> {
                            gui.appendOutput("\n=== Program terminated ===\n");
                            gui.enableControls(false);
                        });
                        // printProcessOutput();
                        return;
                    }

                    if (event instanceof ClassPrepareEvent) {
                        SwingUtilities.invokeLater(() -> {
                            gui.appendOutput("Class loaded: " + debugClass.getName() + "\n");
                        });
                        setInitialBreakpoint();
                    }

                    if (event instanceof BreakpointEvent) {
                        handleBreakpoint((BreakpointEvent) event);
                        continue;
                    }

                    if (event instanceof StepEvent) {
                        StepEvent se = (StepEvent) event;
                        vm.eventRequestManager().deleteEventRequest(event.request());

                        /*SwingUtilities.invokeLater(() -> {
                            try {
                                gui.appendOutput("\nStepped to: " + se.location().sourceName()
                                        + ":" + se.location().lineNumber() + "\n");
                            } catch (AbsentInformationException e) {
                                gui.appendOutput("\nStepped to unknown location\n");
                            }
                        });*/

                        updateGUI(se.location(), se.thread());
                        waitForCommand();
                        continue;
                    }

                    if (event instanceof MethodEntryEvent) {
                        handleMethodEntry((MethodEntryEvent) event);
                        continue;
                    }

                } catch (Exception e) {
                    System.err.println("Error handling event: " + e.getMessage());
                    e.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        gui.appendOutput("ERROR: " + e.getMessage() + "\n");
                    });
                }
            }

            vm.resume();
        }
    }

    private void updateGUI(Location location, ThreadReference thread) {
        try {
            state.updateContext(thread);

            SwingUtilities.invokeLater(() -> {
                gui.updateDebuggerState(state, location, thread);
                gui.enableControls(true);
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private synchronized void waitForCommand() {
        shouldContinue = false;

        while (!shouldContinue && isRunning) {
            try {
                wait(100);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private synchronized void resumeExecution() {
        shouldContinue = true;
        notifyAll();
    }

    public void printProcessOutput() {
        try {
            InputStreamReader reader = new InputStreamReader(vm.process().getInputStream());
            StringBuilder output = new StringBuilder();
            char[] buffer = new char[1024];
            int read;

            while ((read = reader.read(buffer)) != -1) {
                output.append(buffer, 0, read);
            }

            String finalOutput = output.toString();
            SwingUtilities.invokeLater(() -> {
                gui.appendOutput(finalOutput);
            });

        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                gui.appendOutput("Error reading VM output: " + e.getMessage() + "\n");
            });
        }
    }


    @Override
    public void executeCommand(Command command) {
        try {
            state.updateContext(state.getContext().getThread());
            CommandResult result = command.execute(state);

            if (!result.isSuccess()) {
                SwingUtilities.invokeLater(() -> {
                    gui.appendOutput("ERROR: " + result.getMessage() + "\n");
                });
                return;
            }
            /*
            if (!result.getMessage().isEmpty()) {
                SwingUtilities.invokeLater(() -> {
                    gui.appendOutput(result.getMessage() + "\n");
                });
            }*/

            // Reprendre l'exécution
            resumeExecution();

        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                gui.appendOutput("Error executing command: " + e.getMessage() + "\n");
            });
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
                    gui.appendOutput("Failed to set breakpoint: " + result.getMessage() + "\n");
                });
            }

        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                gui.appendOutput("Error setting breakpoint: " + e.getMessage() + "\n");
            });
        }
    }
    private void captureProcessOutput() {
        Process process = vm.process();


        new Thread(() -> {
            try (InputStreamReader isr = new InputStreamReader(process.getInputStream());
                 BufferedReader br = new BufferedReader(isr)) {

                char[] buffer = new char[1024];
                int read;

                while ((read = br.read(buffer)) != -1) {
                    String output = new String(buffer, 0, read);
                    SwingUtilities.invokeLater(() -> {
                        gui.appendOutput(output);
                    });
                }
            } catch (IOException e) {

            }
        }).start();

    }
    @Override
    public void stop() {
        isRunning = false;
        resumeExecution();

        if (vm != null) {
            try {
                vm.exit(0);
            } catch (Exception e) {
                // Ignorer
            }
        }
    }
}