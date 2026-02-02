package dbg.sourceBase;

import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import commands.*;
import models.*;
import java.io.*;
import java.util.*;

public class ScriptableDebugger {
    private Class debugClass;
    private VirtualMachine vm;
    private DebuggerState state;
    private CommandInterpreter interpreter;

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

    private void handleUserCommand(ThreadReference thread) {

            System.out.print("\ndbg> ");
            Scanner sc = new Scanner(System.in);
            String input = sc.nextLine();

            if (input.trim().isEmpty()) {
                return;
            }

            try {
                // Mettre à jour le contexte
                state.updateContext(thread);

                // exécuter la commande
                Command command = interpreter.parse(input);
                CommandResult result = command.execute(state);

                // Afficher le résultat
                displayResult(result);

            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }

    }

    private void displayResult(CommandResult result) {
        if (!result.isSuccess()) {
            System.err.println("ERROR: " + result.getMessage());
            return;
        }

        if (!result.getMessage().isEmpty()) {
            System.out.println(result.getMessage());
        }

        Object data = result.getData();
        if (data == null) {
            return;
        }


        if (data instanceof Variable) {
            System.out.println(data);
        } else if (data instanceof List) {
            List<?> list = (List<?>) data;
            if (list.isEmpty()) {
                System.out.println("(empty)");
            } else {
                for (Object item : list) {
                    System.out.println("  " + item);
                }
            }
        } else if (data instanceof DebugFrame) {
            System.out.println("Current frame: " + data);
        } else if (data instanceof CallStack) {
            System.out.println(data);
        } else if (data instanceof MethodInfo) {
            System.out.println("Method: " + data);
        } else if (data instanceof ObjectReference) {
            ObjectReference obj = (ObjectReference) data;
            System.out.println(obj.referenceType().name() + "@" + obj.uniqueID());
        } else if (data instanceof Breakpoint) {
            System.out.println("Breakpoint: " + data);
        } else {
            System.out.println(data);
        }
    }

    private void handleBreakpoint(BreakpointEvent event) throws IncompatibleThreadStateException, AbsentInformationException {
        Location loc = event.location();
        System.out.println("\n=== Breakpoint hit ===");
        System.out.println("Location: " + loc.sourceName() + ":" + loc.lineNumber());
        System.out.println("Method: " + loc.method().name());

        // Vérifier si c'est un breakpoint spécial
        String key = loc.sourceName() + ":" + loc.lineNumber();
        Breakpoint bp = state.getBreakpoints().get(key);

        if (bp != null) {
            bp.incrementHitCount();

            if (!bp.shouldStop()) {
                System.out.println("Breakpoint condition not met, continuing...");
                return;
            }

            // Si c'est un breakpoint "once", le désactiver
            if (bp.getType() == Breakpoint.BreakpointType.ONCE) {
                bp.getRequest().disable();
                state.getBreakpoints().remove(key);
                System.out.println("One-time breakpoint removed");
            }
        }

        // Attendre commande
        handleUserCommand(event.thread());
    }

    private void handleMethodEntry(MethodEntryEvent event) throws IncompatibleThreadStateException {
        Method method = event.method();

        // Vérifier si on attend ce method entry
        for (String methodName : state.getMethodBreakpoints().keySet()) {
            if (method.name().equals(methodName)) {
                System.out.println("\n=== Method entry: " + method.name() + " ===");
                System.out.println("Class: " + method.declaringType().name());
                handleUserCommand(event.thread());
                return;
            }
        }
    }

    private void setInitialBreakpoint() {
        for (ReferenceType type : vm.allClasses()) {
            if (type.name().equals(debugClass.getName())) {
                try {
                    // Récupérer le nom du fichier source
                    String sourceFile = type.sourceName();

                    // Placer le breakpoint à la ligne 6 (ou autre)
                    List<Location> locations = type.locationsOfLine(6);
                    if (!locations.isEmpty()) {
                        Location loc = locations.get(0);
                        BreakpointRequest req = vm.eventRequestManager()
                                .createBreakpointRequest(loc);
                        req.enable();
                        System.out.println("Initial breakpoint set at " + sourceFile + ":6");
                        return;
                    }
                } catch (AbsentInformationException e) {
                    System.err.println("No debug info available");
                }
            }
        }
    }

    public void startDebugger() throws InterruptedException, AbsentInformationException {
        System.out.println("=== Scriptable Debugger Started ===");
        System.out.println("Available commands: " + interpreter.getAvailableCommands());

        while (true) {
            EventSet eventSet = vm.eventQueue().remove();

            for (Event event : eventSet) {
                try {
                    if (event instanceof VMDisconnectEvent) {
                        System.out.println("\n=== Program terminated ===");
                        printProcessOutput();
                        return;
                    }

                    if (event instanceof ClassPrepareEvent) {
                        System.out.println("Class loaded: " + debugClass.getName());
                        setInitialBreakpoint();
                    }

                    if (event instanceof BreakpointEvent) {
                        handleBreakpoint((BreakpointEvent) event);
                    }

                    if (event instanceof StepEvent) {
                        StepEvent se = (StepEvent) event;
                        System.out.println("\nStepped to: " + se.location().sourceName()
                                + ":" + se.location().lineNumber());

                        // Supprimer la StepRequest après usage
                        vm.eventRequestManager().deleteEventRequest(event.request());

                        handleUserCommand(se.thread());
                    }

                    if (event instanceof MethodEntryEvent) {
                        handleMethodEntry((MethodEntryEvent) event);
                    }

                } catch (Exception e) {
                    System.err.println("Error handling event: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            vm.resume();
        }
    }

    public void printProcessOutput() {
        try {
            InputStreamReader reader = new InputStreamReader(vm.process().getInputStream());
            OutputStreamWriter writer = new OutputStreamWriter(System.out);
            reader.transferTo(writer);
            writer.flush();
        } catch (IOException e) {
            System.err.println("Error reading VM output: " + e.getMessage());
        }
    }

}