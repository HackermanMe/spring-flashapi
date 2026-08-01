package io.github.hackermanme.flashapi.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import io.github.hackermanme.flashapi.service.FlashEventBroadcaster;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class FlashWebSocketHandler extends TextWebSocketHandler implements FlashEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(FlashWebSocketHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, Set<WebSocketSession>> subscriptions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.debug("FlashAPI WebSocket: client connected [{}]", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            Map<String, Object> msg = mapper.readValue(message.getPayload(), Map.class);
            String action = (String) msg.get("action");
            String topic = (String) msg.get("topic");

            if (topic == null || topic.isEmpty()) return;

            if ("subscribe".equals(action)) {
                subscriptions.computeIfAbsent(topic, k -> new CopyOnWriteArraySet<>()).add(session);
            } else if ("unsubscribe".equals(action)) {
                Set<WebSocketSession> sessions = subscriptions.get(topic);
                if (sessions != null) {
                    sessions.remove(session);
                }
            }
        } catch (Exception e) {
            log.debug("FlashAPI WebSocket: invalid message from [{}]", session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        for (Set<WebSocketSession> sessions : subscriptions.values()) {
            sessions.remove(session);
        }
    }

    @Override
    public void broadcast(String entity, String eventType, Map<String, Object> data) {
        Map<String, Object> message = Map.of(
                "type", eventType,
                "entity", entity,
                "data", data != null ? data : Map.of(),
                "timestamp", Instant.now().toString()
        );

        String payload;
        try {
            payload = mapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("FlashAPI WebSocket: failed to serialize event", e);
            return;
        }

        TextMessage textMessage = new TextMessage(payload);
        Set<WebSocketSession> globalSubs = subscriptions.get("/topic/entities");
        Set<WebSocketSession> entitySubs = subscriptions.get("/topic/" + entity.toLowerCase());

        sendToAll(globalSubs, textMessage);
        sendToAll(entitySubs, textMessage);
    }

    private void sendToAll(Set<WebSocketSession> sessions, TextMessage message) {
        if (sessions == null) return;
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (IOException e) {
                    log.debug("FlashAPI WebSocket: failed to send to [{}], removing", session.getId());
                    removeSession(session);
                }
            } else {
                removeSession(session);
            }
        }
    }

    private void removeSession(WebSocketSession session) {
        for (Set<WebSocketSession> sessions : subscriptions.values()) {
            sessions.remove(session);
        }
    }
}
