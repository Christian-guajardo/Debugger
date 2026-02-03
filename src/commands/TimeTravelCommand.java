package commands;

import timetravel.TimelineManager;

public class TimeTravelCommand implements Command {
    private int snapshotId;

    public TimeTravelCommand(int snapshotId) {
        this.snapshotId = snapshotId;
    }

    @Override
    public CommandResult execute(DebuggerState state) throws Exception {
        TimelineManager timeline = state.getTimelineManager();

        if (timeline == null) {
            return CommandResult.error("Timeline not available");
        }

        boolean success = timeline.travelToSnapshot(snapshotId);

        if (success) {
            return CommandResult.success("Traveled to snapshot #" + snapshotId);
        } else {
            return CommandResult.error("Snapshot #" + snapshotId + " not found");
        }
    }
}