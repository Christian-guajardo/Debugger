package timetravel;

import com.sun.jdi.*;
import java.util.*;

/**
 * TimelineManager amélioré
 * - Track AUTOMATIQUEMENT toutes les variables trouvées
 * - Enregistre tous les appels de méthodes
 * - Capture la sortie du programme
 */
public class TimelineManager {
    private List<ExecutionSnapshot> timeline;
    private int currentSnapshotIndex;
    private int nextSnapshotId;

    // Tracking automatique de TOUTES les variables
    private Map<String, VariableTracker> allVariableTrackers;

    // Enregistrement de tous les appels de méthodes
    private List<MethodCallRecord> allMethodCalls;

    // Capture de la sortie du programme
    private StringBuilder programOutput;

    private TimeTravelCallback callback;

    public void startTrackingVariable(String variableName, String currentValue) {
        System.out.println("Starting tracking variable " + variableName);
    }

    public interface TimeTravelCallback {
        void restoreSnapshot(ExecutionSnapshot snapshot);
    }

    public TimelineManager() {
        this.timeline = new ArrayList<>();
        this.currentSnapshotIndex = -1;
        this.nextSnapshotId = 0;
        this.allVariableTrackers = new HashMap<>();
        this.allMethodCalls = new ArrayList<>();
        this.programOutput = new StringBuilder();
    }

    /**
     * Enregistre un snapshot avec tracking automatique
     */
    public ExecutionSnapshot recordSnapshot(Location location, ThreadReference thread) {
        try {
            // Créer le snapshot avec la sortie du programme actuelle
            ExecutionSnapshot snapshot = new ExecutionSnapshot(
                    nextSnapshotId++,
                    location,
                    thread,
                    programOutput.toString()
            );

            timeline.add(snapshot);
            currentSnapshotIndex = timeline.size() - 1;

            // AUTOMATIQUE : Tracker toutes les variables trouvées
            autoTrackVariables(snapshot);

            // AUTOMATIQUE : Enregistrer l'appel de méthode
            recordMethodCall(snapshot);

            return snapshot;

        } catch (Exception e) {
            System.err.println("Error recording snapshot: " + e.getMessage());
            return null;
        }
    }

    /**
     * Ajoute du texte à la sortie du programme
     */
    public void appendProgramOutput(String text) {
        programOutput.append(text);
    }

    /**
     * Track automatiquement toutes les variables du snapshot
     */
    private void autoTrackVariables(ExecutionSnapshot snapshot) {
        Map<String, String> vars = snapshot.getVariables();

        for (Map.Entry<String, String> entry : vars.entrySet()) {
            String varName = entry.getKey();
            String varValue = entry.getValue();

            // Créer un tracker si c'est la première fois qu'on voit cette variable
            if (!allVariableTrackers.containsKey(varName)) {
                allVariableTrackers.put(varName, new VariableTracker(varName, varValue));
                allVariableTrackers.get(varName).initializeVariable(snapshot);
            }

            // Vérifier si la variable a changé
            allVariableTrackers.get(varName).checkForModification(varValue, snapshot);
        }
    }

    /**
     * Enregistre un appel de méthode
     */
    private void recordMethodCall(ExecutionSnapshot snapshot) {
        MethodCallRecord call = new MethodCallRecord(
                snapshot.getSnapshotId(),
                snapshot.getMethodName(),
                snapshot.getSourceFile(),
                snapshot.getLineNumber()
        );
        allMethodCalls.add(call);
    }

    /**
     * Récupère l'historique complet d'une variable
     */
    public List<VariableModification> getVariableHistory(String variableName) {
        VariableTracker tracker = allVariableTrackers.get(variableName);
        return tracker != null ? tracker.getModifications() : new ArrayList<>();
    }

    /**
     * Récupère tous les noms de variables trackées
     */
    public Set<String> getAllTrackedVariableNames() {
        return new HashSet<>(allVariableTrackers.keySet());
    }

