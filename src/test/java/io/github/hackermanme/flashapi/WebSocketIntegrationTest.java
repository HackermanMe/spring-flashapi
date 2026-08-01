package io.github.hackermanme.flashapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    @Order(1)
    void connectAndSubscribe() throws Exception {
        var latch = new CountDownLatch(1);
        var messages = new CopyOnWriteArrayList<Map<String, Object>>();

        var client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage msg) throws Exception {
                messages.add(mapper.readValue(msg.getPayload(), Map.class));
                latch.countDown();
            }
        }, new WebSocketHttpHeaders(), URI.create("ws://localhost:" + port + "/api/ws")).get(5, TimeUnit.SECONDS);

        assertTrue(session.isOpen());

        session.sendMessage(new TextMessage("""
            {"action": "subscribe", "topic": "/topic/entities"}
        """));

        // Create a product via REST to trigger event
        var http = java.net.http.HttpClient.newHttpClient();
        var request = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/products"))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("""
                    {"name": "WS-Test", "price": 10.0, "stock": 1}
                """))
                .build();
        var response = http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response.statusCode());

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Should receive WebSocket event");
        assertEquals(1, messages.size());

        Map<String, Object> event = messages.get(0);
        assertEquals("ENTITY_CREATED", event.get("type"));
        assertEquals("Product", event.get("entity"));
        assertNotNull(event.get("timestamp"));
        assertNotNull(event.get("data"));

        session.close();
    }

    @Test
    @Order(2)
    void subscribeToSpecificEntity() throws Exception {
        var productLatch = new CountDownLatch(1);
        var productMessages = new CopyOnWriteArrayList<Map<String, Object>>();

        var client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage msg) throws Exception {
                productMessages.add(mapper.readValue(msg.getPayload(), Map.class));
                productLatch.countDown();
            }
        }, new WebSocketHttpHeaders(), URI.create("ws://localhost:" + port + "/api/ws")).get(5, TimeUnit.SECONDS);

        session.sendMessage(new TextMessage("""
            {"action": "subscribe", "topic": "/topic/product"}
        """));

        Thread.sleep(100);

        var http = java.net.http.HttpClient.newHttpClient();
        var request = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/products"))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("""
                    {"name": "WS-Entity-Test", "price": 20.0, "stock": 2}
                """))
                .build();
        http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertTrue(productLatch.await(5, TimeUnit.SECONDS), "Should receive entity-specific event");
        assertEquals("ENTITY_CREATED", productMessages.get(0).get("type"));
        assertEquals("Product", productMessages.get(0).get("entity"));

        session.close();
    }

    @Test
    @Order(3)
    void unsubscribeStopsEvents() throws Exception {
        var messages = new CopyOnWriteArrayList<Map<String, Object>>();
        var latch = new CountDownLatch(1);

        var client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage msg) throws Exception {
                messages.add(mapper.readValue(msg.getPayload(), Map.class));
                latch.countDown();
            }
        }, new WebSocketHttpHeaders(), URI.create("ws://localhost:" + port + "/api/ws")).get(5, TimeUnit.SECONDS);

        session.sendMessage(new TextMessage("""
            {"action": "subscribe", "topic": "/topic/entities"}
        """));
        Thread.sleep(100);

        session.sendMessage(new TextMessage("""
            {"action": "unsubscribe", "topic": "/topic/entities"}
        """));
        Thread.sleep(100);

        var http = java.net.http.HttpClient.newHttpClient();
        var request = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/products"))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("""
                    {"name": "WS-NoEvent", "price": 5.0, "stock": 1}
                """))
                .build();
        http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertFalse(latch.await(2, TimeUnit.SECONDS), "Should NOT receive event after unsubscribe");
        assertTrue(messages.isEmpty());

        session.close();
    }

    @Test
    @Order(4)
    void updateEventBroadcasted() throws Exception {
        var latch = new CountDownLatch(1);
        var messages = new CopyOnWriteArrayList<Map<String, Object>>();

        var client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage msg) throws Exception {
                messages.add(mapper.readValue(msg.getPayload(), Map.class));
                latch.countDown();
            }
        }, new WebSocketHttpHeaders(), URI.create("ws://localhost:" + port + "/api/ws")).get(5, TimeUnit.SECONDS);

        session.sendMessage(new TextMessage("""
            {"action": "subscribe", "topic": "/topic/entities"}
        """));
        Thread.sleep(100);

        var http = java.net.http.HttpClient.newHttpClient();
        var updateReq = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/products/1"))
                .header("Content-Type", "application/json")
                .PUT(java.net.http.HttpRequest.BodyPublishers.ofString("""
                    {"name": "Updated-WS"}
                """))
                .build();
        http.send(updateReq, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Should receive update event");
        assertEquals("ENTITY_UPDATED", messages.get(0).get("type"));

        session.close();
    }

    @Test
    @Order(5)
    void deleteEventBroadcasted() throws Exception {
        var latch = new CountDownLatch(1);
        var messages = new CopyOnWriteArrayList<Map<String, Object>>();

        var client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage msg) throws Exception {
                messages.add(mapper.readValue(msg.getPayload(), Map.class));
                latch.countDown();
            }
        }, new WebSocketHttpHeaders(), URI.create("ws://localhost:" + port + "/api/ws")).get(5, TimeUnit.SECONDS);

        session.sendMessage(new TextMessage("""
            {"action": "subscribe", "topic": "/topic/entities"}
        """));
        Thread.sleep(100);

        var http = java.net.http.HttpClient.newHttpClient();
        var deleteReq = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/products/1"))
                .DELETE()
                .build();
        http.send(deleteReq, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Should receive delete event");
        assertEquals("ENTITY_DELETED", messages.get(0).get("type"));

        session.close();
    }
}
