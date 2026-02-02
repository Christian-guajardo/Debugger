package commands;

public class ContinueCommand implements Command {
    @Override
    public CommandResult execute(DebuggerState state) {
        return CommandResult.success("Continuing execution...");
    }
}
