package timetravel;

import commands.DebuggerState;
import commands.CommandResult;
import models.Breakpoint;
import java.util.List;
import java.util.Map;

public class ReplayExecutionStrategy implements ExecutionStrategy {

    @Override
    public CommandResult step(DebuggerState state) {
        TimelineManager tm = state.getTimelineManager();
        int nextIndex = tm.getCurrentSnapshotIndex() + 1;
        if (tm.travelToSnapshot(nextIndex)) {

            return CommandResult.success("Replay: Stepped to snapshot");
        }
        return CommandResult.error("Replay: End of timeline reached.");
    }

    @Override
    public CommandResult stepOver(DebuggerState state) {
        TimelineManager tm = state.getTimelineManager();
        ExecutionSnapshot current = tm.getCurrentSnapshot();

        if (current == null) return CommandResult.error("No current snapshot.");

        int currentStackDepth = current.getCallStack().size();
        List<ExecutionSnapshot> timeline = tm.getTimeline();

        for (int i = tm.getCurrentSnapshotIndex() + 1; i < timeline.size(); i++) {
            if (timeline.get(i).getCallStack().size() <= currentStackDepth) {
                tm.travelToSnapshot(timeline.get(i).getSnapshotId());
                return CommandResult.success("Replay: Stepped Over to snapshot #" + timeline.get(i).getSnapshotId());
            }
        }
        return CommandResult.error("Replay: Could not step over (end of scope or trace).");
    }

    @Override
    public CommandResult continueRun(DebuggerState state) {
        TimelineManager tm = state.getTimelineManager();
        List<ExecutionSnapshot> timeline = tm.getTimeline();
        int currentIndex = tm.getCurrentSnapshotIndex();


        for (int i = currentIndex + 1; i < timeline.size(); i++) {
            ExecutionSnapshot snap = timeline.get(i);
            if (isBreakpointHit(state, snap)) {
                tm.travelToSnapshot(snap.getSnapshotId());
                return CommandResult.success("Replay: Breakpoint hit at " +
                        snap.getSourceFile() + ":" + snap.getLineNumber());
            }
        }

        int lastIndex = tm.getTimelineSize() - 1;
        if (lastIndex >= 0) {
            ExecutionSnapshot last = timeline.get(lastIndex);
            tm.travelToSnapshot(last.getSnapshotId());
            return CommandResult.success("Replay: Jumped to end of execution.");
        }

        return CommandResult.error("Timeline empty.");
    }

    @Override
    public CommandResult setBreakpoint(DebuggerState state, String fileName, int lineNumber) {
        String key = fileName + ":" + lineNumber;

        Breakpoint bp = new Breakpoint(fileName, lineNumber);
        state.getBreakpoints().put(key, bp);

        return CommandResult.success("Replay Breakpoint set at " + key + " (Simulation)");
    }

    @Override
    public CommandResult printVariable(DebuggerState state, String varName) {
        ExecutionSnapshot snap = state.getTimelineManager().getCurrentSnapshot();
        if (snap != null && snap.getVariables().containsKey(varName)) {
            return CommandResult.success(snap.getVariables().get(varName));
        }
        return CommandResult.error("Variable inconnue dans ce snapshot.");
    }


    private boolean isBreakpointHit(DebuggerState state, ExecutionSnapshot snap) {
        String key = snap.getSourceFile() + ":" + snap.getLineNumber();
        return state.getBreakpoints().containsKey(key);
    }
}