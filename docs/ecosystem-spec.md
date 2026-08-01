# FlashAPI HTTP Specification v1

> **Ce document est le CONTRAT que toute implémentation serveur (Java, Python, Node, etc.) doit respecter et que le SDK client (`@flashapi/client`) cible.**

---

## Architecture de l'écosystème

```
┌─────────────────────────────────────────────────────────────┐
│                    FLASHAPI ECOSYSTEM                         │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Couche client (npm)                                         │
│  ┌────────────────────────────────┐                          │
│  │  @flashapi/client              │  ← UN seul SDK           │
│  │  (JS/TS, framework-agnostic)  │     pour tous les         │
│  └────────────────────────────────┘     backends             │
│                                                              │
│  Couche spécification                                        │
│  ┌────────────────────────────────┐                          │
│  │  FlashAPI HTTP Spec v1         │  ← CE DOCUMENT           │
│  │  (le contrat)                  │                          │
│  └────────────────────────────────┘                          │
│                                                              │
│  Couche serveur (implémentations)                            │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐         │
│  │ spring-      │ │ flashapi     │ │ express-     │         │
│  │ flashapi     │ │ (Python)     │ │ flashapi     │         │
│  │ (Java)       │ │              │ │ (Node.js)    │         │
│  └──────────────┘ └──────────────┘ └──────────────┘         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

Le SDK client ne parle ni Java ni Python — il parle HTTP. Tant que le backend respecte cette spec (mêmes URLs, mêmes formats JSON, mêmes headers), le client fonctionne.

---

## URL Patterns

| Operation | Method | URL |
|-----------|--------|-----|
| List (paginated) | GET | `/api/{entities}` |
| List deleted only | GET | `/api/{entities}?deleted=true` |
| Get by ID | GET | `/api/{entities}/{id}` |
| Create | POST | `/api/{entities}` |
| Bulk create | POST | `/api/{entities}/bulk` |
| Bulk update | PUT | `/api/{entities}/bulk` |
| Bulk delete | DELETE | `/api/{entities}/bulk` |
| Update | PUT | `/api/{entities}/{id}` |
| Delete (soft or hard) | DELETE | `/api/{entities}/{id}` |
| Restore | POST | `/api/{entities}/{id}/restore` |
| Export | GET | `/api/{entities}/export?format={csv|xlsx|pdf}` |
| Search | GET | `/api/{entities}?search={term}` |
| Filter | GET | `/api/{entities}?{fieldName}.{operator}={value}` |
| History (audit) | GET | `/api/{entities}/{id}/history` |

`{entities}` = nom de l'entité en camelCase, pluriel (ex: `eleves`, `orders`, `webhookItems`)

---

## Response Formats

### List Response

```json
{
  "data": [ {...}, {...} ],
  "meta": {
    "page": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3
  }
}
```

### Single Entity Response

```json
{
  "data": { "id": 1, "nom": "Dupont", ... }
}
```

### Create Response
- Status: `201 Created`
- Body: `{ "data": { ... } }`

### Update Response
- Status: `200 OK`
- Body: `{ "data": { ... } }`

### Delete Response
- Status: `204 No Content`
- Body: vide

### Restore Response
- Status: `204 No Content`
- Body: vide

### Error Response

```json
{
  "error": "Description du problème",
  "status": 404
}
```

---

## Query Parameters

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | int | `0` | Numéro de page (0-indexed) |
| `size` | int | `20` | Éléments par page |
| `sort` | string | — | Champ de tri (ex: `nom,asc` ou `createdAt,desc`) |
| `search` | string | — | Recherche full-text sur tous les champs String |
| `deleted` | boolean | `false` | `true` = afficher uniquement les soft-deleted |
| `{field}` | any | — | Filtre par valeur exacte du champ (alias de `{field}.eq`) |
| `{field}.{op}` | any | — | Filtre avec opérateur (voir ci-dessous) |

### Filter Operators

Format : `?{fieldName}.{operator}={value}`

| Operator | Description | Example |
|----------|-------------|---------|
| `eq` | Égalité exacte (défaut) | `?status.eq=active` ou `?status=active` |
| `neq` | Différent de | `?status.neq=archived` |
| `gt` | Strictement supérieur | `?price.gt=100` |
| `gte` | Supérieur ou égal | `?age.gte=18` |
| `lt` | Strictement inférieur | `?price.lt=50` |
| `lte` | Inférieur ou égal | `?age.lte=65` |
| `contains` | Contient (case-insensitive) | `?name.contains=dupont` |
| `startswith` | Commence par (case-insensitive) | `?name.startswith=du` |
| `endswith` | Se termine par (case-insensitive) | `?email.endswith=@gmail.com` |
| `isnull` | Est null (`true`) ou non-null (`false`) | `?deletedAt.isnull=true` |
| `in` | Dans une liste (séparé par virgule) | `?status.in=active,pending` |

Si aucun opérateur n'est spécifié, `eq` est utilisé par défaut.

---

## Webhook Contract

### Delivery
- Method: POST
- Content-Type: `application/json`

### Headers

| Header | Value |
|--------|-------|
| `X-FlashAPI-Event` | `CREATE`, `UPDATE`, `DELETE` |
| `X-FlashAPI-Entity` | Nom de l'entité (ex: `Eleve`) |

### Payload

```json
{
  "event": "CREATE",
  "entity": "Eleve",
  "entityId": "42",
  "data": { ... },
  "timestamp": "2026-07-14T15:30:00Z"
}
```

---

## WebSocket Contract (temps réel frontend)

### Connection endpoint
- URL: `/api/ws` (raw WebSocket, pas de lib tierce requise côté client)
- Le client se connecte avec `new WebSocket("ws://host/api/ws")` — fonctionne en natif dans tout navigateur et runtime JS/TS.

### Subscribe/Unsubscribe (client → serveur)

```json
{"action": "subscribe", "topic": "/topic/entities"}
{"action": "subscribe", "topic": "/topic/eleves"}
{"action": "unsubscribe", "topic": "/topic/eleves"}
```

| Topic | Description |
|-------|-------------|
| `/topic/entities` | Tous les événements CRUD (toutes entités) |
| `/topic/{entity}` | Événements pour une entité spécifique (nom en minuscule) |

### Event messages (serveur → client)

```json
{
  "type": "ENTITY_CREATED",
  "entity": "Eleve",
  "data": { ... },
  "timestamp": "2026-07-14T15:30:00Z"
}
```

Event types: `ENTITY_CREATED`, `ENTITY_UPDATED`, `ENTITY_DELETED`, `ENTITY_RESTORED`

### Comportement
- Le serveur ne push rien tant que le client n'a pas souscrit à au moins un topic
- Un client peut souscrire à plusieurs topics simultanément
- La déconnexion du client nettoie automatiquement toutes ses souscriptions
- Les messages sont envoyés à tous les subscribers du topic concerné
- Pas de garantie d'ordre (best-effort delivery, pas de file d'attente)

---

## Bulk Operations

### Bulk Create

```
POST /api/{entities}/bulk
Content-Type: application/json

