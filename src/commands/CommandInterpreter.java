package commands;

import java.util.*;

public class CommandInterpreter {
    private Map<String, CommandFactory> commandFactories;

    public CommandInterpreter() {
        this.commandFactories = new HashMap<>();
        registerCommands();
    }

    private void registerCommands() {
        // Navigation
        commandFactories.put("step", args -> new StepCommand());
        commandFactories.put("step-over", args -> new StepOverCommand());
        commandFactories.put("continue", args -> new ContinueCommand());

        // Inspection
        commandFactories.put("frame", args -> new FrameCommand());
        commandFactories.put("temporaries", args -> new TemporariesCommand());
        commandFactories.put("stack", args -> new StackCommand());
        commandFactories.put("receiver", args -> new ReceiverCommand());
        commandFactories.put("sender", args -> new SenderCommand());
        commandFactories.put("receiver-variables", args -> new ReceiverVariablesCommand());
        commandFactories.put("method", args -> new MethodCommand());
        commandFactories.put("arguments", args -> new ArgumentsCommand());

        // Variables
        commandFactories.put("print-var", args -> {
            if (args.length < 1) {
                throw new IllegalArgumentException("print-var requires variable name");
            }
            return new PrintVarCommand(args[0]);
        });

        // Breakpoints
        commandFactories.put("break", args -> {
            if (args.length < 2) {
                throw new IllegalArgumentException("break requires fileName and lineNumber");
            }
            return new BreakCommand(args[0], Integer.parseInt(args[1]));
        });

        commandFactories.put("break-once", args -> {
            if (args.length < 2) {
                throw new IllegalArgumentException("break-once requires fileName and lineNumber");
            }
            return new BreakOnceCommand(args[0], Integer.parseInt(args[1]));
        });

        commandFactories.put("break-on-count", args -> {
            if (args.length < 3) {
                throw new IllegalArgumentException("break-on-count requires fileName, lineNumber, and count");
            }
            return new BreakOnCountCommand(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]));
        });

        commandFactories.put("breakpoints", args -> new BreakpointsCommand());

        commandFactories.put("break-before-method-call", args -> {
            if (args.length < 1) {
                throw new IllegalArgumentException("break-before-method-call requires method name");
            }
            return new BreakBeforeMethodCallCommand(args[0]);
        });
    }

    public Command parse(String input) throws Exception {
        String[] parts = input.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            throw new IllegalArgumentException("Empty command");
        }

        String commandName = parts[0];
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        CommandFactory factory = commandFactories.get(commandName);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown command: " + commandName);
        }

        return factory.create(args);
    }

    public Set<String> getAvailableCommands() {
        return commandFactories.keySet();
    }
}