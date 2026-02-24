# AGENTS.md - Stock Trading Project Guidelines

## 项目变更要求
所有针对代码实现的变动，都应该遵循以下的顺序流程
1. 根据提示词，分析变更点，先修改需求和设计文档
2. 再根据需求和设计文档修改代码
3. 根据变动代码然后补充测试用例
4. 最后再执行相关流程的验证

## Project Structure

```
stock-trading/
├── stock-backend/          # Java Spring Boot 3.2.2 (JDK 17)
├── stock-frontend/         # React 18 + TypeScript 4.9 + UmiJS 4.x
├── stock-service/          # Python FastAPI AI service
└── documents/              # Design documents
```

## Build Commands

### Backend (stock-backend/)

```bash
cd stock-backend
set JAVA_HOME=C:\Users\MS\.jdks\jdk-17.0.12
D:\apache-maven-3.6.2\bin\mvn.cmd clean install              # Compile and package
D:\apache-maven-3.6.2\bin\mvn.cmd spring-boot:run           # Run application
D:\apache-maven-3.6.2\bin\mvn.cmd test                      # Run all tests
D:\apache-maven-3.6.2\bin\mvn.cmd test -Dtest=ClassName     # Run single test class
D:\apache-maven-3.6.2\bin\mvn.cmd test -Dtest=ClassName#method  # Run single test method
D:\apache-maven-3.6.2\bin\mvn.cmd clean install -DskipTests # Skip tests during build
```

> **注意**: 需要 JDK 17 才能编译此项目，当前环境已配置 JDK 17: `C:\Users\MS\.jdks\jdk-17.0.12`

### Frontend (stock-frontend/)

```bash
cd stock-frontend
pnpm install                  # Install dependencies
npm start                     # Start dev server
npm run build                 # Build for production
npm test                      # Run all tests
npm run jest -- path/to/test.tsx      # Run single test file
npm run jest -- -t "pattern"  # Run tests matching pattern
npm run tsc                   # Type check
npm run lint                  # Lint code
npm run lint:fix              # Fix lint issues
npm run prettier              # Format code
```

### Python AI Service (stock-service/)

```bash
cd stock-service
python -m venv venv
source venv/bin/activate       # Linux/Mac (Windows: venv\Scripts\activate)
pip install -r requirements.txt
pytest                         # Run tests
pytest tests/test_file.py      # Run single test file
pytest --cov=app              # Run with coverage
uvicorn app.main:app --reload --host 0.0.0.0 --port 8001
```

## Code Style Guidelines

### Java (Backend)

- **Java Version**: 17 (Spring Boot 3.2.2)
- **Naming**: CamelCase classes/methods, UPPER_SNAKE constants
- **Lombok**: `@Data`, `@Slf4j`, `@RequiredArgsConstructor`
- **Imports**: No wildcards, static imports last
- **Dependencies**: JPA (Hibernate), Hutool, FastJSON2, EasyExcel, SpringDoc OpenAPI
- **Error Handling**: `@ControllerAdvice` with `Response<T>` wrapper
- **API**: RESTful endpoints, `@RequestBody` for POST/PUT, `@PathVariable` for IDs
- **Logging**: `@Slf4j` with log.info() for requests, log.error() for exceptions
- **DTOs**: Nested static classes within controllers
- **Service**: Interface + Impl pattern, `@RequiredArgsConstructor` for DI

### TypeScript/React (Frontend)

- **Style**: Prettier config (singleQuote, trailingComma: 'all', printWidth: 100)
- **Imports**: `@/` alias for src/, group: React/libs/components/utils
- **Components**: Functional with hooks, PascalCase naming
- **Types**: Strict TypeScript, always define prop interfaces, use `type`
- **Formatting**: LF line endings, proseWrap: 'never', endOfLine: 'lf'
- **Framework**: UmiJS 4.x, Ant Design 5.x, React 18.x, Pro Components
- **State**: React hooks (useState, useRef, useEffect), no class components
- **API**: async/await with try-catch, errors via Ant Design notification
- **UI**: Ant Design, ProTable for grids, charts from @ant-design/charts
- **Intl**: `useIntl()` hook and `<FormattedMessage>`

### Python AI Service

- **Framework**: FastAPI 0.109.x, Pydantic 2.x
- **ML**: PyTorch 2.x, TensorFlow 2.15, scikit-learn, transformers
- **Structure**: `app/api/` (routes), `app/services/` (logic), `app/core/` (config)
- **Async**: async/await for I/O, `@asynccontextmanager` for lifespan
- **Logging**: Python logging with structured format
- **Errors**: FastAPI exception handlers, structured responses
- **Config**: Pydantic Settings with `.env`, case_sensitive=True
- **Typing**: Type hints everywhere, `List[Type]` from typing

## Testing Standards

### Java
- JUnit 5 (JUnit Jupiter), naming: `*Test.java`
- Mock with Mockito, location: `src/test/java/`
- Integration: `@SpringBootTest`, unit: mock dependencies

### TypeScript/React
- Jest with React Testing Library
- Files: `*.test.tsx` or `*.spec.tsx`
- Location: Co-located or `__tests__/`
- Coverage: `npm run test:coverage`

### Python
- pytest with pytest-asyncio for async tests
- Files: `test_*.py` or `*_test.py`
- Location: `tests/` folder or co-located
- Fixtures in `conftest.py`

## Error Handling

### Backend (Java)
- Global exception handler with `@ControllerAdvice`
- Return `Response<T>` wrapper with success/error status
- Log errors at ERROR level with exception details
- HTTP: 200 success, 400/404 client errors, 500 server errors

### Frontend (React)
- API errors with try-catch blocks
- Messages via Ant Design notification
- Loading states for async operations

### Python Service
- FastAPI exception handlers (`@app.exception_handler`)
- Structured error responses with error codes
- Use loguru or standard logging with appropriate levels

## Key Ports

- Backend API: http://localhost:8080
- Frontend: http://localhost:8000
- Python AI: http://localhost:8001
- Proxy: `/api/*` → `http://localhost:8080`

## Development Workflow

1. Run tests before committing
2. Ensure linting passes (`npm run lint` for frontend)
3. Type check TypeScript (`npm run tsc`)
4. Format code (`npm run prettier` for frontend)
5. Verify builds: `mvn clean install`, `npm run build`

## Module Architecture

- **Data Collection**: Stock data sync, AKShare/AKTools integration
- **Prediction**: LSTM model service for price prediction
- **Sentiment**: FinBERT-based sentiment analysis
- **Trading**: Order execution, account management
- **Risk**: Risk control and position management
- **Decision**: Trading signal generation engine
