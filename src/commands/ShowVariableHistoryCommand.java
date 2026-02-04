package commands;

import timetravel.TimelineManager;
import timetravel.VariableModification;
import java.util.List;

/**
 * Commande pour afficher l'historique d'une variable suivie
 * CORRIGÉ : Utilise l'historique filtré jusqu'au snapshot actuel
 * Usage: show-history x
 */
public class ShowVariableHistoryCommand implements Command {
    private String variableName;

    public ShowVariableHistoryCommand(String variableName) {
        this.variableName = variableName;
    }

    @Override
    public CommandResult execute(DebuggerState state) throws Exception {
        TimelineManager timeline = state.getTimelineManager();

        if (timeline == null) {
            return CommandResult.error("Timeline not available");
        }

        // CORRECTION : Utiliser l'historique filtré jusqu'au snapshot actuel
        List<VariableModification> history = timeline.getVariableHistoryUpToCurrent(variableName);

        if (history.isEmpty()) {
            return CommandResult.success("No modifications found for variable: " + variableName);
        }

        return CommandResult.success(
                "History for variable '" + variableName + "' (" + history.size() + " modifications):",
                history
        );
    }
}