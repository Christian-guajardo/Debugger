package timetravel;

import com.sun.jdi.*;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.StepRequest;
import commands.DebuggerState;
import commands.CommandResult;
import models.Breakpoint;
import models.Variable;

import java.util.List;

public class LiveExecutionStrategy implements ExecutionStrategy {

    @Override
    public CommandResult step(DebuggerState state) {
        if (state.getContext() == null) {
            return CommandResult.error("No execution context available");
        }
        return createStepRequest(state, StepRequest.STEP_LINE, StepRequest.STEP_INTO, "Stepping into...");
    }

    @Override
    public CommandResult stepOver(DebuggerState state) {
        if (state.getContext() == null) {
            return CommandResult.error("No execution context available");
        }
        return createStepRequest(state, StepRequest.STEP_LINE, StepRequest.STEP_OVER, "Stepping over...");
    }

    @Override
    public CommandResult continueRun(DebuggerState state) {
        return CommandResult.success("Continuing execution...");
    }

    @Override
    public CommandResult setBreakpoint(DebuggerState state, String fileName, int lineNumber) {
        VirtualMachine vm = state.getVm();

        for (ReferenceType refType : vm.allClasses()) {
            try {
                if (refType.sourceName().equals(fileName)) {
                    List<Location> locations = refType.locationsOfLine(lineNumber);

                    if (!locations.isEmpty()) {
                        Location loc = locations.get(0);

                        BreakpointRequest req = vm.eventRequestManager()
                                .createBreakpointRequest(loc);
                        req.enable();

                        String key = fileName + ":" + lineNumber;
                        Breakpoint bp = new Breakpoint(
                                fileName,
                                lineNumber,
                                req,
                                Breakpoint.BreakpointType.NORMAL
                        );
                        state.getBreakpoints().put(key, bp);

                        return CommandResult.success(
                                "Breakpoint set at " + fileName + ":" + lineNumber,
                                bp
                        );
                    }
                }
            } catch (AbsentInformationException e) {

            }

        }
        return CommandResult.error("Could not set breakpoint at " + fileName + ":" + lineNumber);
    }

    @Override
    public CommandResult printVariable(DebuggerState state, String varName) {
        if (state.getContext() == null || state.getContext().getCurrentFrame() == null) {
            return CommandResult.error("No frame available");
        }

        StackFrame frame = state.getContext().getCurrentFrame().getFrame();

        try {
            for (LocalVariable lv : frame.visibleVariables()) {
                if (lv.name().equals(varName)) {
                    return CommandResult.success("", new Variable(lv.name(), lv.typeName(), frame.getValue(lv)));
                }
            }

            ObjectReference receiver = frame.thisObject();
            if (receiver != null) {
                Field field = receiver.referenceType().fieldByName(varName);
                if (field != null) {
                    return CommandResult.success("", new Variable(field.name(), field.typeName(), receiver.getValue(field)));
                }
            }

            return CommandResult.error("Variable '" + varName + "' not found");
        } catch (AbsentInformationException e) {
            return CommandResult.error("No debug information available");
        }
    }


    private CommandResult createStepRequest(DebuggerState state, int size, int depth, String message) {
        try {
            ThreadReference thread = state.getContext().getThread();
            VirtualMachine vm = state.getVm();

            vm.eventRequestManager().deleteEventRequests(vm.eventRequestManager().stepRequests());

            StepRequest stepRequest = vm.eventRequestManager()
                    .createStepRequest(thread, size, depth);

            stepRequest.addClassExclusionFilter("java.*");
            stepRequest.addClassExclusionFilter("javax.*");
            stepRequest.addClassExclusionFilter("sun.*");
            stepRequest.addClassExclusionFilter("com.sun.*");
            stepRequest.addClassExclusionFilter("jdk.*");
            stepRequest.addClassExclusionFilter("oracle.*");

            stepRequest.addCountFilter(1);
            stepRequest.enable();

            return CommandResult.success(message);
        } catch (Exception e) {
            return CommandResult.error("Error: " + e.getMessage());
        }
    }
}