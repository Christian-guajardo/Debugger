package commands;


import models.Breakpoint;
import java.util.ArrayList;
import java.util.List;

public class BreakpointsCommand implements Command {
    @Override
    public CommandResult execute(DebuggerState state) throws Exception {
        if (state.getBreakpoints().isEmpty()) {
            return CommandResult.success("No breakpoints set");
        }

        List<Breakpoint> breakpoints = new ArrayList<>(state.getBreakpoints().values());
        return CommandResult.success("Active breakpoints:", breakpoints);
    }
}