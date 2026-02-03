package commands;

import com.sun.jdi.*;
import com.sun.jdi.request.StepRequest;

public class StepCommand implements Command {
    @Override
    public CommandResult execute(DebuggerState state) throws Exception {
        return state.getExecutionStrategy().step(state);
    }
}