[{...}, {...}, {...}]
```

Response: `201 Created`

```json
{
  "data": [{...}, {...}, {...}],
  "meta": { "total": 3, "succeeded": 3, "failed": 0 }
}
```

### Bulk Update

```
PUT /api/{entities}/bulk
Content-Type: application/json

[{"id": 1, "name": "Updated1"}, {"id": 2, "name": "Updated2"}]
```

Chaque objet DOIT contenir le champ identifiant (id ou lookup field). Les autres champs sont mis à jour.

Response: `200 OK`

```json
{
  "data": [{...}, {...}],
  "meta": { "total": 2, "succeeded": 2, "failed": 0 }
}
```

### Bulk Delete

```
DELETE /api/{entities}/bulk
Content-Type: application/json

[1, 2, 3]
```

Le body est un tableau d'identifiants (id ou lookup field values).

Response: `200 OK`

```json
{
  "data": [],
  "meta": { "total": 3, "succeeded": 3, "failed": 0 }
}
```

---

## Export

```
GET /api/{entities}/export?format=xlsx
```

Formats supportés : `csv`, `xlsx`, `pdf`

Response: binary file avec `Content-Disposition: attachment; filename="{entities}.{format}"`

---

## Field Visibility Rules

| Annotation (Java) | Decorator (Python) | In Response | In Create/Update | In Export |
|---|---|---|---|---|
| (none) | (none) | Yes | Yes | Yes |
| `@FlashReadOnly` | `readonly=True` | Yes | No | Yes |
| `@FlashWriteOnly` | `writeonly=True` | No | Yes | No |
| `@FlashHidden` | `hidden=True` | No | No | No |
| `@FlashExportExclude` | `export_exclude=True` | Yes | Yes | No |

Combinaisons : les restrictions se cumulent (la plus restrictive gagne).

---

## Dashboard (Monitoring)

### Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `{dashboardPath}` | GET | HTML dashboard UI (auto-refresh 5s) |
| `{dashboardPath}/metrics.json` | GET | Raw metrics as JSON |

Default `dashboardPath`: `/api/dashboard`

### Configuration (Java)

```yaml
flashapi:
  dashboard:
    enabled: true
    path: /api/dashboard
    role: ADMIN
