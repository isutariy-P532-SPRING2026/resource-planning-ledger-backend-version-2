# Resource Planning Ledger — Backend

**Live API:** https://resource-planning-ledger-backend-version-w90h.onrender.com/

**Frontend:** https://resource-planning-ledger-frontend-qyhq.onrender.com/

**GitHub backend:** [isutariy-P532-SPRING2026/resource-planning-ledger-backend-version-2](https://github.com/isutariy-P532-SPRING2026/resource-planning-ledger-backend-version-2)

**GitHub frontend:** [isutariy-P532-SPRING2026/resource-planning-ledger-frontend-version-2](https://github.com/isutariy-P532-SPRING2026/resource-planning-ledger-frontend-version-2)

A Resource Planning Ledger REST API built with Java 17 + Spring Boot 3 and PostgreSQL, following a four-layer architecture (Controller → Manager → Engine → Repository) and six classic OO design patterns.

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
| `GET` | `/api/plans/{id}/report?status=X` | Report filtered to a specific action status |
| `GET` | `/api/plans/{id}/metrics` | Completion, resource cost, and risk metrics for the plan subtree |
| `GET` | `/api/plans/{id}/actions` | List all actions under a plan |
| `GET` | `/api/plans/{id}/actions?status=X` | List actions filtered by status |

### Actions
| Method | Path | Description |
|---|---|---|
| `GET` | `/api/actions/{id}` | Get action detail with allocations and legal transitions |
| `POST` | `/api/actions/{id}/implement` | Transition → IN_PROGRESS |
| `POST` | `/api/actions/{id}/complete` | Transition → COMPLETED, posts ledger entries |
| `POST` | `/api/actions/{id}/suspend` | Transition → SUSPENDED (requires reason) |
| `POST` | `/api/actions/{id}/resume` | Resume from SUSPENDED |
| `POST` | `/api/actions/{id}/abandon` | Transition → ABANDONED |
| `POST` | `/api/actions/{id}/submit-for-approval` | Transition → PENDING_APPROVAL |
| `POST` | `/api/actions/{id}/approve` | Transition → COMPLETED (from PENDING_APPROVAL) |
| `POST` | `/api/actions/{id}/reject` | Transition → IN_PROGRESS (from PENDING_APPROVAL) |
| `POST` | `/api/actions/{id}/reopen` | Transition → REOPENED, reverses ledger entries |
| `POST` | `/api/actions/{id}/allocations` | Add resource allocation |

### Accounts & Ledger
| Method | Path | Description |
|---|---|---|
| `GET` | `/api/accounts` | List all accounts with balances and resource kind |
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

`ProposedAction` stores a `stateName` string resolved at runtime to a stateless Spring singleton `ActionState` bean via `ActionStateMachineEngine`. Each state class encapsulates its own legal transitions and throws `IllegalStateTransitionException` for illegal ones. The `ActionContext` + `ActionContextCallback` interface decouples state objects from managers, keeping them framework-agnostic and unit-testable.

**Full state transition map (Week 2):**
```
PROPOSED         → IN_PROGRESS       (implement)
IN_PROGRESS      → PENDING_APPROVAL  (submit-for-approval)
IN_PROGRESS      → SUSPENDED         (suspend)
IN_PROGRESS      → ABANDONED         (abandon)
PENDING_APPROVAL → COMPLETED         (approve)
PENDING_APPROVAL → IN_PROGRESS       (reject)
COMPLETED        → REOPENED          (reopen — reverses ledger entries)
SUSPENDED        → IN_PROGRESS       (resume, if already implemented)
SUSPENDED        → PROPOSED          (resume, if not yet implemented)
SUSPENDED        → ABANDONED         (abandon)
```

### 2. Composite — `PlanNode` tree

`Plan` (composite) and `ProposedAction` (leaf) both implement `PlanNode` and extend `PlanNodeEntity` (JOINED JPA inheritance with `node_type` discriminator). `Plan.getStatus()` is derived from children per spec rules. `getTotalAllocatedQuantity()` recurses through all descendants. The `accept(PlanNodeVisitor)` method is wired on every node for Visitor extensibility.

### 3. Iterator — `DepthFirstPlanIterator`

A pure-Java stack-based `Iterator<PlanNode>` that performs depth-first pre-order traversal over an already-loaded in-memory tree. No JPA queries fire inside `next()`. `PlanManager.loadChildrenRecursively()` eagerly loads the full subtree before traversal so the iterator works safely with `open-in-view=false`.

### 4. Template Method — `AbstractLedgerEntryGenerator`

The ledger-entry generation skeleton (`generateEntries`) is `final` and cannot be overridden, guaranteeing double-entry conservation in `postEntries` (also `final`). Subclasses implement `selectAllocations()` and `validate()`, and may override the `buildWithdrawal()`, `buildDeposit()`, and `afterPost()` hooks.

`LedgerEngine` injects `List<AbstractLedgerEntryGenerator>` — new generators are added as new `@Component` subclasses with zero changes to existing code. `ReversalLedgerEntryGenerator` is called explicitly by `ActionApprovalManager.reopen()` to reverse all ledger entries for a completed action, restoring pool balances.

### 5. Visitor — `PlanNodeVisitor` (Week 2)

Three visitor implementations traverse the plan tree via `DepthFirstPlanIterator`:

- **`CompletionRatioVisitor`** — counts completed vs total actions, computes completion ratio
- **`ResourceCostVisitor`** — accumulates total allocated quantity per resource type across all leaf actions (uses `loadedAllocations` pre-fetched by `PlanManager`)
- **`RiskScoreVisitor`** — scores risk from action status distribution (ABANDONED=3pts, SUSPENDED=2pts, PROPOSED=1pt); classifies as LOW / MEDIUM / HIGH

Results returned as `{completion, resourceCost, risk}` by `GET /api/plans/{id}/metrics`.

### 6. Strategy — `PostingRuleEngine`

`PostingRuleEngine` holds a list of `PostingRule` strategy implementations. After each ledger entry is saved, all applicable rules fire against that entry and its account (e.g., `OverConsumptionAlertRule` fires when a pool balance goes negative and writes an `OVER_CONSUMPTION_ALERT` audit entry).

---

## Architecture

```
Controller  ←→  Manager  ←→  Engine  ←→  Repository
   ↓               ↓            ↓             ↓
HTTP/JSON    Business logic  Algorithms   Spring Data JPA
```

- **Controllers** — thin REST layer, no business logic
- **Managers** — orchestrate transactions, delegate to engines/repos (`ActionManager`, `ActionApprovalManager`, `LedgerManager`, `PlanManager`, `ReportManager`)
- **Engines** — stateless algorithms (`ActionStateMachineEngine`, `LedgerEngine`, `PlanInstantiationEngine`, `PostingRuleEngine`)
- **Repositories** — Spring Data JPA interfaces

---

## Render.com Deployment

1. Create a **Web Service** → Docker → port 8080.
2. Create a **PostgreSQL** database (free tier).
3. Set environment variables: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
4. `spring.jpa.hibernate.ddl-auto=update` auto-creates and migrates the schema on startup.

> **Note:** `ddl-auto=update` adds new columns and tables but never removes existing check constraints. If new enum values are added to `ActionStatus`, run the following SQL migration on the live database:
> ```sql
> ALTER TABLE implemented_actions DROP CONSTRAINT implemented_actions_status_check;
> ALTER TABLE implemented_actions ADD CONSTRAINT implemented_actions_status_check
>   CHECK (status IN ('PROPOSED', 'PENDING_APPROVAL', 'IN_PROGRESS', 'SUSPENDED', 'COMPLETED', 'REOPENED', 'ABANDONED'));
> ```
