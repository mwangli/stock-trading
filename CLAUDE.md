<!-- AI_GENERATE_START ---- -->
# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Language Requirement

All responses must be in Chinese (except code identifiers, class/method/API names). Show structured reasoning: conclusion -> evidence -> reasoning -> verification -> alternatives.

## Build & Run Commands

### Backend (Java/Maven) — working directory: `backend/`

```bash
mvn spring-boot:run          # Start app on port 8080
mvn compile                  # Compile only (use for quick verification after changes)
mvn clean package -DskipTests  # Build JAR (no automated tests in this project)
```

### Frontend (React/Vite) — working directory: `frontend/`

```bash
npm install                  # Install dependencies
npm run dev                  # Dev server on port 5173 (proxies /api to localhost:8080)
npm run build                # Production build (runs tsc + vite build)
npm run lint                 # ESLint
```

### Python model workspace — working directory: `python/`

```bash
python -m venv .venv
.venv\Scripts\activate
python -m pip install -e ".[dev]"
python -m stock_models inspect-config --config configs/base.yaml
```

Use Python 3.14. Python is an offline training and artifact workspace, not an online trading service.
### After every code change, verify compilation:
- Backend: `cd backend && mvn compile`
- Frontend: `cd frontend && npm run build`

### Docker (infrastructure for local dev)

```bash
docker compose up -d stock-mysql stock-mongo   # Start databases only
docker-compose up -d --build             # Full stack deployment
```

## Architecture

The repository has three code workspaces: `frontend/`, `backend/`, and `python/`, plus peer-level `docs/`. The Maven parent aggregates `backend/` and `frontend/`; Python uses `pyproject.toml`. Production keeps frontend and backend as separate containers, while the Python training profile is opt-in and does not start by default.

### Backend: Single Spring Boot app (Java 17, Spring Boot 3.2.10)

Organized by **business domain**, not by layer:

| Package (`com.stock.*`) | Purpose |
|---|---|
| `dataCollector` | Stock list sync, real-time quotes, historical K-lines, news scraping |
| `modelService` | Online features, model artifact validation, inference, sentiment aggregation, and the transitional DJL compatibility path. Target production inference uses ONNX Runtime. |
| `strategyAnalysis` | Daily stock selection (LSTM 60% + sentiment 40%) and T+1 intraday sell strategy (moving stop-loss, RSI, volume divergence, Bollinger bands, forced close at 14:57) |
| `tradingExecutor` | Risk controls, order execution (simulated), position/holding management, fee calculation |
| `job` | Unified dynamic task scheduler — all scheduled tasks go through `JobConfig` table + `JobSchedulerService` |
| `config` | Global configs (WebSocket, scheduling, etc.) |
| `logging` | `ApiLoggingAspect` — global AOP for controller access logging |
| `event`, `handler` | Spring events, WebSocket handlers for real-time log/notification push |

Each domain module follows: `controller/` -> `service/` -> `repository/` (JPA for MySQL, MongoRepository for MongoDB), with `entity/`, `dto/`, `config/`, `scheduled/` sub-packages as needed.

### Frontend: React 19 + Vite 7 + TypeScript 5

- **UI**: Ant Design 6, TailwindCSS 4 (mandatory — no inline styles or CSS files)
- **State**: Zustand stores in `src/store/`
- **API**: Axios clients in `src/api/`
- **i18n**: i18next with `src/locales/`
- **Charts**: ECharts
- **Routing**: React Router 7

### Python model workspace: Python 3.14

- **Training**: PyTorch and Transformers.
- **Artifacts**: ONNX, ONNX Runtime validation, Pydantic contracts, SHA-256 manifests.
- **Data**: Java-exported immutable snapshots through Pandas/PyArrow.
- **Precision**: FP32 by default; sentiment INT8 is allowed only after FP32 fails measured 2C4G resource gates and INT8 passes business validation.
- **Boundary**: Python never calls the broker, executes risk rules, or activates the production model pointer.
- **Comments**: Use Chinese docstrings/comments for business purpose, inputs, outputs, failure conditions, and production boundaries.
### Databases

- **MySQL 8**: Business data (stocks, prices, trades, positions, job configs). JPA `ddl-auto: update` — no migration scripts.
- **MongoDB 6**: Model weights (binary), training records. Dynamic collections per model.
- **Broker Token cache**: In-memory TTL cache inside the single backend instance; restart triggers re-login.

### Data Flow (T+1 trade cycle)

1. `DataSyncScheduler` collects daily K-lines + news
2. LSTM predicts next-day price; FinBERT scores news sentiment
3. `SelectStockScheduler` runs dual-factor selection at market open -> BUY signals
4. `IntradayStrategyScheduler` monitors positions every minute -> SELL decisions
5. `TradingExecutor` executes orders, records trades
6. WebSocket pushes real-time logs/notifications to frontend dashboard

## Mandatory Code Conventions

### Java

- **Author**: All class Javadoc must use `@author mwangli` with `@since` date
- **Javadoc**: Required on all public/protected methods with `@param`, `@return`, `@throws`
- **Business comments**: Numbered step comments at key logic nodes (e.g., `// 1. 检查仓位上限`)
- **Response wrapper**: All endpoints return `ResponseDTO<T>` — never raw DTOs or Maps
- **DTOs**: All controller params/returns must be typed DTOs (never `Map`, `List<Map>`, `Object`). DTO fields require Javadoc comments.
- **API paths**: `/api/` prefix, lowercase+hyphens (REST style). Path params always at end of URL (e.g., `/api/jobs/status/{id}`, not `/api/jobs/{id}/status`)
- **Scheduled tasks**: MUST use `JobConfig` table + `JobSchedulerService`. NEVER use `@Scheduled` annotation.
- **Controller logging**: Every controller uses `@Slf4j` with explicit `log.info()` at method entry (in addition to global AOP)
- **Error handling**: Global `@ControllerAdvice`. Never swallow exceptions.
- **Lombok**: Use `@Data`, `@Slf4j`, `@RequiredArgsConstructor`

### TypeScript/React

- **Strict typing**: No `any` — always define Interface/Type
- **Styling**: TailwindCSS classes only (no inline `style` or separate CSS)
- **Components**: Functional components + Hooks, PascalCase filenames
- **Mock fallback**: When API is unavailable, display mock data — never leave pages empty or erroring

### Git

- Atomic commits with Conventional Commits (`feat:`, `fix:`, `docs:`, `refactor:`)
- Never use `git reset --hard` or `git push -f` — use `revert` or new commits
- Push immediately after commit

## Project Notes

- **No automated tests** — quality relies on code review and manual verification. Do not generate test cases.
- Backend is a single monolith — always build from `backend/` directory, do not look for sub-module POMs.
- Temporary files go in `.tmp/` (gitignored).
- Model binaries, training datasets, caches, and `python/runtime/` are not committed. Versioned ONNX artifacts are released through the documented artifact workflow.
- Database credentials use `${GLOBAL_DB_PASSWORD}` env var.
- Ports: backend 8080, frontend dev 5173, MySQL 3306, MongoDB 27017.
<!-- AI_GENERATE_END ---- -->
