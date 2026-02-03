package commands;

import models.Variable;
import java.util.List;

public class TemporariesCommand implements Command {
    @Override
    public CommandResult execute(DebuggerState state) {
        if (state.getContext() == null || state.getContext().getCurrentFrame() == null) {
            return CommandResult.error("No frame available");
        }

        List<Variable> temporaries = state.getContext().getCurrentFrame().getTemporaries();
        return CommandResult.success("Temporaries:", temporaries);
    }
}
