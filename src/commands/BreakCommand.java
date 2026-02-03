package commands;

import com.sun.jdi.*;
import com.sun.jdi.request.BreakpointRequest;
import models.Breakpoint;
import java.util.List;

public class BreakCommand implements Command {
    private String fileName;
    private int lineNumber;

    public BreakCommand(String fileName, int lineNumber) {
        this.fileName = fileName;
        this.lineNumber = lineNumber;
    }

    @Override
    public CommandResult execute(DebuggerState state) throws Exception {

        return state.getExecutionStrategy().setBreakpoint(state, fileName, lineNumber);
    }


}
