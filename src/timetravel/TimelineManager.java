package timetravel;

import com.sun.jdi.*;
import java.util.*;

public class TimelineManager {
    private List<ExecutionSnapshot> timeline;
    private int currentSnapshotIndex;
    private int nextSnapshotId;
    private Map<String, VariableTracker> allVariableTrackers;
    private List<MethodCallRecord> allMethodCalls;
    private StringBuilder programOutput;
    private TimeTravelCallback callback;
    private String lastMethodSignature = null;
    private int lastStackDepth = 0;



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

    // Crée un snapshot complet de l'état actuel et l'ajoute à la chronologie
    public ExecutionSnapshot recordSnapshot(Location location, ThreadReference thread) {
        try {
            ExecutionSnapshot snapshot = new ExecutionSnapshot(
                    nextSnapshotId++, location, thread, programOutput.toString());

            timeline.add(snapshot);
            currentSnapshotIndex = timeline.size() - 1;
            autoTrackVariables(snapshot);
            recordMethodCallIfNew(snapshot);
            return snapshot;
        } catch (Exception e) {
            return null;
        }
    }

    public void appendProgramOutput(String text) {
        programOutput.append(text);
    }

    private String extractMethodContext(ExecutionSnapshot snapshot) {
        if (snapshot.getCallStack() == null || snapshot.getCallStack().isEmpty()) {
            return snapshot.getMethodName();
        }
        String fullFrame = snapshot.getCallStack().get(0);
        int ligneIndex = fullFrame.indexOf(" ligne ");
        if (ligneIndex > 0) {
            return fullFrame.substring(0, ligneIndex).trim();
        }
        return fullFrame;
    }

    // Analyse les variables du snapshot pour détecter et enregistrer tout changement de valeur
    private void autoTrackVariables(ExecutionSnapshot snapshot) {
        Map<String, String> vars = snapshot.getVariables();
        String methodContext = extractMethodContext(snapshot);

        for (Map.Entry<String, String> entry : vars.entrySet()) {
            String varName = entry.getKey();
            String varValue = entry.getValue();
            String uniqueKey = varName + "@" + methodContext;

            if (!allVariableTrackers.containsKey(uniqueKey)) {
                allVariableTrackers.put(uniqueKey, new VariableTracker(varName, varValue, methodContext));
                allVariableTrackers.get(uniqueKey).initializeVariable(snapshot);
            }
            allVariableTrackers.get(uniqueKey).checkForModification(varValue, snapshot);
        }
    }

    // Enregistre un nouvel appel de méthode
    private void recordMethodCallIfNew(ExecutionSnapshot snapshot) {
        if (snapshot.getCallStack() == null) return;
        int currentStackDepth = snapshot.getCallStack().size();
        String currentMethodSignature = extractMethodContext(snapshot);
        if (lastMethodSignature == null || currentStackDepth > lastStackDepth) {
            MethodCallRecord call = new MethodCallRecord(
                    snapshot.getSnapshotId(),
                    snapshot.getMethodName(), //
                    snapshot.getSourceFile(),
                    snapshot.getLineNumber(),
                    currentMethodSignature
            );
            allMethodCalls.add(call);

            lastMethodSignature = currentMethodSignature;
        }
        lastStackDepth = currentStackDepth;
    }

    // Récupère l'historique des modifications d'une variable jusqu'au point actuel dans le temps
    public List<VariableModification> getVariableHistoryUpToCurrent(String variableName) {
        if (currentSnapshotIndex < 0) {
            return new ArrayList<>();
        }
        ExecutionSnapshot currentSnapshot = timeline.get(currentSnapshotIndex);
        return getVariableHistoryUpToSnapshot(variableName, currentSnapshot);
    }

    public List<VariableModification> getVariableHistoryUpToSnapshot(String variableName, ExecutionSnapshot upToSnapshot) {
        String methodContext = extractMethodContext(upToSnapshot);
        String uniqueKey = variableName + "@" + methodContext;
        VariableTracker tracker = allVariableTrackers.get(uniqueKey);

        if (tracker == null) {
            return new ArrayList<>();
        }

        List<VariableModification> filteredMods = new ArrayList<>();
        int maxSnapshotId = upToSnapshot.getSnapshotId();

        for (VariableModification mod : tracker.getModifications()) {

                filteredMods.add(mod);

        }
        return filteredMods;
    }

    public List<VariableModification> getVariableHistory(String variableName) {
        for (VariableTracker tracker : allVariableTrackers.values()) {
            if (tracker.getVariableName().equals(variableName)) {
                return tracker.getModifications();
            }
        }
        return new ArrayList<>();
    }

    public Set<String> getAllTrackedVariableNames() {
        Set<String> names = new HashSet<>();
        for (VariableTracker tracker : allVariableTrackers.values()) {
            names.add(tracker.getVariableName());
        }
        return names;
    }

    public int getTrackedVariablesWithModificationsCount() {
        int count = 0;
        Set<String> counted = new HashSet<>();

        for (VariableTracker tracker : allVariableTrackers.values()) {
            if (!counted.contains(tracker.getVariableName())) {
                if (!tracker.getModifications().isEmpty()) {
                    count++;
                    counted.add(tracker.getVariableName());
                }
            }
        }
        return count;
    }

