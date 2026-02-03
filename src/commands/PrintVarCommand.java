package commands;

import com.sun.jdi.*;
import models.Variable;

public class PrintVarCommand implements Command {
    private final String varName;

    public PrintVarCommand(String varName) {
        this.varName = varName;
    }

    @Override
    public CommandResult execute(DebuggerState state) throws Exception {

        return state.getExecutionStrategy().printVariable(state, varName);
    }
}
