package com.researchassistant.events;

import java.time.Duration;
import java.util.List;

public interface WorkbenchEventPublisher {

    WorkbenchEvent publish(WorkbenchEvent event);

    List<WorkbenchEvent> readRunEventsAfter(String runId, String lastEventId);

    default List<WorkbenchEvent> readRunEventsAfter(String runId, String lastEventId, Duration wait) {
        return readRunEventsAfter(runId, lastEventId);
    }
}
