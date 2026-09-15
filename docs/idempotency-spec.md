# Idempotency Keys — Spécification Technique

## Vue d'ensemble

**Problème :** Double-click, retry réseau, timeout client → requêtes dupliquées → création de doublons, double facturation, etc.

**Solution :** Header `Idempotency-Key` sur les requêtes mutables (POST, PUT, DELETE). Si la même clé arrive deux fois, la 2ᵉ requête retourne la même réponse que la 1ʳᵉ sans réexécuter l'opération.

**Inspiré de :** Stripe, GitHub, Adyen

---

## Comportement

### Requête avec clé d'idempotence

```http
POST /api/invoices
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json

{
  "amount": 150.00,
  "status": "draft"
}
```

**1ʳᵉ exécution :**
- FlashAPI vérifie : clé existe dans `flash_idempotency_keys` ?
- Non → traite la requête normalement
- Crée Invoice #123
- Stocke dans `flash_idempotency_keys` : clé + réponse (status 201, body)
- Retourne `201 Created` avec l'invoice

**2ᵉ exécution (retry, double-click) :**
- Même `Idempotency-Key` arrive
- FlashAPI vérifie : clé existe ?
- Oui → **retourne la réponse stockée** (201, même body)
- **Aucune 2ᵉ invoice créée**

---

## Schéma de base de données

### Table `flash_idempotency_keys`

```sql
CREATE TABLE flash_idempotency_keys (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    request_path VARCHAR(500) NOT NULL,
    request_method VARCHAR(10) NOT NULL,
    request_body TEXT,
    response_status INT NOT NULL,
    response_body TEXT,
    response_headers TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    INDEX idx_expires_at (expires_at),
    INDEX idx_key_path_method (idempotency_key, request_path, request_method)
);
```

**Colonnes :**
- `idempotency_key` — UUID ou string fourni par le client
- `request_path` — `/api/invoices` (pour validation)
- `request_method` — `POST`, `PUT`, `DELETE`
- `request_body` — JSON de la requête (pour détection de conflits)
- `response_status` — `201`, `200`, `204`, etc.
- `response_body` — JSON de la réponse
- `response_headers` — Headers de la réponse (JSON map)
- `expires_at` — TTL pour nettoyage automatique

**Contraintes :**
- `idempotency_key` UNIQUE (empêche les doublons)
- Index sur `expires_at` pour nettoyage rapide
- Index composite pour lookup rapide

---

## Workflow

### Middleware IdempotencyInterceptor

```java
@Component
public class IdempotencyInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                             HttpServletResponse response, 
                             Object handler) {
        String key = request.getHeader("Idempotency-Key");
        if (key == null) {
            return true; // Pas de clé → traitement normal
        }
        
        String method = request.getMethod();
        if (!isMutableMethod(method)) {
            return true; // GET, HEAD, OPTIONS → ignorer
        }
        
        String path = request.getRequestURI();
        
        // Lookup dans la base
        IdempotencyRecord record = idempotencyRepo.findByKey(key);
        
        if (record != null) {
            // Clé existe → vérifier cohérence
            if (!record.getPath().equals(path) || !record.getMethod().equals(method)) {
                throw new IdempotencyConflictException(
                    "Idempotency key '" + key + "' was used for " + 
                    record.getMethod() + " " + record.getPath() + 
                    " but now used for " + method + " " + path
                );
            }
            
            // Vérifier body (optionnel mais recommandé)
            String currentBody = readBody(request);
            if (!record.getRequestBody().equals(currentBody)) {
                throw new IdempotencyConflictException(
                    "Idempotency key '" + key + "' reused with different request body"
                );
            }
            
            // Retourner la réponse stockée
            replayResponse(response, record);
            return false; // Stop le traitement
        }
        
        // Clé n'existe pas → continuer
        request.setAttribute("idempotency.key", key);
        request.setAttribute("idempotency.body", readBody(request));
        return true;
    }
    
    private boolean isMutableMethod(String method) {
        return "POST".equals(method) || "PUT".equals(method) 
            || "PATCH".equals(method) || "DELETE".equals(method);
    }
    
    private void replayResponse(HttpServletResponse response, IdempotencyRecord record) {
        response.setStatus(record.getResponseStatus());
        response.setContentType("application/json");
        
        // Restaurer headers
        Map<String, String> headers = parseHeaders(record.getResponseHeaders());
        headers.forEach(response::setHeader);
        
        // Écrire body
        response.getWriter().write(record.getResponseBody());
    }
}
```

