package timetravel;


import commands.*;

public interface ExecutionStrategy {
    CommandResult step(DebuggerState state);
    CommandResult stepOver(DebuggerState state);
    CommandResult continueRun(DebuggerState state);
    CommandResult setBreakpoint(DebuggerState state, String fileName, int lineNumber);
    CommandResult printVariable(DebuggerState state, String varName);
}