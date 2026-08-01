# WebSocket Real-Time Events

FlashAPI includes built-in WebSocket support for broadcasting CRUD events to connected clients in real time. No STOMP, no SockJS, no extra library — just raw WebSocket with JSON messages.

## Quick Start

### 1. Add the dependency

**Maven:**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

**Gradle:**

```kotlin
implementation("org.springframework.boot:spring-boot-starter-websocket")
```

That's it. FlashAPI auto-detects the WebSocket starter and enables real-time broadcasting.

### 2. Connect from a client

```javascript
const ws = new WebSocket("ws://localhost:8080/api/ws");

ws.onopen = () => {
  // Subscribe to all entity events
  ws.send(JSON.stringify({ action: "subscribe", topic: "/topic/entities" }));
};

ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  console.log(data.type, data.entity, data.data);
};
```

## Connection Endpoint

| Property | Value |
|----------|-------|
| URL | `{basePath}/ws` (default: `/api/ws`) |
| Protocol | Raw WebSocket (RFC 6455) |
| Encoding | JSON (UTF-8) |

## Subscribe / Unsubscribe

Clients send JSON messages to manage subscriptions:

```json
{"action": "subscribe", "topic": "/topic/entities"}
{"action": "subscribe", "topic": "/topic/product"}
{"action": "unsubscribe", "topic": "/topic/product"}
```

### Topics

| Topic | Description |
|-------|-------------|
| `/topic/entities` | All CRUD events across all entities |
| `/topic/{entity}` | Events for a specific entity (lowercase name) |

A client can subscribe to multiple topics simultaneously.

## Event Format (Server → Client)

```json
{
  "type": "ENTITY_CREATED",
  "entity": "Product",
  "data": {
    "id": 1,
    "name": "Laptop",
    "price": 999.99
  },
  "timestamp": "2026-07-14T15:30:00.123Z"
}
```

### Event Types

| Type | Trigger |
|------|---------|
| `ENTITY_CREATED` | POST (single or bulk) |
| `ENTITY_UPDATED` | PUT (single or bulk) |
| `ENTITY_DELETED` | DELETE (single or bulk, including soft delete) |
| `ENTITY_RESTORED` | POST `/{id}/restore` |

## Configuration

```yaml
flashapi:
  websocket:
    enabled: true  # default: true (auto-disabled if starter-websocket not on classpath)
```

To disable WebSocket even with the starter present:

```yaml
flashapi:
  websocket:
    enabled: false
```

## Behavior

- No events are pushed until the client subscribes to at least one topic
- Disconnection automatically cleans up all subscriptions for that client
- Messages are sent to all subscribers of the matching topic(s)
- Best-effort delivery — no queue, no persistence, no ordering guarantee
- A client subscribed to `/topic/entities` receives ALL events (superset of entity-specific topics)
- Events include the serialized entity data (visible fields only, respects `@FlashHidden`)

## Security

If you use Spring Security, make sure to allow the WebSocket endpoint:

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/ws").permitAll()  // or .authenticated()
            // ...
        )
        .build();
}
```

## Architecture

```
┌─────────────┐       ┌──────────────────┐       ┌─────────────────┐
│   Client    │──ws──▶│ FlashWebSocket   │◀──────│ GenericCrud     │
│  (browser)  │◀──────│    Handler       │       │    Service      │
└─────────────┘       └──────────────────┘       └─────────────────┘
                              │
                    FlashEventBroadcaster
                        (interface)
```

The `GenericCrudService` only depends on `FlashEventBroadcaster` (a simple interface in the core package). The WebSocket handler implements this interface. When `spring-boot-starter-websocket` is not on the classpath, no WebSocket beans are created and `GenericCrudService` operates without broadcasting — zero overhead.

## Custom Event Broadcasting

You can implement `FlashEventBroadcaster` to add your own event delivery (SSE, message queue, etc.):

```java
@Component
public class SseEventBroadcaster implements FlashEventBroadcaster {
    @Override
    public void broadcast(String entity, String eventType, Map<String, Object> data) {
        // Push to SSE emitters, Kafka, RabbitMQ, etc.
    }
}
```

If you register your own `FlashEventBroadcaster` bean, FlashAPI will use it instead of (or in addition to) WebSocket.
