package models;

import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.ThreadReference;

public class ExecutionContext {
    private ThreadReference thread;
    private CallStack callStack;
    private DebugFrame currentFrame;
    private MethodInfo currentMethod;

    public ExecutionContext(ThreadReference thread) throws IncompatibleThreadStateException {
        this.thread = thread;
        refresh();
    }

    public void refresh() throws IncompatibleThreadStateException {
        this.callStack = new CallStack(thread);
        this.currentFrame = callStack.getCurrentFrame();
        if (currentFrame != null) {
            this.currentMethod = new MethodInfo(
                    currentFrame.getLocation().method(),
                    currentFrame.getFrame()
            );
        }
    }

    public ThreadReference getThread() { return thread; }
    public CallStack getCallStack() { return callStack; }
    public DebugFrame getCurrentFrame() { return currentFrame; }
    public MethodInfo getCurrentMethod() { return currentMethod; }
}
