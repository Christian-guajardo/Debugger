package commands;

import com.sun.jdi.*;
import com.sun.jdi.request.BreakpointRequest;
import models.Breakpoint;
import java.util.List;

public class BreakOnCountCommand implements Command {
    private String fileName;
    private int lineNumber;
    private int count;

    public BreakOnCountCommand(String fileName, int lineNumber, int count) {
        this.fileName = fileName;
        this.lineNumber = lineNumber;
        this.count = count;
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
                                Breakpoint.BreakpointType.ON_COUNT,
                                count
                        );
                        state.getBreakpoints().put(key, bp);

                        return CommandResult.success(
                                "Conditional breakpoint set at " + fileName + ":" + lineNumber +
                                        " (will stop after " + count + " hits)",
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