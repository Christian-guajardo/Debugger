package commands;

import com.sun.jdi.*;
import com.sun.jdi.request.BreakpointRequest;
import models.Breakpoint;
import java.util.List;

public class BreakCommand implements Command {
    private String fileName;
    private int lineNumber;

    public BreakCommand(String fileName, int lineNumber) {
        this.fileName = fileName;
        this.lineNumber = lineNumber;
    }

    @Override
    public CommandResult execute(DebuggerState state) throws Exception {
        VirtualMachine vm = state.getVm();

        for (ReferenceType refType : vm.allClasses()) {
            try {
                if (refType.sourceName().equals(fileName)) {
                    List<Location> locations = refType.locationsOfLine(lineNumber);

                    if (!locations.isEmpty()) {
                        Location loc = locations.get(0);

                        BreakpointRequest req = vm.eventRequestManager()
                                .createBreakpointRequest(loc);
                        req.enable();

                        String key = fileName + ":" + lineNumber;
                        Breakpoint bp = new Breakpoint(
                                fileName,
                                lineNumber,
                                req,
                                Breakpoint.BreakpointType.NORMAL
                        );
                        state.getBreakpoints().put(key, bp);

                        return CommandResult.success(
                                "Breakpoint set at " + fileName + ":" + lineNumber,
                                bp
                        );
                    }
                }
            } catch (AbsentInformationException e) {
            }
        }

        return CommandResult.error("Could not set breakpoint at " + fileName + ":" + lineNumber);
    }
}