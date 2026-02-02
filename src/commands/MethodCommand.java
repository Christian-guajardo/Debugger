package commands;

import models.MethodInfo;

public class MethodCommand implements Command {
    @Override
    public CommandResult execute(DebuggerState state) {
        if (state.getContext() == null || state.getContext().getCurrentMethod() == null) {
            return CommandResult.error("No method available");
        }

        return CommandResult.success("", state.getContext().getCurrentMethod());
    }
}
