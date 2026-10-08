package com.supportpilot.agent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Per-request collector passed to tools through the ToolContext. */
public class AgentActions {

    private final List<AgentAction> actions = new CopyOnWriteArrayList<>();

    public void add(AgentAction action) {
        actions.add(action);
    }

    public List<AgentAction> list() {
        return List.copyOf(actions);
    }
}
