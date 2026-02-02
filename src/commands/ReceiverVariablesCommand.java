package commands;

import com.sun.jdi.*;
import models.Variable;
import java.util.ArrayList;
import java.util.List;

public class ReceiverVariablesCommand implements Command {
    @Override
    public CommandResult execute(DebuggerState state) {
        if (state.getContext() == null || state.getContext().getCurrentFrame() == null) {
            return CommandResult.error("No frame available");
        }

        ObjectReference receiver = state.getContext().getCurrentFrame().getReceiver();
        if (receiver == null) {
            return CommandResult.error("No receiver (static method)");
        }

        List<Variable> variables = new ArrayList<>();
        for (Field field : receiver.referenceType().allFields()) {
            Value value = receiver.getValue(field);
            variables.add(new Variable(field.name(), field.typeName(), value));
        }

        return CommandResult.success("Receiver variables:", variables);
    }
}
