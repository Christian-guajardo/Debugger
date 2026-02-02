package models;

import com.sun.jdi.Value;

public class Variable {
    private String name;
    private String type;
    private Value value;

    public Variable(String name, String type, Value value) {
        this.name = name;
        this.type = type;
        this.value = value;
    }

    public String getName() { return name; }
    public String getType() { return type; }
    public Value getValue() { return value; }

    public String getValueAsString() {
        if (value == null) return "null";
        return value.toString();
    }

    @Override
    public String toString() {
        return name + " (" + type + ") = " + getValueAsString();
    }
}
