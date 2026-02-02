package commands;

import com.sun.jdi.*;
import com.sun.jdi.request.StepRequest;

public class StepOverCommand implements Command {
    @Override
    public CommandResult execute(DebuggerState state) throws Exception {
        if (state.getContext() == null) {
            return CommandResult.error("No execution context available");
        }

        ThreadReference thread = state.getContext().getThread();
        VirtualMachine vm = state.getVm();

        StepRequest stepRequest = vm.eventRequestManager()
            .createStepRequest(thread, StepRequest.STEP_LINE, StepRequest.STEP_OVER);

        stepRequest.addClassExclusionFilter("java.*");
        stepRequest.addClassExclusionFilter("javax.*");
        stepRequest.addClassExclusionFilter("sun.*");
        stepRequest.addClassExclusionFilter("com.sun.*");
        stepRequest.addClassExclusionFilter("jdk.*");

        stepRequest.addCountFilter(1);
        stepRequest.enable();

        return CommandResult.success("Stepping over...");
    }
}
