package commands;

import com.sun.jdi.*;
import com.sun.jdi.request.StepRequest;

public class StepOverCommand implements Command {
    @Override
    public CommandResult execute(DebuggerState state) throws Exception {
        return state.getExecutionStrategy().stepOver(state);
    }
}
