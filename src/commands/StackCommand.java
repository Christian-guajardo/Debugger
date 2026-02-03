package commands;

import models.CallStack;

public class StackCommand implements Command {
    @Override
    public CommandResult execute(DebuggerState state) {
        if (state.getContext() == null) {
            return CommandResult.error("No execution context available");
        }

        CallStack stack = state.getContext().getCallStack();
        if (stack == null) {
            return CommandResult.error("No call stack available");
        }

        return CommandResult.success("", stack);
    }
}
