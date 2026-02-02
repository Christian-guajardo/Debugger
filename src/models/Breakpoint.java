package models;

import com.sun.jdi.request.BreakpointRequest;

public class Breakpoint {
    private String fileName;
    private int lineNumber;
    private BreakpointRequest request;
    private BreakpointType type;
    private int hitCount;
    private int targetCount;

    public enum BreakpointType {
        NORMAL,
        ONCE,
        ON_COUNT
    }

    public Breakpoint(String fileName, int lineNumber, BreakpointRequest request, BreakpointType type) {
        this(fileName, lineNumber, request, type, 0);
    }

    public Breakpoint(String fileName, int lineNumber, BreakpointRequest request,
                      BreakpointType type, int targetCount) {
        this.fileName = fileName;
        this.lineNumber = lineNumber;
        this.request = request;
        this.type = type;
        this.hitCount = 0;
        this.targetCount = targetCount;
    }

    public void incrementHitCount() { hitCount++; }
    public int getHitCount() { return hitCount; }
    public int getTargetCount() { return targetCount; }
    public BreakpointType getType() { return type; }
    public BreakpointRequest getRequest() { return request; }
    public String getFileName() { return fileName; }
    public int getLineNumber() { return lineNumber; }

    public boolean shouldStop() {
        switch (type) {
            case NORMAL:
                return true;
            case ONCE:
                return hitCount == 0;
            case ON_COUNT:
                return hitCount >= targetCount;
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        String info = fileName + ":" + lineNumber;
        switch (type) {
            case ONCE:
                info += " [once]";
                break;
            case ON_COUNT:
                info += " [count: " + hitCount + "/" + targetCount + "]";
                break;
        }
        return info;
    }
}
