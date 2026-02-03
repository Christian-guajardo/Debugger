package timetravel;

import com.sun.jdi.Value;

public class VariableModification {
    private final String variableName;
    private final String oldValue;
    private final String newValue;
    private final int snapshotId;
    private final int lineNumber;
    private final String methodName;
    private final long timestamp;

    public VariableModification(String variableName, String oldValue, String newValue,
                                int snapshotId, int lineNumber, String methodName) {
        this.variableName = variableName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.snapshotId = snapshotId;
        this.lineNumber = lineNumber;
        this.methodName = methodName;
        this.timestamp = System.currentTimeMillis();
    }


    public String getVariableName() { return variableName; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public int getSnapshotId() { return snapshotId; }
    public int getLineNumber() { return lineNumber; }
    public String getMethodName() { return methodName; }
    public long getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return String.format("%s: %s → %s (ligne %d dans %s())",
                variableName, oldValue, newValue, lineNumber, methodName);
    }
}
