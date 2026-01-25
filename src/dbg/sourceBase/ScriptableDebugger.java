package dbg.sourceBase;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.event.*;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.StepRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Map;

public class ScriptableDebugger {

    private Class debugClass;
    private VirtualMachine vm;

    public VirtualMachine connectAndLaunchVM() throws IOException, IllegalConnectorArgumentsException, VMStartException {
        LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
        arguments.get("main").setValue(debugClass.getName());
        VirtualMachine vm = launchingConnector.launch(arguments);
        return vm;
    }
    public void attachTo(Class debuggeeClass) {

        this.debugClass = debuggeeClass;
        try {
            vm = connectAndLaunchVM();
            enableClassPrepareRequest(vm);
            startDebugger();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (IllegalConnectorArgumentsException e) {
            e.printStackTrace();
        } catch (VMStartException e) {
            e.printStackTrace();
            System.out.println(e.toString());
        } catch (VMDisconnectedException e) {
            System.out.println("Virtual Machine is disconnected: " + e.toString());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startDebugger() throws VMDisconnectedException, InterruptedException {
        EventSet eventSet = null;
        while ((eventSet = vm.eventQueue().remove()) != null) {
            for (Event event : eventSet) {

                if (event instanceof ClassPrepareEvent) {
                    setBreakPoint(debugClass.getName(), 6);
                }
                if (event instanceof BreakpointEvent) {
                    System.out.println("Breakpoint hit! Enter command (type 'step' to step, anything else to continue):");
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                        String command = reader.readLine();

                        if ("step".equals(command)) {
                            enableStepRequest((BreakpointEvent) event);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (event instanceof StepEvent) {
                    System.out.println("Step event! Enter command (type 'step' to continue stepping, anything else to continue):");
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                        String command = reader.readLine();

                        if (!"step".equals(command)) {

                            for (StepRequest sr : vm.eventRequestManager().stepRequests()) {
                                sr.disable();
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (event instanceof VMDisconnectEvent) {
                    System.out.println("===End of program.");
                    InputStreamReader reader = new InputStreamReader(vm.process().getInputStream());
                    OutputStreamWriter writer = new OutputStreamWriter(System.out);
                    try {
                        reader.transferTo(writer);
                        writer.flush();
                    } catch (IOException e) {
                        System.out.println("Target VM inputstream reading error.");
                    }
                    return;
                }

                System.out.println(event.toString());
                vm.resume();
            }
        }


    }
    public void enableClassPrepareRequest(VirtualMachine vm) {
        ClassPrepareRequest classPrepareRequest =
                vm.eventRequestManager().createClassPrepareRequest();
        classPrepareRequest.addClassFilter(debugClass.getName());
        classPrepareRequest.enable();
    }
    public void setBreakPoint(String className, int lineNumber) {
        for (ReferenceType targetClass : vm.allClasses()) {
            if (targetClass.name().equals(className)) {
                try {
                    Location location = targetClass.locationsOfLine(lineNumber).get(0);
                    BreakpointRequest bpReq =
                            vm.eventRequestManager().createBreakpointRequest(location);
                    bpReq.enable();
                } catch (AbsentInformationException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void enableStepRequest(LocatableEvent event) {
        StepRequest stepRequest =
                vm.eventRequestManager().createStepRequest(
                        event.thread(),
                        StepRequest.STEP_MIN,
                        StepRequest.STEP_OVER
                );
        stepRequest.enable();
    }

    public void waitForUserCommand() throws IOException {
        System.out.println("Enter command (type 'step' to step, anything else to continue):");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String command = reader.readLine();

        if ("step".equals(command)) {
            // On ne fait rien ici, le step sera configuré après
        } else {
            System.out.println("Continuing execution...");
        }
    }
}
