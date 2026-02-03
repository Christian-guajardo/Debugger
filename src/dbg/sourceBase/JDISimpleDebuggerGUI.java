package dbg.sourceBase;

public class JDISimpleDebuggerGUI {
    public static void main(String[] args) throws Exception {
        ScriptableDebuggerGUI debuggerInstance = new ScriptableDebuggerGUI();
        debuggerInstance.attachTo(testTree.class);

    }
}