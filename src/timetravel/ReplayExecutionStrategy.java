package timetravel;

import commands.DebuggerState;
import commands.CommandResult;
import models.Breakpoint;
import java.util.List;


public class ReplayExecutionStrategy implements ExecutionStrategy {

    @Override
    public CommandResult step(DebuggerState state) {
        TimelineManager tm = state.getTimelineManager();
        int nextIndex = tm.getCurrentSnapshotIndex() + 1;

        if (nextIndex < tm.getTimelineSize()) {
            tm.travelToSnapshot(tm.getTimeline().get(nextIndex).getSnapshotId());
            return CommandResult.success("Stepped to next snapshot");
        }

        return CommandResult.error("End of timeline reached");
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

        // Chercher le prochain breakpoint
        for (int i = currentIndex + 1; i < timeline.size(); i++) {
            ExecutionSnapshot snap = timeline.get(i);
            if (isBreakpointHit(state, snap)) {
                tm.travelToSnapshot(snap.getSnapshotId());
                return CommandResult.success("Breakpoint hit at " +
                        snap.getSourceFile() + ":" + snap.getLineNumber());
            }
        }

        // Si pas de breakpoint, aller à la fin
        if (timeline.size() > 0) {
            ExecutionSnapshot last = timeline.get(timeline.size() - 1);
            tm.travelToSnapshot(last.getSnapshotId());
            return CommandResult.success("Reached end of execution");
        }

        return CommandResult.error("Timeline empty");
    }

    @Override
    public CommandResult setBreakpoint(DebuggerState state, String fileName, int lineNumber) {
        String key = fileName + ":" + lineNumber;
        Breakpoint bp = new Breakpoint(fileName, lineNumber);
        state.getBreakpoints().put(key, bp);
        return CommandResult.success("Replay breakpoint set at " + key);
    }

    @Override
    public CommandResult printVariable(DebuggerState state, String varName) {
        ExecutionSnapshot snap = state.getTimelineManager().getCurrentSnapshot();
        if (snap != null && snap.getVariables().containsKey(varName)) {
            String value = snap.getVariables().get(varName);
            return CommandResult.success(varName + " = " + value);
        }
        return CommandResult.error("Variable '" + varName + "' not found in current snapshot");
    }

    /**
     * Vérifie si deux snapshots sont au même endroit
     */
    private boolean isSameLocation(ExecutionSnapshot s1, ExecutionSnapshot s2) {
        return s1.getSourceFile().equals(s2.getSourceFile()) &&
                s1.getLineNumber() == s2.getLineNumber() &&
                s1.getMethodName().equals(s2.getMethodName());
    }

    /**
     * Vérifie si un breakpoint est placé sur ce snapshot
     */
    private boolean isBreakpointHit(DebuggerState state, ExecutionSnapshot snap) {
        String key = snap.getSourceFile() + ":" + snap.getLineNumber();
        return state.getBreakpoints().containsKey(key);
    }
}