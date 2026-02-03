package models;

import com.sun.jdi.*;
import java.util.ArrayList;
import java.util.List;

public class CallStack {
    private List<DebugFrame> frames;

    public CallStack(ThreadReference thread) throws IncompatibleThreadStateException {
        this.frames = new ArrayList<>();
        for (StackFrame sf : thread.frames()) {
            frames.add(new DebugFrame(sf));
        }
    }

    public List<DebugFrame> getFrames() { return frames; }

    public DebugFrame getCurrentFrame() {
        return frames.isEmpty() ? null : frames.get(0);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Call Stack:\n");
        for (int i = 0; i < frames.size(); i++) {
            sb.append("  [").append(i).append("] ").append(frames.get(i)).append("\n");
        }
        return sb.toString();
    }
}
