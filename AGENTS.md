# AGENTS.md - Stock Trading Project Guidelines

## Project Structure

```
stock-trading/
├── stock-backend/          # Java Spring Boot backend
├── stock-frontend/        # React frontend (Ant Design Pro)
├── stock-service/         # Python AI service (FastAPI)
└── documents/             # Design documents
```

## Build Commands

### Backend (stock-backend/)

```bash
cd stock-backend

# Compile and package
mvn clean install

# Run Spring Boot application
mvn spring-boot:run

# Run all tests
mvn test

# Run single test class
mvn test -Dtest=ClassName

# Run single test method
mvn test -Dtest=ClassName#methodName

# Skip tests during build
mvn clean install -DskipTests
```

### Frontend (stock-frontend/)

```bash
cd stock-frontend

# Install dependencies (use pnpm)
pnpm install

# Start development server
npm start
# or
npm run start:dev

# Build for production
npm run build

# Run all tests
npm test

# Run tests in watch mode
npm run jest -- --watch

# Run single test file
npm run jest -- path/to/test.tsx

# Run tests matching pattern
npm run jest -- -t "test pattern"

# Type check
npm run tsc

# Lint code
npm run lint

# Fix lint issues
npm run lint:fix
```

### Python AI Service (stock-service/)

```bash
cd stock-service

# Create virtual environment
python -m venv venv
source venv/bin/activate  # Linux/Mac
# venv\Scripts\activate   # Windows

# Install dependencies
pip install -r requirements.txt

# Run FastAPI development server
uvicorn app.main:app --reload --host 0.0.0.0 --port 8001
```

## Code Style Guidelines

### Java (Backend)

- **Java Version**: 17 (Spring Boot 3.2.2)
- **Framework**: Spring Boot
- **Naming**: CamelCase for classes/methods, UPPER_SNAKE for constants
- **Lombok**: Use `@Data`, `@Slf4j` annotations
- **Imports**: Organize imports, no wildcard imports
- **Key Dependencies**: MyBatis-Plus, Hutool, FastJSON2, EasyExcel

### TypeScript/React (Frontend)

- **Style**: Prettier config (single quotes, trailing commas, 100 char width)
- **Imports**: Use `@/` alias for src/ directory imports
- **Components**: Functional components with hooks, PascalCase naming
- **Types**: Strict TypeScript, always define prop interfaces
- **Formatting**: LF line endings, no prose wrap
- **Framework**: UmiJS 4.x, Ant Design 5.x, React 18.x

### Python AI Service

- **Framework**: FastAPI 0.109.x
- **ML Libraries**: PyTorch, TensorFlow, scikit-learn
- **Testing**: pytest with pytest-asyncio
- **Async**: Use async/await for I/O operations

## Testing Standards

### Java
- Use JUnit 5 (JUnit Jupiter)
- Test class naming: `*Test.java`
- Mock external dependencies with Mockito

### TypeScript/React
- Use Jest with React Testing Library
- Test files: `*.test.tsx` or `*.spec.tsx`

### Python
- Use pytest
- Test files: `test_*.py` or `*_test.py`

## Development Workflow

1. Always run tests before committing
2. Ensure linting passes (`npm run lint` for frontend)
3. Type check TypeScript (`npm run tsc`)
4. Ports: Backend 8080, Frontend 8000, Python AI 8001

## Key Ports

- Backend API: http://localhost:8080
- Frontend: http://localhost:8000
- Python AI: http://localhost:8001
- Proxy: `/api/*` → `http://localhost:8080`
