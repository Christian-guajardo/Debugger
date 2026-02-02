package commands;

public interface Command {
    CommandResult execute(DebuggerState state) throws Exception;
}
