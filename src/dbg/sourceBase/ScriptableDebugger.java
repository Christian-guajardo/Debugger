package dbg.sourceBase;

import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import commands.*;
import models.*;
import timetravel.*;
import java.io.*;
import java.util.*;

public class ScriptableDebugger {
    private Class debugClass;
    private VirtualMachine vm;
    private DebuggerState state;
    private CommandInterpreter interpreter;
    private boolean isVmRunning = true;

    public ScriptableDebugger() {
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
        try {
            vm = connectAndLaunchVM();
            state = new DebuggerState(vm);
            isVmRunning = true;


            state.getTimelineManager().setCallback(snapshot -> {
                System.out.println("\n=== Time-Travel Restoration ===");
                System.out.println("Restoring to: " + snapshot);


                if (isVmRunning) {
                    try {
                        state.updateContext(snapshot.getThread());
                    } catch (Exception e) {

                    }
                } else {

                }
            });

            enableClassPrepareRequest(vm);
            startDebugger();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void enableClassPrepareRequest(VirtualMachine vm) {
        ClassPrepareRequest r = vm.eventRequestManager().createClassPrepareRequest();
        r.addClassFilter(debugClass.getName());
        r.enable();
    }

    public void startDebugger() throws InterruptedException, AbsentInformationException {
        // --- PHASE 1 : ENREGISTREMENT ---
        System.out.println("=== Phase 1: Capture de l'exécution (Automatique) ===");
        recordTrace();

        // --- PHASE 2 : REPLAY ---
        System.out.println("\n=== Phase 2: Mode Replay (Simulation) ===");
        System.out.println("Le programme est terminé. Vous naviguez dans " +
                state.getTimelineManager().getTimelineSize() + " snapshots.");

        state.setExecutionStrategy(new ReplayExecutionStrategy());


        if (state.getTimelineManager().getTimelineSize() > 0) {
            state.getTimelineManager().travelToSnapshot(0);
        }

        inputLoop();
    }

    private void recordTrace() throws InterruptedException {
        while (isVmRunning) {
            EventSet eventSet = vm.eventQueue().remove();
            for (Event event : eventSet) {

                if (event instanceof VMDisconnectEvent) {
                    System.out.println("Fin de l'exécution réelle (VM Disconnected).");

                    isVmRunning = false;
                    printProcessOutput();
                    break;
                }

                if (event instanceof ClassPrepareEvent) {
                    ClassPrepareEvent evt = (ClassPrepareEvent) event;
                    System.out.println("Classe chargée : " + evt.referenceType().name());
                    createAutoStepRequest(evt.thread());
                }

                if (event instanceof StepEvent || event instanceof BreakpointEvent) {
                    Location loc = ((LocatableEvent) event).location();
                    ThreadReference thread = ((LocatableEvent) event).thread();
                    recordSnapshot(loc, thread);
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
        stepRequest.addClassExclusionFilter("java.*");
        stepRequest.addClassExclusionFilter("javax.*");
        stepRequest.addClassExclusionFilter("sun.*");
        stepRequest.addClassExclusionFilter("jdk.*");
        stepRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        stepRequest.enable();
    }

    private void recordSnapshot(Location location, ThreadReference thread) {
        try {
            state.getTimelineManager().recordSnapshot(location, thread);
        } catch (Exception e) {
            System.err.println("Error recording snapshot: " + e.getMessage());
        }
    }

    private void inputLoop() {
        Scanner sc = new Scanner(System.in);
        System.out.println("\nCommandes : step, step-over, continue, print <var>, quit");

        while (true) {
            ExecutionSnapshot current = state.getTimelineManager().getCurrentSnapshot();
            String locationInfo = (current != null) ?
                    "[" + current.getSnapshotId() + "] " + current.getMethodName() + ":" + current.getLineNumber()
                    : "[?]";

            System.out.print("\nreplay " + locationInfo + "> ");
            String input = sc.nextLine();

            if (input.equals("quit")) break;
            if (input.trim().isEmpty()) continue;

            try {
                Command command = interpreter.parse(input);
                CommandResult result = command.execute(state);
                displayResult(result);
            } catch (Exception e) {
                System.err.println("Erreur : " + e.getMessage());
            }
        }
    }

    private void displayResult(CommandResult result) {
        if (!result.isSuccess()) {
            System.err.println(result.getMessage());
            return;
        }
        if (!result.getMessage().isEmpty()) {
            System.out.println(result.getMessage());
        }
        if (result.getData() != null) {
            System.out.println(result.getData());
        }
    }

    public void printProcessOutput() {
        try {
            InputStreamReader reader = new InputStreamReader(vm.process().getInputStream());
            OutputStreamWriter writer = new OutputStreamWriter(System.out);
            reader.transferTo(writer);
            writer.flush();
        } catch (Exception e) { }
    }


}