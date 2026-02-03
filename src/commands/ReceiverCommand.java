package commands;

import com.sun.jdi.ObjectReference;

public class ReceiverCommand implements Command {
    @Override
    public CommandResult execute(DebuggerState state) {
        if (state.getContext() == null || state.getContext().getCurrentFrame() == null) {
            return CommandResult.error("No frame available");
        }

        ObjectReference receiver = state.getContext().getCurrentFrame().getReceiver();
        if (receiver == null) {
            return CommandResult.error("No receiver (static method or main)");
        }

        return CommandResult.success("Receiver:", receiver);
    }
}
