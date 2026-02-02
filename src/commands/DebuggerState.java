package commands;

import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.request.MethodEntryRequest;
import models.Breakpoint;
import models.ExecutionContext;

import java.util.HashMap;
import java.util.Map;

public class DebuggerState {
    private VirtualMachine vm;
    private ExecutionContext context;
    private Map<String, Breakpoint> breakpoints;
    private Map<String, MethodEntryRequest> methodBreakpoints;
    private boolean running;

    public DebuggerState(VirtualMachine vm) {
        this.vm = vm;
        this.breakpoints = new HashMap<>();
        this.methodBreakpoints = new HashMap<>();
        this.running = true;
    }

    public void updateContext(ThreadReference thread) throws IncompatibleThreadStateException {
        this.context = new ExecutionContext(thread);
    }

    public VirtualMachine getVm() { return vm; }
    public ExecutionContext getContext() { return context; }
    public Map<String, Breakpoint> getBreakpoints() { return breakpoints; }
    public Map<String, MethodEntryRequest> getMethodBreakpoints() { return methodBreakpoints; }
    public boolean isRunning() { return running; }
    public void setRunning(boolean running) { this.running = running; }
}