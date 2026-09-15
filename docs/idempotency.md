# Idempotency Keys

## Vue d'ensemble

Les clés d'idempotence empêchent le traitement en double des requêtes. Si le même `Idempotency-Key` arrive deux fois, la seconde retourne la même réponse sans réexécuter l'opération.

**Cas d'usage :** Double-click, retry réseau, webhook replay

---

## Configuration

```yaml
flashapi:
  idempotency:
    enabled: true
    ttl-hours: 24
    cleanup-interval-hours: 6
```

---

## Utilisation

```javascript
const idempotencyKey = crypto.randomUUID();

await fetch('/api/invoices', {
  method: 'POST',
  headers: {
    'Idempotency-Key': idempotencyKey,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({ amount: 150 })
});
```

**Réponse rejouée :**
```http
HTTP/1.1 201 Created
Idempotency-Replay: true

{"data": {"id": 123, "amount": 150}}
```

---

## Méthodes supportées

✅ POST, PUT, PATCH, DELETE  
❌ GET, HEAD, OPTIONS (ignorés)

---

## Validation

### ❌ Conflit de path/method/body

```javascript
// Requête 1
POST /api/invoices
Idempotency-Key: abc123
{"amount": 100}

// Requête 2 (body différent)
POST /api/invoices
Idempotency-Key: abc123
{"amount": 200}
```

**Erreur 422 :**
```json
{
  "error": {
    "code": "IDEMPOTENCY_CONFLICT",
    "message": "Idempotency key 'abc123' reused with different request body"
  }
}
```

---

## Expiration

Les clés expirent après `ttl-hours` (24h par défaut). Cleanup automatique toutes les 6h.

---

## Performance

**Overhead :** ~5-10ms (1 lookup SQL + 1 INSERT si nouvelle clé)

---

## Sécurité

- **Replay attack :** TTL de 24h
- **Key guessing :** UUID v4 = 2^122 possibilités
- **Information disclosure :** Respect de `@FlashHidden` et `@FlashWriteOnly`

---

## FAQ

**Q : Obligatoire ?**  
Non. Si pas de header, traitement normal.

**Q : Réutiliser pour plusieurs entités ?**  
Non. Une clé = un triplet (key, path, method).

**Q : Si 1ʳᵉ requête échoue ?**  
Seules les réponses 2xx sont stockées.

**Q : Différence avec @Version ?**  
- **@Version** → conflits concurrents
- **Idempotency** → doublons de requête

---

## Références

- **[Spec technique](idempotency-spec.md)**
- **[Stripe Idempotency](https://stripe.com/docs/api/idempotent_requests)**
