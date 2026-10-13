# Demo Script

A ~5 minute walkthrough of the Inventory Service, run locally so the audit trail can be shown in the H2 console.

---

## Setup

```bash
mvn clean spring-boot:run
```

Wait for `Started InventoryApplication`, then open two tabs:

| Tab | URL | Notes |
|---|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html | |
| H2 console | http://localhost:8080/h2-console | JDBC URL `jdbc:h2:mem:inventorydb`, user `sa`, password blank → **Connect** |

The database is in-memory and resets on every restart, so each run starts clean.

In Swagger, each step is: click the endpoint → **Try it out** → paste the body → **Execute**.

---

## 1. Public reads, protected writes

- `GET /api/products` → **200**, empty list
- `POST /api/products` (any body, no token) → **403**

> Anyone can read the catalogue, but changing stock requires authentication — a single rule in `SecurityConfig`.

## 2. Authentication

- `POST /api/auth/register`
  ```json
  {"username":"alice","password":"password123"}
  ```
- Copy the `token` from the response.
- Click **Authorize** (padlock, top right) → paste the token → **Authorize** → **Close**.
- Optional: `POST /api/auth/login` with a wrong password → **401**

> Login returns a signed JWT. The server holds no sessions — `JwtAuthenticationFilter` verifies the signature on every request, so the service scales horizontally behind a load balancer.

## 3. Create, conflict, validation

- `POST /api/products`
  ```json
  {"sku":"SKU-001","name":"Widget","price":9.99,"quantity":100}
  ```
  → **201**, `"version": 0`
- Execute the same request again → **409** (duplicate SKU)
- `POST /api/products` with `{"sku":"","price":-1}` → **400**, with an error per field

> All errors use the RFC 7807 `ProblemDetail` format, so clients can handle them uniformly.

## 4. Stock update and audit trail

- `PUT /api/products/{id}` with id `1`
  ```json
  {"sku":"SKU-001","name":"Widget","price":9.99,"quantity":60}
  ```
  → **200**, `"version": 1`
- In the H2 console:
  ```sql
  SELECT * FROM INVENTORY_EVENTS;
  ```
  Two rows: **STOCK_SET 100** and **STOCK_DECREASE 40**, each with `USER_ID = 1`.

> Every stock change publishes a `StockChangedEvent`. `InventoryEventListener` writes the audit row only **after the main transaction commits**, so a rolled-back update never leaves a phantom audit entry.
>
> The subtle part: in the `AFTER_COMMIT` phase the original transaction has already finished, so a plain `save()` joins it and is never flushed — the row is silently lost. The listener therefore runs in its own transaction (`Propagation.REQUIRES_NEW`), and an integration test (`stockChanges_areRecordedInAuditTrail`) fails if that annotation is removed.

## 5. Build and delivery

- GitHub → **Actions** tab → green `mvn verify` run.
- `Dockerfile`: multi-stage build — full Maven image to compile, slim JRE image to run, non-root user.
- `docker compose up` starts PostgreSQL and the app together; data persists in a named volume.

---

## Likely questions

**What does `@Version` do?**
Optimistic locking. Each update checks the version it read; if another write committed first, it fails with a **409** instead of silently overwriting. Current limitation: `ProductRequest` doesn't carry a `version`, so it guards concurrent database writes but not two clients editing over HTTP. Adding `version` to the request DTO would close that gap.

**Why 403 rather than 401 when no token is sent?**
That's Spring Security's default. The more correct response is 401; a custom `AuthenticationEntryPoint` would fix it.

**How would the audit trail scale?**
Move from an in-process listener to the transactional outbox pattern or a message broker (e.g. Kafka). Today, if the process crashes between commit and the listener running, that audit row is lost.

**Why MapStruct?**
Entity ↔ DTO mapping generated at compile time — no reflection, and mapping mistakes are compile errors.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `No qualifying bean of type 'ProductMapper'` | Stale build output (often from an IDE compiling into `target/`). Run `mvn clean spring-boot:run`. In VS Code, also run **Java: Clean Java Language Server Workspace**. |
| Port 8080 already in use | Stop the other process, or run with `-Dspring-boot.run.arguments=--server.port=8081`. |
| H2 console says database not found | JDBC URL must be exactly `jdbc:h2:mem:inventorydb`. |