### AfterCompletion — Sauvegarder la réponse

```java
@Override
public void afterCompletion(HttpServletRequest request, 
                            HttpServletResponse response,
                            Object handler, 
                            Exception ex) {
    String key = (String) request.getAttribute("idempotency.key");
    if (key == null) return;
    
    String path = request.getRequestURI();
    String method = request.getMethod();
    String requestBody = (String) request.getAttribute("idempotency.body");
    
    int status = response.getStatus();
    String responseBody = captureResponseBody(response);
    String responseHeaders = captureHeaders(response);
    
    Instant expiresAt = Instant.now().plus(ttl, ChronoUnit.HOURS);
    
    IdempotencyRecord record = new IdempotencyRecord(
        key, path, method, requestBody,
        status, responseBody, responseHeaders,
        Instant.now(), expiresAt
    );
    
    idempotencyRepo.save(record);
}
```

---

## Configuration

### application.yml

```yaml
flashapi:
  idempotency:
    enabled: true
    ttl-hours: 24  # Durée de vie d'une clé (24h par défaut)
    cleanup-interval-hours: 6  # Nettoyage automatique toutes les 6h
    max-body-size: 10240  # Taille max du body à stocker (10KB)
```

### Nettoyage automatique

```java
@Scheduled(fixedRateString = "${flashapi.idempotency.cleanup-interval-hours}",
           timeUnit = TimeUnit.HOURS)
public void cleanupExpiredKeys() {
    int deleted = idempotencyRepo.deleteExpired(Instant.now());
    log.info("Idempotency cleanup: removed {} expired keys", deleted);
}
```

SQL :
```sql
DELETE FROM flash_idempotency_keys WHERE expires_at < NOW();
```

---

## Validation & Erreurs

### ❌ Clé réutilisée avec path/method différent

**Requête 1 :**
```http
POST /api/invoices
Idempotency-Key: abc123
```

**Requête 2 :**
```http
PUT /api/products/5
Idempotency-Key: abc123
```

**Réponse :**
```json
{
  "error": {
    "code": "IDEMPOTENCY_CONFLICT",
    "message": "Idempotency key 'abc123' was used for POST /api/invoices but now used for PUT /api/products/5",
    "status": 422
  }
}
```

### ❌ Clé réutilisée avec body différent

**Requête 1 :**
```http
POST /api/invoices
Idempotency-Key: abc123
{ "amount": 100 }
```

**Requête 2 :**
```http
POST /api/invoices
Idempotency-Key: abc123
{ "amount": 200 }
```

**Réponse :**
```json
{
  "error": {
    "code": "IDEMPOTENCY_CONFLICT",
    "message": "Idempotency key 'abc123' reused with different request body",
    "status": 422
  }
}
```

### ✅ Clé valide, réponse rejouée

**Réponse HTTP :**
```http
HTTP/1.1 201 Created
Content-Type: application/json
Idempotency-Replay: true

{
  "data": {
    "id": 123,
    "amount": 150.00,
    "status": "draft"
  }
}
```

**Header `Idempotency-Replay: true`** → indique au client que c'est une réponse rejouée.

---

## Format de clé

**Recommandé :** UUID v4

```javascript
// Côté client (JavaScript)
const idempotencyKey = crypto.randomUUID();

fetch('/api/invoices', {
  method: 'POST',
  headers: {
    'Idempotency-Key': idempotencyKey,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({ amount: 150 })
});
```

**Alternative :** Hash de la requête