```

### Configuration (Python)

```python
app.config["FLASHAPI_DASHBOARD_ENABLED"] = True
app.config["FLASHAPI_DASHBOARD_PATH"] = "/api/dashboard"
app.config["FLASHAPI_DASHBOARD_ROLE"] = "ADMIN"
```

### Security Behavior

| Condition | Access |
|-----------|--------|
| No auth framework on classpath | Open (dev mode) |
| Auth present, user has required role | Allowed |
| Auth present, user lacks role | `403 Forbidden` |

### Metrics JSON Format

```json
{
  "generatedAt": "2026-07-19T15:30:00Z",
  "uptimeSeconds": 3600,
  "entities": {
    "Eleve": {
      "name": "Eleve",
      "count": 342,
      "softDelete": true,
      "auditEnabled": true,
      "webhookEnabled": true,
      "rateLimited": false,
      "multiTenant": false,
      "operations": {
        "CREATE": 120,
        "READ": 180,
        "UPDATE": 30,
        "DELETE": 12
      }
    }
  },
  "totals": {
    "creates": 342,
    "reads": 1204,
    "updates": 210,
    "deletes": 67,
    "searches": 185,
    "exports": 12,
    "bulkOps": 5,
    "total": 2025
  },
  "webhooks": {
    "sent": 518,
    "failed": 3,
    "retries": 12,
    "targetUrls": ["http://localhost:9090/webhooks"]
  },
  "recentEvents": [
    {
      "timestamp": "2026-07-19T15:29:58Z",
      "operation": "CREATE",
      "entity": "Eleve",
      "entityId": "42",
      "status": "OK"
    }
  ]
}
```

### Auto-Discovery

The dashboard MUST auto-detect per entity:
- Whether webhooks are enabled
- Whether audit is enabled
- Whether soft-delete is enabled
- Whether rate-limiting is enabled
- Whether multi-tenancy is enabled

No manual registration. Adding a webhook annotation to an entity makes it appear in the dashboard on next restart.

---

## Audit Trail

### Endpoints

| Operation | Method | URL |
|-----------|--------|-----|
| Get history | GET | `/api/{entities}/{id}/history` |

### Response Format

```json
{
  "data": [
    {
      "action": "CREATE",
      "entityType": "Eleve",
      "entityId": "42",
      "timestamp": "2026-07-19T10:00:00Z",
      "performedBy": "admin",
      "changes": null
    },
    {
      "action": "UPDATE",
      "entityType": "Eleve",
      "entityId": "42",
      "timestamp": "2026-07-19T11:30:00Z",
      "performedBy": "admin",
      "changes": {
        "nom": {"from": "Dupont", "to": "Martin"}
      }
    }
  ]
}
```

### Configuration (Java)

```yaml
flashapi:
  audit:
    enabled: true
    table-name: flash_audit_log
