package io.github.hackermanme.flashapi.service;

import java.util.Map;

public interface FlashEventBroadcaster {
    void broadcast(String entity, String eventType, Map<String, Object> data);
}
