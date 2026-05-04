# Resource Planning Ledger — Backend

**Live API:** https://resource-planning-ledger-backend-version-w90h.onrender.com/

**Frontend:** https://resource-planning-ledger-frontend-qyhq.onrender.com/

**GitHub backend:** [isutariy-P532-SPRING2026/resource-planning-ledger-backend-version-2](https://github.com/isutariy-P532-SPRING2026/resource-planning-ledger-backend-version-2)

**GitHub frontend:** [isutariy-P532-SPRING2026/resource-planning-ledger-frontend-version-2](https://github.com/isutariy-P532-SPRING2026/resource-planning-ledger-frontend-version-2)

A Resource Planning Ledger REST API built with Java 17 + Spring Boot 3 and PostgreSQL, following a four-layer architecture (Controller → Manager → Engine → Repository) and four classic OO design patterns.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.x |
| Database | PostgreSQL 16 |
| ORM | Spring Data JPA / Hibernate 6 |
| Build | Maven (Maven Wrapper) |
| Deploy | Render.com (Docker) |

---

## Running Locally

### Prerequisites
- Java 17+
- PostgreSQL running on `localhost:5432` with database `rpl`, user `rpl`, password `rpl`

```bash
# Clone and run
./mvnw spring-boot:run
# API available at http://localhost:8080
```

### With Docker Compose (recommended)

```bash
docker compose up --build
# API available at http://localhost:8080
```

### Standalone Docker

```bash
# Start PostgreSQL
docker run -d --name rpl-db \
  -e POSTGRES_DB=rpl -e POSTGRES_USER=rpl -e POSTGRES_PASSWORD=rpl \
  -p 5432:5432 postgres:16-alpine

# Build and run app
docker build -t rpl .
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/rpl \
  -e SPRING_DATASOURCE_USERNAME=rpl \
  -e SPRING_DATASOURCE_PASSWORD=rpl \
  rpl
```

---

## Environment Variables

| Variable | Description | Default (local) |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL for PostgreSQL | `jdbc:postgresql://localhost:5432/rpl` |
| `SPRING_DATASOURCE_USERNAME` | DB username | `rpl` |
| `SPRING_DATASOURCE_PASSWORD` | DB password | `rpl` |

---

## API Endpoints

### Plans
| Method | Path | Description |
|---|---|---|
| `GET` | `/api/plans` | List all top-level plans |
| `POST` | `/api/plans` | Create a new plan (scratch or from protocol) |
| `GET` | `/api/plans/{id}` | Get plan with full node tree |
| `POST` | `/api/plans/{id}/children` | Add a child node (action or sub-plan) |
| `GET` | `/api/plans/{id}/report` | Depth-first traversal report with allocations |

### Actions
| Method | Path | Description |
|---|---|---|
| `GET` | `/api/actions/{id}` | Get action detail |
| `POST` | `/api/actions/{id}/implement` | Transition → IN_PROGRESS |
| `POST` | `/api/actions/{id}/complete` | Transition → COMPLETED, posts ledger entries |
| `POST` | `/api/actions/{id}/suspend` | Transition → SUSPENDED |
| `POST` | `/api/actions/{id}/resume` | Resume from SUSPENDED |
| `POST` | `/api/actions/{id}/abandon` | Transition → ABANDONED |
| `POST` | `/api/actions/{id}/allocations` | Add resource allocation |

### Accounts & Ledger
| Method | Path | Description |
|---|---|---|
| `GET` | `/api/accounts` | List all accounts with balances |
| `GET` | `/api/accounts/{id}/entries` | Get ledger entries for an account |
| `POST` | `/api/accounts/{id}/deposit` | Deposit to a pool account |

### Protocols
| Method | Path | Description |
|---|---|---|
| `GET` | `/api/protocols` | List all protocols |
| `POST` | `/api/protocols` | Create a protocol with steps |
| `PUT` | `/api/protocols/{id}` | Update a protocol |
| `DELETE` | `/api/protocols/{id}` | Delete a protocol |

### Resource Types
| Method | Path | Description |
|---|---|---|
| `GET` | `/api/resource-types` | List all resource types |
| `POST` | `/api/resource-types` | Create a resource type |
| `PUT` | `/api/resource-types/{id}` | Update a resource type |
| `DELETE` | `/api/resource-types/{id}` | Delete a resource type |

### Audit Log
| Method | Path | Description |
|---|---|---|
| `GET` | `/api/audit-log` | Full chronological audit event log |

---

## Design Patterns

### 1. State — `ActionStateMachine`

`ProposedAction` stores a `stateName` string resolved at runtime to a stateless Spring singleton `ActionState` bean via `ActionStateMachineEngine`. Each state class (`ProposedState`, `InProgressState`, `SuspendedState`, `CompletedState`, `AbandonedState`) encapsulates its own legal transitions and throws `IllegalStateTransitionException` for illegal ones.

The `ActionContext` + `ActionContextCallback` interface decouples state objects from `ActionManager`, keeping them framework-agnostic and unit-testable without a Spring context.

**State transitions:**
```
PROPOSED → IN_PROGRESS (implement)
IN_PROGRESS → COMPLETED (complete)
IN_PROGRESS → SUSPENDED (suspend)
IN_PROGRESS → ABANDONED (abandon)
SUSPENDED → IN_PROGRESS (resume, if already implemented)
SUSPENDED → PROPOSED (resume, if not yet implemented)
SUSPENDED → ABANDONED (abandon)
```

### 2. Composite — `PlanNode` tree

`Plan` (composite) and `ProposedAction` (leaf) both implement `PlanNode` and extend `PlanNodeEntity` (JOINED JPA inheritance with `node_type` discriminator). `Plan.getStatus()` is derived from children per spec rules. `getTotalAllocatedQuantity()` recurses through all descendants. The `accept(PlanNodeVisitor)` method is wired on every node for Visitor extensibility.

### 3. Iterator — `DepthFirstPlanIterator`

A pure-Java stack-based `Iterator<PlanNode>` that performs depth-first pre-order traversal over an already-loaded in-memory tree. No JPA queries fire inside `next()`. `PlanManager.loadChildrenRecursively()` eagerly loads the full subtree before traversal so the iterator works safely with `open-in-view=false`.

### 4. Template Method — `AbstractLedgerEntryGenerator`

The ledger-entry generation skeleton (`generateEntries`) is `final` and cannot be overridden, guaranteeing double-entry conservation in `postEntries` (also `final`). Subclasses implement `selectAllocations()` and `validate()`, and may override the `afterPost()` hook. `LedgerEngine` injects `List<AbstractLedgerEntryGenerator>` — new generators are added as new `@Component` subclasses with zero changes to existing code.

Each completed action produces a `TRANSACTION_POSTED` audit entry that captures both the debit and credit sides plus a sum-to-zero verification.

---

## Architecture

```
Controller  ←→  Manager  ←→  Engine  ←→  Repository
   ↓               ↓            ↓             ↓
HTTP/JSON    Business logic  Algorithms   Spring Data JPA
```

- **Controllers** — thin REST layer, no business logic
- **Managers** — orchestrate transactions, delegate to engines/repos
- **Engines** — stateless algorithms (`ActionStateMachineEngine`, `LedgerEngine`, `PlanInstantiationEngine`, `PostingRuleEngine`)
- **Repositories** — Spring Data JPA interfaces

---

## Render.com Deployment

1. Create a **Web Service** → Docker → port 8080.
2. Create a **PostgreSQL** database (free tier).
3. Set environment variables: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
4. `spring.jpa.hibernate.ddl-auto=update` auto-creates and migrates the schema on startup.
