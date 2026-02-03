package commands;

import models.Variable;
import java.util.List;

public class ArgumentsCommand implements Command {
    @Override
    public CommandResult execute(DebuggerState state) {
        if (state.getContext() == null || state.getContext().getCurrentMethod() == null) {
            return CommandResult.error("No method available");
        }

        List<Variable> args = state.getContext().getCurrentMethod().getArguments();
        return CommandResult.success("Arguments:", args);
    }
}