    /**
     * Récupère toutes les variables avec leurs modifications
     */
    public Map<String, List<VariableModification>> getAllVariablesWithHistory() {
        Map<String, List<VariableModification>> result = new HashMap<>();

        for (Map.Entry<String, VariableTracker> entry : allVariableTrackers.entrySet()) {
            String varName = entry.getKey();
            List<VariableModification> history = entry.getValue().getModifications();

            // N'inclure que les variables qui ont été modifiées
            if (!history.isEmpty()) {
                result.put(varName, history);
            }
        }

        return result;
    }

    /**
     * Récupère tous les appels de méthodes
     */
    public List<MethodCallRecord> getAllMethodCalls() {
        return new ArrayList<>(allMethodCalls);
    }

    /**
     * Récupère les appels à une méthode spécifique
     */
    public List<MethodCallRecord> getCallsToMethod(String methodName) {
        List<MethodCallRecord> result = new ArrayList<>();

        for (MethodCallRecord call : allMethodCalls) {
            if (call.getMethodName().equals(methodName)) {
                result.add(call);
            }
        }

        return result;
    }

    /**
     * Voyage dans le temps vers un snapshot
     */
    public boolean travelToSnapshot(int snapshotId) {
        for (int i = 0; i < timeline.size(); i++) {
            if (timeline.get(i).getSnapshotId() == snapshotId) {
                currentSnapshotIndex = i;
                ExecutionSnapshot snapshot = timeline.get(i);

                if (callback != null) {
                    callback.restoreSnapshot(snapshot);
                }

                return true;
            }
        }
        return false;
    }

    // Getters standards
    public List<ExecutionSnapshot> getTimeline() { return new ArrayList<>(timeline); }
    public int getCurrentSnapshotIndex() { return currentSnapshotIndex; }
    public ExecutionSnapshot getCurrentSnapshot() {
        return currentSnapshotIndex >= 0 ? timeline.get(currentSnapshotIndex) : null;
    }
    public void setCallback(TimeTravelCallback callback) { this.callback = callback; }
    public int getTimelineSize() { return timeline.size(); }

    /**
     * Classe interne pour tracker une variable
     */
    private class VariableTracker {
        private final String variableName;
        private String lastValue;
        private List<VariableModification> modifications;

        public VariableTracker(String variableName, String initialValue) {
            this.variableName = variableName;
            this.lastValue = initialValue;
            this.modifications = new ArrayList<>();
        }

        public void initializeVariable(ExecutionSnapshot snapshot){
            VariableModification mod = new VariableModification(
                    variableName,
                    "_",
                    lastValue,
                    snapshot.getSnapshotId(),
                    snapshot.getLineNumber(),
                    snapshot.getMethodName()
            );

            modifications.add(mod);
        }

        public void checkForModification(String newValue, ExecutionSnapshot snapshot) {
            if (!newValue.equals(lastValue)) {
                VariableModification mod = new VariableModification(
                        variableName,
                        lastValue,
                        newValue,
                        snapshot.getSnapshotId(),
                        snapshot.getLineNumber(),
                        snapshot.getMethodName()
                );

                modifications.add(mod);
                lastValue = newValue;
            }
        }

        public List<VariableModification> getModifications() {
            return new ArrayList<>(modifications);
        }
    }

    /**
     * Classe pour enregistrer un appel de méthode
     */
    public static class MethodCallRecord {
        private final int snapshotId;
        private final String methodName;
        private final String sourceFile;
        private final int lineNumber;

        public MethodCallRecord(int snapshotId, String methodName, String sourceFile, int lineNumber) {
            this.snapshotId = snapshotId;
            this.methodName = methodName;
            this.sourceFile = sourceFile;
            this.lineNumber = lineNumber;
        }

        public int getSnapshotId() { return snapshotId; }
        public String getMethodName() { return methodName; }
        public String getSourceFile() { return sourceFile; }
        public int getLineNumber() { return lineNumber; }

        @Override
        public String toString() {
            return String.format("%s() at %s:%d", methodName, sourceFile, lineNumber);
        }
    }
}