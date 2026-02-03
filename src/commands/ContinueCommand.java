package commands;

public class ContinueCommand implements Command {
    @Override
    public CommandResult execute(DebuggerState state) throws Exception {
        return state.getExecutionStrategy().continueRun(state);
    }
}
