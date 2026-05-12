package com.researchassistant.events;

public final class WorkbenchRunEventStream {

    private WorkbenchRunEventStream() {
    }

    public static boolean isTerminal(WorkbenchEventType eventType) {
        return eventType == WorkbenchEventType.RUN_COMPLETED
                || eventType == WorkbenchEventType.RUN_FAILED
                || eventType == WorkbenchEventType.RUN_CANCELLED;
    }
}