    // Récupère l'historique complet de toutes les variables jusqu'à l'instant présent
    public Map<String, List<VariableModification>> getAllVariablesWithHistoryUpToCurrent() {
        if (currentSnapshotIndex < 0) {
            return new HashMap<>();
        }

        ExecutionSnapshot currentSnapshot = timeline.get(currentSnapshotIndex);
        Map<String, List<VariableModification>> result = new HashMap<>();
        int maxSnapshotId = currentSnapshot.getSnapshotId();

        for (Map.Entry<String, VariableTracker> entry : allVariableTrackers.entrySet()) {
            String varName = entry.getValue().getVariableName();
            List<VariableModification> filteredHistory = new ArrayList<>();

            for (VariableModification mod : entry.getValue().getModifications()) {
                if (mod.getSnapshotId() <= maxSnapshotId) {
                    filteredHistory.add(mod);
                }
            }

            if (!filteredHistory.isEmpty()) {
                if (result.containsKey(varName)) {
                    result.get(varName).addAll(filteredHistory);
                } else {
                    result.put(varName, filteredHistory);
                }
            }
        }
        return result;
    }

    public Map<String, List<VariableModification>> getAllVariablesWithHistory() {
        Map<String, List<VariableModification>> result = new HashMap<>();

        for (Map.Entry<String, VariableTracker> entry : allVariableTrackers.entrySet()) {
            String varName = entry.getValue().getVariableName();
            List<VariableModification> history = entry.getValue().getModifications();

            if (!history.isEmpty()) {
                if (result.containsKey(varName)) {
                    result.get(varName).addAll(history);
                } else {
                    result.put(varName, new ArrayList<>(history));
                }
            }
        }
        return result;
    }

    public List<MethodCallRecord> getAllMethodCallsUpToCurrent() {
        if (currentSnapshotIndex < 0) {
            return new ArrayList<>();
        }

        int maxSnapshotId = timeline.get(currentSnapshotIndex).getSnapshotId();
        List<MethodCallRecord> filtered = new ArrayList<>();

        for (MethodCallRecord call : allMethodCalls) {

                filtered.add(call);
            
        }
        return filtered;
    }

    public List<MethodCallRecord> getAllMethodCalls() {
        return new ArrayList<>(allMethodCalls);
    }

    public List<MethodCallRecord> getCallsToMethodUpToCurrent(String methodName) {
        List<MethodCallRecord> result = new ArrayList<>();
        for (MethodCallRecord call : getAllMethodCallsUpToCurrent()) {
            if (call.getMethodName().equals(methodName)) {
                result.add(call);
            }
        }
        return result;
    }

    public List<MethodCallRecord> getCallsToMethod(String methodName) {
        List<MethodCallRecord> result = new ArrayList<>();
        for (MethodCallRecord call : allMethodCalls) {
            if (call.getMethodName().equals(methodName)) {
                result.add(call);
            }
        }
        return result;
    }

    // Restaure un état passé correspondant à l'ID de snapshot donné
    public boolean travelToSnapshot(int snapshotId) {
        for (int i = 0; i < timeline.size(); i++) {
            if (timeline.get(i).getSnapshotId() == snapshotId) {
                currentSnapshotIndex = i;
                if (callback != null) {
                    callback.restoreSnapshot(timeline.get(i));
                }
                return true;
            }
        }
        return false;
    }

    public List<ExecutionSnapshot> getTimeline() {
        return new ArrayList<>(timeline);
    }

    public int getCurrentSnapshotIndex() {
        return currentSnapshotIndex;
    }

    public ExecutionSnapshot getCurrentSnapshot() {
        return currentSnapshotIndex >= 0 ? timeline.get(currentSnapshotIndex) : null;
    }

    public void setCallback(TimeTravelCallback callback) {
        this.callback = callback;
    }

    public int getTimelineSize() {
        return timeline.size();
    }

    private class VariableTracker {
        private final String variableName;
        private final String methodContext;
        private String lastValue;
        private List<VariableModification> modifications;

        public VariableTracker(String variableName, String initialValue, String methodContext) {
            this.variableName = variableName;
            this.methodContext = methodContext;
            this.lastValue = initialValue;
            this.modifications = new ArrayList<>();
        }

        public void initializeVariable(ExecutionSnapshot snapshot){
            modifications.add(new VariableModification(
                    variableName, "_", lastValue,
                    snapshot.getSnapshotId(), snapshot.getLineNumber(), snapshot.getMethodName()));
        }

        public void checkForModification(String newValue, ExecutionSnapshot snapshot) {
            if (!newValue.equals(lastValue)) {
                modifications.add(new VariableModification(
                        variableName, lastValue, newValue,
                        snapshot.getSnapshotId(), snapshot.getLineNumber(), snapshot.getMethodName()));
                lastValue = newValue;
            }
        }

        public List<VariableModification> getModifications() {
            return new ArrayList<>(modifications);
        }

        public String getVariableName() {
            return variableName;
        }
    }

    public static class MethodCallRecord {
        private final int snapshotId;
        private final String methodName;
        private final String sourceFile;
        private final int lineNumber;
        private final String fullSignature;

        public MethodCallRecord(int snapshotId, String methodName, String sourceFile,
                                int lineNumber, String fullSignature) {
            this.snapshotId = snapshotId;
            this.methodName = methodName;
            this.sourceFile = sourceFile;
            this.lineNumber = lineNumber;
            this.fullSignature = fullSignature;
        }

        public int getSnapshotId() { return snapshotId; }
        public String getMethodName() { return methodName; }
        public String getSourceFile() { return sourceFile; }
        public int getLineNumber() { return lineNumber; }
        public String getFullSignature() { return fullSignature; }

        @Override
        public String toString() {
            return String.format("%s at %s:%d", fullSignature, sourceFile, lineNumber);
        }
    }
}