```

### Configuration (Python)

```python
app.config["FLASHAPI_AUDIT_ENABLED"] = True
app.config["FLASHAPI_AUDIT_TABLE"] = "flash_audit_log"
```

---

## Rate Limiting

### Behavior

When rate limit is exceeded: `429 Too Many Requests`

```json
{
  "error": "Rate limit exceeded",
  "status": 429,
  "retryAfter": 60
}
```

### Headers on every response

| Header | Value |
|--------|-------|
| `X-RateLimit-Limit` | Max requests per window |
| `X-RateLimit-Remaining` | Remaining requests |
| `X-RateLimit-Reset` | Seconds until window resets |

---

## Conformance Checklist

Une implémentation est "FlashAPI-conforme" si elle respecte :

### Core (obligatoire)
- [ ] URL patterns identiques
- [ ] Format de réponse `{ data, meta }` pour les listes
- [ ] Format de réponse `{ data }` pour les entités uniques
- [ ] Codes HTTP standards (201, 200, 204, 404, 400)
- [ ] Pagination via `page` et `size`
- [ ] Recherche full-text via `?search=`
- [ ] Filtres par champ via `?fieldName=value` (eq par défaut)
- [ ] Filtres avancés via `?fieldName.operator=value` (11 opérateurs)
- [ ] Respect des règles de visibilité des champs

### Soft Delete
- [ ] Soft delete transparent (exclu par défaut, `?deleted=true` pour voir)
- [ ] Endpoint restore `POST /{id}/restore`

### Webhooks
- [ ] Webhook headers `X-FlashAPI-Event` et `X-FlashAPI-Entity`
- [ ] Webhook payload avec `event`, `entity`, `entityId`, `data`, `timestamp`
- [ ] Retry avec backoff exponentiel
- [ ] Livraison asynchrone (ne bloque pas la réponse API)

### Audit
- [ ] Endpoint `GET /{id}/history`
- [ ] Actions enregistrées : CREATE, UPDATE, DELETE
- [ ] Changements (diff) enregistrés pour les UPDATE
- [ ] `performedBy` renseigné avec l'identité de l'utilisateur courant

### Bulk
- [ ] Bulk create `POST /bulk` avec tableau JSON → `201`
- [ ] Bulk update `PUT /bulk` avec tableau d'objets (chacun contenant l'id) → `200`
- [ ] Bulk delete `DELETE /bulk` avec tableau d'identifiants → `200`
- [ ] Réponse avec `meta.total`, `meta.succeeded` et `meta.failed`

### Export
- [ ] Endpoint `GET /export?format={csv|xlsx|pdf}`
- [ ] Header `Content-Disposition` correct

### Dashboard
- [ ] Endpoint HTML auto-refresh
- [ ] Endpoint JSON `/metrics.json`
- [ ] Auto-discovery des features par entité
- [ ] Sécurisation par rôle quand un framework auth est présent
- [ ] Métriques : opérations par entité, totaux, webhooks (sent/failed/retries), événements récents

### Rate Limiting
- [ ] Réponse `429` quand la limite est atteinte
- [ ] Headers `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`

---

## SDK Client Usage Preview

```javascript
import { createFlashClient } from '@flashapi/client';

const api = createFlashClient('http://localhost:8080');

// CRUD
const eleves = await api.eleves.list({ page: 0, size: 20 });
await api.eleves.create({ nom: 'Dupont', prenom: 'Jean' });
await api.eleves.update('abc-123', { prenom: 'Pierre' });
await api.eleves.delete('abc-123');

// Soft delete
const corbeille = await api.eleves.list({ deleted: true });
await api.eleves.restore('abc-123');

// Search & filter
const results = await api.eleves.list({ search: 'dupont' });
const garcons = await api.eleves.list({ sexe: 'MASCULIN' });

// Bulk
await api.eleves.bulkCreate([{...}, {...}, {...}]);

// Export
const blob = await api.eleves.export('xlsx');

// Real-time (WebSocket)
api.eleves.onCreated((eleve) => console.log('Nouveau:', eleve));
api.eleves.onDeleted((eleve) => console.log('Supprimé:', eleve));
```
