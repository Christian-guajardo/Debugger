package commands;

import timetravel.TimelineManager;
import timetravel.VariableModification;
import java.util.List;

/**
 * Commande pour commencer à suivre une variable
 * Usage: follow-var x
 */
public class FollowVariableCommand implements Command {
    private String variableName;

    public FollowVariableCommand(String variableName) {
        this.variableName = variableName;
    }

    @Override
    public CommandResult execute(DebuggerState state) throws Exception {
        TimelineManager timeline = state.getTimelineManager();

        if (timeline == null) {
            return CommandResult.error("Timeline not available");
        }

        // Obtenir la valeur actuelle de la variable
        String currentValue = getVariableCurrentValue(state, variableName);

        if (currentValue == null) {
            return CommandResult.error("Variable '" + variableName + "' not found");
        }

        // Commencer à suivre la variable
        timeline.startTrackingVariable(variableName, currentValue);

        return CommandResult.success("Now tracking variable: " + variableName);
    }

    private String getVariableCurrentValue(DebuggerState state, String varName) {
        try {
            if (state.getContext() == null || state.getContext().getCurrentFrame() == null) {
                return null;
            }

            // Chercher dans les variables locales
            for (models.Variable var : state.getContext().getCurrentFrame().getTemporaries()) {
                if (var.getName().equals(varName)) {
                    return var.getValueAsString();
                }
            }
        } catch (Exception e) {
            // Ignorer
        }
        return null;
    }
}