package commands;

import models.DebugFrame;

public class FrameCommand implements Command {
    @Override
    public CommandResult execute(DebuggerState state) {
        if (state.getContext() == null) {
            return CommandResult.error("No execution context available");
        }

        DebugFrame frame = state.getContext().getCurrentFrame();
        if (frame == null) {
            return CommandResult.error("No frame available");
        }

        return CommandResult.success("", frame);
    }
}