```javascript
const requestBody = JSON.stringify({ amount: 150 });
const idempotencyKey = await sha256(requestBody);
```

**Validation côté serveur :**
- Longueur : 1-255 caractères
- Caractères valides : alphanumériques + `-` + `_`
- Rejeter si vide ou trop long

---

## Cas d'usage

### 1. Double-click sur bouton "Payer"

**Sans idempotency :**
- User clique 2× sur "Payer 150€"
- 2 requêtes POST /api/payments
- 2 paiements créés → 300€ facturés

**Avec idempotency :**
- User clique 2×
- 1ʳᵉ requête : crée Payment #123
- 2ᵉ requête : retourne Payment #123 (même réponse)
- 1 seul paiement

### 2. Retry réseau

**Sans idempotency :**
- Client envoie POST /api/orders
- Timeout réseau (mais le serveur a traité la requête)
- Client retry → 2ᵉ commande créée

**Avec idempotency :**
- 1ʳᵉ requête : Order #456 créé
- Timeout (client ne voit pas la réponse)
- Retry avec même `Idempotency-Key` → réponse rejouée, aucun doublon

### 3. Webhook retry

**Sans idempotency :**
- Service externe envoie webhook POST /api/webhooks/stripe
- Serveur répond 500 (erreur temporaire)
- Stripe retry → même événement traité 2×

**Avec idempotency :**
- Stripe inclut `Idempotency-Key` dans le header
- 1ʳᵉ tentative : erreur 500
- Retry : même clé → si la 1ʳᵉ a finalement réussi, retourne 200 (évite le doublon)

---

## Activation par entité

### Option 1 : Global (tous les endpoints)

```yaml
flashapi:
  idempotency:
    enabled: true
```

→ Tous les POST/PUT/DELETE nécessitent `Idempotency-Key` (ou optionnel selon config).

### Option 2 : Par entité

```java
@Entity
@FlashEntity(idempotency = true, idempotencyRequired = true)
public class Payment {
    @Id @GeneratedValue
    private Long id;
    
    private BigDecimal amount;
}
```

**Comportement :**
- `idempotency = true` → feature activée pour cette entité
- `idempotencyRequired = true` → header **obligatoire** (reject si absent)

Si `idempotencyRequired = false` → header optionnel, mais utilisé s'il est présent.

---

## Performance

### Overhead

**Lookup :** 1 requête SQL par POST/PUT/DELETE avec `Idempotency-Key`
```sql
SELECT * FROM flash_idempotency_keys WHERE idempotency_key = ? LIMIT 1;
```

**Index :** Unique sur `idempotency_key` → O(log n)

**Write :** 1 INSERT après succès de la requête
```sql
INSERT INTO flash_idempotency_keys (...) VALUES (...);
```

**Estimation :** +5-10ms par requête (négligeable vs traitement métier).

### Nettoyage

**Scheduled job :** Toutes les 6h (configurable)
```sql
DELETE FROM flash_idempotency_keys WHERE expires_at < NOW();
```

