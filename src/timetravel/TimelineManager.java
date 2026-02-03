package timetravel;

import com.sun.jdi.*;
import java.util.*;


public class TimelineManager {
    private List<ExecutionSnapshot> timeline;
    private int currentSnapshotIndex;
    private int nextSnapshotId;


    private Map<String, VariableTracker> trackedVariables;


    private TimeTravelCallback callback;

    public interface TimeTravelCallback {
        void restoreSnapshot(ExecutionSnapshot snapshot);
    }

    public TimelineManager() {
        this.timeline = new ArrayList<>();
        this.currentSnapshotIndex = -1;
        this.nextSnapshotId = 0;
        this.trackedVariables = new HashMap<>();
    }


    public ExecutionSnapshot recordSnapshot(Location location, ThreadReference thread) {
        try {
            ExecutionSnapshot snapshot = new ExecutionSnapshot(
                    nextSnapshotId++,
                    location,
                    thread
            );

            timeline.add(snapshot);
            currentSnapshotIndex = timeline.size() - 1;


            checkTrackedVariables(snapshot);

            return snapshot;

        } catch (Exception e) {
            System.err.println("Error recording snapshot: " + e.getMessage());
            return null;
        }
    }

    /**
     * suivre une variable
     */
    public void startTrackingVariable(String variableName, String currentValue) {
        if (!trackedVariables.containsKey(variableName)) {
            VariableTracker tracker = new VariableTracker(variableName, currentValue);
            trackedVariables.put(variableName, tracker);

            analyzeHistoryForVariable(tracker);
        }
    }

    /**
     * Arrête de suivre une variable
     * Cette methode risque est inutile
     */
    public void stopTrackingVariable(String variableName) {
        trackedVariables.remove(variableName);
    }

    /**
     * Récupère l'historique d'une variable
     */
    public List<VariableModification> getVariableHistory(String variableName) {
        VariableTracker tracker = trackedVariables.get(variableName);
        return tracker != null ? tracker.getModifications() : new ArrayList<>();
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

    /**
     * Vérifie si les variables ont changé
     */
    private void checkTrackedVariables(ExecutionSnapshot snapshot) {
        for (VariableTracker tracker : trackedVariables.values()) {
            String varName = tracker.getVariableName();

            if (snapshot.getVariables().containsKey(varName)) {
                String newValue = snapshot.getVariables().get(varName);
                tracker.checkForModification(newValue, snapshot);
            }
        }
    }

    /**
     * Analyse rétroactivement la timeline pour une variable
     */
    private void analyzeHistoryForVariable(VariableTracker tracker) {
        for (ExecutionSnapshot snapshot : timeline) {
            String varName = tracker.getVariableName();

            if (snapshot.getVariables().containsKey(varName)) {
                String value = snapshot.getVariables().get(varName);
                tracker.checkForModification(value, snapshot);
            }
        }
    }


    public List<ExecutionSnapshot> getTimeline() { return new ArrayList<>(timeline); }
    public int getCurrentSnapshotIndex() { return currentSnapshotIndex; }
    public ExecutionSnapshot getCurrentSnapshot() {
        return currentSnapshotIndex >= 0 ? timeline.get(currentSnapshotIndex) : null;
    }

    public void setCallback(TimeTravelCallback callback) {
        this.callback = callback;
    }

    public int getTimelineSize() {
        return timeline.size();
    }

    /**
     * Classe interne pour suivre une variable
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

        public String getVariableName() { return variableName; }
        public List<VariableModification> getModifications() {
            return new ArrayList<>(modifications);
        }
    }
}