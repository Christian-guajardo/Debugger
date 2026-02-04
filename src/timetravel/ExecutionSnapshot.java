package timetravel;

import com.sun.jdi.*;
import models.*;
import java.util.*;


public class ExecutionSnapshot {
    private final int snapshotId;
    private final long timestamp;
    private final Location location;
    private final int lineNumber;
    private final String sourceFile;
    private final String methodName;
    private final Map<String, String> variables;
    private final List<String> callStack;
    private final ThreadReference thread;
    private final String programOutputSoFar;

    public ExecutionSnapshot(int id, Location loc, ThreadReference thread, String programOutput)
            throws IncompatibleThreadStateException, AbsentInformationException {
        this.snapshotId = id;
        this.timestamp = System.currentTimeMillis();
        this.location = loc;
        this.lineNumber = loc.lineNumber();
        this.sourceFile = loc.sourceName();
        this.methodName = loc.method().name();
        this.thread = thread;
        this.programOutputSoFar = programOutput != null ? programOutput : "";
        this.variables = new HashMap<>();
        captureVariables(thread);
        this.callStack = new ArrayList<>();
        captureCallStack(thread);
    }

    private void captureVariables(ThreadReference thread)
            throws IncompatibleThreadStateException {
        try {
            StackFrame frame = thread.frame(0);
            for (LocalVariable var : frame.visibleVariables()) {
                Value value = frame.getValue(var);
                variables.put(var.name(), value != null ? value.toString() : "null");
            }
        } catch (AbsentInformationException e) {

        }
    }

    private void captureCallStack(ThreadReference thread)
            throws IncompatibleThreadStateException {
        for (StackFrame frame : thread.frames()) {
            try {
                String frameName = frame.location().declaringType().name() +
                        "." + frame.location().method().name() +
                        "() ligne " + frame.location().lineNumber();
                callStack.add(frameName);
            } catch (Exception e) {
                callStack.add("<unknown frame>");
            }
        }
    }


    public int getSnapshotId() { return snapshotId; }
    public long getTimestamp() { return timestamp; }
    public Location getLocation() { return location; }
    public int getLineNumber() { return lineNumber; }
    public String getSourceFile() { return sourceFile; }
    public String getMethodName() { return methodName; }
    public Map<String, String> getVariables() { return variables; }
    public List<String> getCallStack() { return callStack; }
    public ThreadReference getThread() { return thread; }
    public String getProgramOutputSoFar() { return programOutputSoFar; }

    @Override
    public String toString() {
        return String.format("Snapshot #%d: %s:%d in %s()",
                snapshotId, sourceFile, lineNumber, methodName);
    }
}