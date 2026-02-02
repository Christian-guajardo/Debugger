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
        if (state.getContext() == null || state.getContext().getCurrentFrame() == null) {
            return CommandResult.error("No frame available");
        }

        StackFrame frame = state.getContext().getCurrentFrame().getFrame();

        try {
            for (LocalVariable lv : frame.visibleVariables()) {
                if (lv.name().equals(varName)) {
                    return CommandResult.success("", new Variable(lv.name(), lv.typeName(), frame.getValue(lv)));
                }
            }

            ObjectReference receiver = frame.thisObject();
            if (receiver != null) {
                Field field = receiver.referenceType().fieldByName(varName);
                if (field != null) {
                    return CommandResult.success("", new Variable(field.name(), field.typeName(), receiver.getValue(field)));
                }
            }

            return CommandResult.error("Variable '" + varName + "' not found");
        } catch (AbsentInformationException e) {
            return CommandResult.error("No debug information available");
        }
    }
}
