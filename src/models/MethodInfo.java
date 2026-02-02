package models;

import com.sun.jdi.*;
import java.util.ArrayList;
import java.util.List;

public class MethodInfo {
    private Method method;
    private List<Variable> arguments;

    public MethodInfo(Method method, StackFrame frame) throws IncompatibleThreadStateException {
        this.method = method;
        this.arguments = new ArrayList<>();
        loadArguments(frame);
    }

    private void loadArguments(StackFrame frame) throws IncompatibleThreadStateException {
        try {
            List<LocalVariable> vars = method.variables();
            for (LocalVariable lv : vars) {
                if (lv.isArgument()) {
                    Value val = frame.getValue(lv);
                    arguments.add(new Variable(lv.name(), lv.typeName(), val));
                }
            }
        } catch (AbsentInformationException e) {
        }
    }

    public Method getMethod() { return method; }
    public List<Variable> getArguments() { return arguments; }

    @Override
    public String toString() {
        return method.declaringType().name() + "." + method.name() + method.signature();
    }
}
