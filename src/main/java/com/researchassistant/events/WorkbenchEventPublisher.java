package com.researchassistant.events;

import java.util.List;

public interface WorkbenchEventPublisher {

    WorkbenchEvent publish(WorkbenchEvent event);

    List<WorkbenchEvent> readRunEventsAfter(String runId, String lastEventId);
}