**Impact :** Minimal (exécuté en arrière-plan, utilise l'index sur `expires_at`).

---

## Sécurité

### ❌ Replay attack

**Attaque :** Attacker intercepte une requête avec `Idempotency-Key`, la rejoue plus tard.

**Protection :** TTL (24h par défaut). Après expiration, la clé est supprimée → replay échoue.

### ❌ Key guessing

**Attaque :** Attacker génère des clés aléatoires pour trouver des réponses stockées.

**Protection :** UUID v4 = 2^122 possibilités → impossible à bruteforce.

### ❌ Information disclosure

**Risque :** `response_body` contient des données sensibles (ex: mot de passe).

**Protection :** FlashAPI respecte `@FlashHidden`, `@FlashWriteOnly` → les champs cachés ne sont jamais dans `response_body`.

---

## Tests

### Test 1 : Création dupliquée

```java
@Test
void testIdempotency_duplicateCreation() {
    String key = UUID.randomUUID().toString();
    
    // 1ère requête
    Response response1 = post("/api/invoices")
        .header("Idempotency-Key", key)
        .body("{\"amount\": 100}")
        .execute();
    
    assertThat(response1.status()).isEqualTo(201);
    Long id1 = response1.jsonPath().getLong("data.id");
    
    // 2ème requête (même clé)
    Response response2 = post("/api/invoices")
        .header("Idempotency-Key", key)
        .body("{\"amount\": 100}")
        .execute();
    
    assertThat(response2.status()).isEqualTo(201);
    Long id2 = response2.jsonPath().getLong("data.id");
    
    // Même ID → aucun doublon
    assertThat(id1).isEqualTo(id2);
    
    // Header de replay
    assertThat(response2.header("Idempotency-Replay")).isEqualTo("true");
}
```

### Test 2 : Conflit de clé

```java
@Test
void testIdempotency_keyConflict() {
    String key = UUID.randomUUID().toString();
    
    // 1ère requête
    post("/api/invoices")
        .header("Idempotency-Key", key)
        .body("{\"amount\": 100}")
        .execute();
    
    // 2ème requête (même clé, path différent)
    Response response = post("/api/products")
        .header("Idempotency-Key", key)
        .body("{\"name\": \"Phone\"}")
        .execute();
    
    assertThat(response.status()).isEqualTo(422);
    assertThat(response.jsonPath().getString("error.code"))
        .isEqualTo("IDEMPOTENCY_CONFLICT");
}
```

### Test 3 : Expiration

```java
@Test
void testIdempotency_expiration() {
    String key = UUID.randomUUID().toString();
    
    // 1ère requête
    post("/api/invoices")
        .header("Idempotency-Key", key)
        .body("{\"amount\": 100}")
        .execute();
    
    // Simuler expiration (avancer l'horloge de 25h)
    clock.advance(Duration.ofHours(25));
    
    // Cleanup
    idempotencyService.cleanupExpired();
    
    // 2ème requête → nouvelle création (clé expirée)
    Response response = post("/api/invoices")
        .header("Idempotency-Key", key)
        .body("{\"amount\": 100}")
        .execute();
    
    assertThat(response.status()).isEqualTo(201);
    assertThat(response.header("Idempotency-Replay")).isNull();
}
```

---

## Différences avec @Version

| Feature | @Version (Optimistic Locking) | Idempotency Keys |
|---------|------------------------------|------------------|
| **Protège contre** | Conflits de modification simultanée | Doublons de requête |
| **Scénario** | User A et B modifient en même temps | Retry réseau, double-click |
| **Mécanisme** | JPA vérifie `version` à chaque UPDATE | Middleware vérifie clé avant traitement |
| **Opérations** | UPDATE uniquement | CREATE, UPDATE, DELETE |
| **Erreur** | `OptimisticLockException` | Réponse rejouée (200/201) |
| **Stockage** | Colonne `version` dans l'entité | Table `flash_idempotency_keys` séparée |

**Conclusion :** Les deux sont complémentaires. `@Version` pour les conflits concurrents, idempotency pour les doublons de requête.

---

## Roadmap

### Phase 1 (Sprint 2)
- ✅ Spec complète
- ⏳ Implémentation table + entity
- ⏳ Middleware interceptor
- ⏳ Configuration YAML
- ⏳ Tests unitaires

### Phase 2 (Sprint 3)
- ⏳ Nettoyage automatique (scheduled job)
- ⏳ Documentation utilisateur
- ⏳ Exemples client (JS, Java, Python)

### Phase 3 (Future)
- Support Redis pour stockage haute performance
- Compression du `response_body` (gzip)
- Stats (nombre de clés, replays, conflits)

---

## Références

- **Stripe Idempotency:** https://stripe.com/docs/api/idempotent_requests
- **GitHub Idempotency:** https://docs.github.com/en/rest/guides/best-practices-for-integrators#dealing-with-rate-limits
- **RFC 7231 (Safe Methods):** https://tools.ietf.org/html/rfc7231#section-4.2.1
