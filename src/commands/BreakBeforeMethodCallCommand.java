package commands;

import com.sun.jdi.*;
import com.sun.jdi.request.MethodEntryRequest;

public class BreakBeforeMethodCallCommand implements Command {
    private String methodName;

    public BreakBeforeMethodCallCommand(String methodName) {
        this.methodName = methodName;
    }

    @Override
    public CommandResult execute(DebuggerState state) throws Exception {
        VirtualMachine vm = state.getVm();

        MethodEntryRequest req = vm.eventRequestManager().createMethodEntryRequest();

        boolean found = false;
        for (ReferenceType refType : vm.allClasses()) {
            for (Method method : refType.methods()) {
                if (method.name().equals(methodName)) {
                    req.addClassFilter(refType);
                    found = true;
                    break;
                }
            }
            if (found) break;
        }

        if (!found) {
            return CommandResult.error("Method '" + methodName + "' not found");
        }

        req.enable();
        state.getMethodBreakpoints().put(methodName, req);

        return CommandResult.success("Method entry breakpoint set for: " + methodName);
    }
}