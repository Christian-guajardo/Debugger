package commands;

import com.sun.jdi.ObjectReference;
import models.DebugFrame;

public class SenderCommand implements Command {
    @Override
    public CommandResult execute(DebuggerState state) {
        if (state.getContext() == null || state.getContext().getCallStack() == null) {
            return CommandResult.error("No call stack available");
        }

        if (state.getContext().getCallStack().getFrames().size() < 2) {
            return CommandResult.error("No sender (top-level call)");
        }

        DebugFrame callingFrame = state.getContext().getCallStack().getFrames().get(1);
        ObjectReference sender = callingFrame.getReceiver();

        if (sender == null) {
            return CommandResult.error("Sender is a static context");
        }

        return CommandResult.success("Sender:", sender);
    }
}
