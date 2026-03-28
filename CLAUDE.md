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

### Frontend (React/Vite) — working directory: `frontend-v2/`

```bash
npm install                  # Install dependencies
npm run dev                  # Dev server on port 5173 (proxies /api to localhost:8080)
npm run build                # Production build (runs tsc + vite build)
npm run lint                 # ESLint
```

### After every code change, verify compilation:
- Backend: `cd backend && mvn compile`
- Frontend: `cd frontend-v2 && npm run build`

### Docker (infrastructure for local dev)

```bash
docker-compose up -d mysql redis mongo   # Start databases only
docker-compose up -d --build             # Full stack deployment
```

## Architecture

Maven multi-module project: parent POM (`pom.xml`) aggregates `backend/` and `frontend-v2/`. In production, frontend is built via Maven and bundled into the backend JAR as static resources — single deployable artifact on port 8080.

### Backend: Single Spring Boot app (Java 17, Spring Boot 3.2.10)

Organized by **business domain**, not by layer:

| Package (`com.stock.*`) | Purpose |
|---|---|
| `dataCollector` | Stock list sync, real-time quotes, historical K-lines, news scraping |
| `modelService` | LSTM price prediction + FinBERT sentiment analysis via DJL (Deep Java Library). Models stored in MongoDB as binary, loaded by `mongo:ID` identifier — no local file I/O |
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

### Databases

- **MySQL 8**: Business data (stocks, prices, trades, positions, job configs). JPA `ddl-auto: update` — no migration scripts.
- **MongoDB 6**: Model weights (binary), training records. Dynamic collections per model.
- **Redis 7**: Caching layer for predictions and strategy state.

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
- **API paths**: `/api/` prefix, camelCase (e.g., `/api/autoLogin/login`, `/api/browser/phone/sendCode`). Path params always at end of URL (e.g., `/api/jobs/status/{id}`, not `/api/jobs/{id}/status`). **禁止使用 kebab-case（如 `check-agreements`），必须使用 camelCase（如 `checkAgreements`）**
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
- Sentiment model files (`models/sentiment/`) are managed by Git LFS.
- Database credentials use `${GLOBAL_DB_PASSWORD}` env var.
- Ports: backend 8080, frontend dev 5173, MySQL 3306, MongoDB 27017, Redis 6379.
