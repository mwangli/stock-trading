# AGENTS.md - Stock Trading Project Guidelines

## Project Structure

This is a full-stack stock trading application with AI-powered trading decisions:

```
stock-trading/
├── stock-backend/          # Java Spring Boot backend
│   ├── src/main/java/     # Java source code
│   ├── pom.xml            # Maven configuration
│   └── Dockerfile         # Container config
├── stock-front/           # React frontend (Ant Design Pro)
│   ├── src/               # TypeScript/React source
│   ├── package.json       # Node dependencies
│   └── config/            # Build configuration
├── py-service/            # Python AI service (FastAPI)
│   ├── app/              # Application code
│   │   ├── api/          # API routes
│   │   ├── services/     # Business logic
│   │   └── core/        # Configuration
│   ├── requirements.txt   # Python dependencies
│   └── Dockerfile        # Container config
└── documents/             # Design documents
    ├── requirements/      # Requirements documents
    └── design/           # Design documents
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

### Frontend (stock-front/)

```bash
cd stock-front

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

# Format code
npm run prettier
```

### Python AI Service (py-service/)

```bash
cd py-service

# Create virtual environment (recommended)
python -m venv venv
source venv/bin/activate  # Linux/Mac
# or
venv\Scripts\activate     # Windows

# Install dependencies
pip install -r requirements.txt

# Run FastAPI development server
uvicorn app.main:app --reload --host 0.0.0.0 --port 8001

# Run with Docker
docker build -t stock-py-service .
docker run -p 8001:8001 stock-py-service
```

## Code Style Guidelines

### Java (Backend)

- **Java Version**: 8/9 (source/target 9 in pom.xml)
- **Framework**: Spring Boot 2.6.6
- **Naming**: CamelCase for classes/methods, UPPER_SNAKE for constants
- **Lombok**: Use `@Data`, `@Slf4j` annotations for boilerplate reduction
- **Imports**: Organize imports, no wildcard imports
- **Dependencies**: Prefer Spring Boot starters, use consistent versions from pom.xml

### TypeScript/React (Frontend)

- **Style**: Follow Prettier config (single quotes, trailing commas, 100 char width)
- **Imports**: Use `@/` alias for src/ directory imports
- **Components**: Functional components with hooks, PascalCase naming
- **Types**: Strict TypeScript enabled, always define prop interfaces
- **Formatting**: LF line endings, no prose wrap

### General

- **Error Handling**: Use try/catch with meaningful error messages
- **Logging**: Backend uses SLF4J/Logback; frontend uses console for dev only
- **Comments**: Javadoc for Java, JSDoc for TypeScript when needed
- **Git**: Follow conventional commit messages

## Testing Standards

### Java

- Use JUnit 5 (JUnit Jupiter)
- Test class naming: `*Test.java`
- Mock external dependencies with Mockito
- Integration tests in `src/test/java`

### TypeScript/React

- Use Jest with React Testing Library
- Test files: `*.test.tsx` or `*.spec.tsx`
- Mock API calls and external dependencies
- Test user interactions and component rendering

## Environment Setup

### Required

- Java JDK 21 (JAVA_HOME=/d/jdk-21.0.10)
- Maven 3.9.6 (MAVEN_HOME=/d/apache-maven-3.9.6)
- Node.js >= 12.0.0
- pnpm (preferred package manager)

### IDE

- Backend: IntelliJ IDEA or Eclipse with Lombok plugin
- Frontend: VS Code with ESLint and Prettier extensions

## Key Dependencies

### Backend

- Spring Boot Web, Data JPA, Quartz, WebSocket
- MyBatis-Plus for ORM
- DeepLearning4J for ML models
- Hutool for utilities
- EasyExcel for Excel processing

### Frontend

- UmiJS 4.x (framework)
- Ant Design 5.x + Pro Components
- React 18.x
- TypeScript 4.9.x
- Jest for testing

### Python AI Service

- FastAPI 0.109.x (framework)
- Uvicorn 0.27.x (ASGI server)
- PyTorch for FinBERT model
- TensorFlow for LSTM model
- httpx for HTTP client
- Redis for caching

## Development Workflow

1. Always run tests before committing
2. Ensure linting passes (`npm run lint` for frontend)
3. Type check TypeScript (`npm run tsc`)
4. Backend API runs on port 8080, frontend dev server on port 8000
5. Python AI service runs on port 8001
6. Proxy configured: `/api/*` → `http://localhost:8080`

## Notes

- Backend uses MongoDB and MySQL (configure in application.yaml)
- Redis for caching
- Quartz for scheduled tasks
- LSTM model for stock prediction
- T+1 trading strategy implementation
- Three-tier architecture: React → Spring Boot → Python FastAPI
- Docker Compose deployment (no K8s due to limited server resources)
<!-- PRPM_MANIFEST_START -->

<skills_system priority="1">
<usage>
When users ask you to perform tasks, check if any of the available skills below can help complete the task more effectively. Skills provide specialized capabilities and domain knowledge.

How to use skills (loaded into main context):
- Use the <path> from the skill entry below
- Invoke: Bash("cat <path>")
- The skill content will load into your current context
- Example: Bash("cat .openskills/backend-architect/SKILL.md")

Usage notes:
- Skills share your context window
- Do not invoke a skill that is already loaded in your context
</usage>

<available_skills>

<skill activation="lazy">
<name>skill-using-superpowers</name>
<description>Use when starting any conversation - establishes mandatory workflows for finding and using skills, including using Read tool before announcing usage, following brainstorming before coding, and creating TodoWrite todos for checklists</description>
<path>.openskills\skill-using-superpowers\SKILL.md</path>
</skill>

<skill activation="lazy">
<name>skill-brainstorming</name>
<description>Use when creating or developing anything, before writing code or implementation plans - refines rough ideas into fully-formed designs through structured Socratic questioning, alternative exploration, and incremental validation</description>
<path>.openskills\skill-brainstorming\SKILL.md</path>
</skill>

<skill activation="lazy">
<name>skill-writing-plans</name>
<description>Use when design is complete and you need detailed implementation tasks for engineers with zero codebase context - creates comprehensive implementation plans with exact file paths, complete code examples, and verification steps assuming engineer has minimal domain knowledge</description>
<path>.openskills\skill-writing-plans\SKILL.md</path>
</skill>

<skill activation="lazy">
<name>skill-executing-plans</name>
<description>Use when partner provides a complete implementation plan to execute in controlled batches with review checkpoints - loads plan, reviews critically, executes tasks in batches, reports for review between batches</description>
<path>.openskills\skill-executing-plans\SKILL.md</path>
</skill>

<skill activation="lazy">
<name>skill-test-driven-development</name>
<description>Use when implementing any feature or bugfix, before writing implementation code - write the test first, watch it fail, write minimal code to pass; ensures tests actually verify behavior by requiring failure first</description>
<path>.openskills\skill-test-driven-development\SKILL.md</path>
</skill>

<skill activation="lazy">
<name>skill-systematic-debugging</name>
<description>Use when encountering any bug, test failure, or unexpected behavior, before proposing fixes - four-phase framework (root cause investigation, pattern analysis, hypothesis testing, implementation) that ensures understanding before attempting solutions</description>
<path>.openskills\skill-systematic-debugging\SKILL.md</path>
</skill>

<skill activation="lazy">
<name>skill-requesting-code-review</name>
<description>Use when completing tasks, implementing major features, or before merging to verify work meets requirements - dispatches code-reviewer subagent to review implementation against plan or requirements before proceeding</description>
<path>.openskills\skill-requesting-code-review\SKILL.md</path>
</skill>

<skill activation="lazy">
<name>skill-receiving-code-review</name>
<description>Use when receiving code review feedback, before implementing suggestions, especially if feedback seems unclear or technically questionable - requires technical rigor and verification, not performative agreement or blind implementation</description>
<path>.openskills\skill-receiving-code-review\SKILL.md</path>
</skill>

<skill activation="lazy">
<name>skill-verification-before-completion</name>
<description>Use when about to claim work is complete, fixed, or passing, before committing or creating PRs - requires running verification commands and confirming output before making any success claims; evidence before assertions always</description>
<path>.openskills\skill-verification-before-completion\SKILL.md</path>
</skill>

<skill activation="lazy">
<name>skill-using-git-worktrees</name>
<description>Use when starting feature work that needs isolation from current workspace or before executing implementation plans - creates isolated git worktrees with smart directory selection and safety verification</description>
<path>.openskills\skill-using-git-worktrees\SKILL.md</path>
</skill>

<skill activation="lazy">
<name>skill-subagent-driven-development</name>
<description>Use when executing implementation plans with independent tasks in the current session - dispatches fresh subagent for each task with code review between tasks, enabling fast iteration with quality gates</description>
<path>.openskills\skill-subagent-driven-development\SKILL.md</path>
</skill>

<skill activation="lazy">
<name>skill-dispatching-parallel-agents</name>
<description>Use when facing 3+ independent failures that can be investigated without shared state or dependencies - dispatches multiple Claude agents to investigate and fix independent problems concurrently</description>
<path>.openskills\skill-dispatching-parallel-agents\SKILL.md</path>
</skill>

<skill activation="lazy">
<name>skill-root-cause-tracing</name>
<description>Use when errors occur deep in execution and you need to trace back to find the original trigger - systematically traces bugs backward through call stack, adding instrumentation when needed, to identify source of invalid data or incorrect behavior</description>
<path>.openskills\skill-root-cause-tracing\SKILL.md</path>
</skill>

<skill activation="lazy">
<name>skill-defense-in-depth</name>
<description>Use when invalid data causes failures deep in execution, requiring validation at multiple system layers - validates at every layer data passes through to make bugs structurally impossible</description>
<path>.openskills\skill-defense-in-depth\SKILL.md</path>
</skill>

<skill activation="lazy">
<name>skill-condition-based-waiting</name>
<description>Use when tests have race conditions, timing dependencies, or inconsistent pass/fail behavior - replaces arbitrary timeouts with condition polling to wait for actual state changes, eliminating flaky tests from timing guesses</description>
<path>.openskills\skill-condition-based-waiting\SKILL.md</path>
</skill>

<skill activation="lazy">
<name>skill-testing-anti-patterns</name>
<description>Use when writing or changing tests, adding mocks, or tempted to add test-only methods to production code - prevents testing mock behavior, production pollution with test-only methods, and mocking without understanding dependencies</description>
<path>.openskills\skill-testing-anti-patterns\SKILL.md</path>
</skill>

<skill activation="lazy">
<name>skill-testing-skills-with-subagents</name>
<description>Use when creating or editing skills, before deployment, to verify they work under pressure and resist rationalization - applies RED-GREEN-REFACTOR cycle to process documentation by running baseline without skill, writing to address failures, iterating to close loopholes</description>
<path>.openskills\skill-testing-skills-with-subagents\SKILL.md</path>
</skill>

<skill activation="lazy">
<name>skill-writing-skills</name>
<description>Use when creating new skills, editing existing skills, or verifying skills work before deployment - applies TDD to process documentation by testing with subagents before writing, iterating until bulletproof against rationalization</description>
<path>.openskills\skill-writing-skills\SKILL.md</path>
</skill>

<skill activation="lazy">
<name>skill-sharing-skills</name>
<description>Use when you&apos;ve developed a broadly useful skill and want to contribute it upstream via pull request - guides process of branching, committing, pushing, and creating PR to contribute skills back to upstream repository</description>
<path>.openskills\skill-sharing-skills\SKILL.md</path>
</skill>

<skill activation="lazy">
<name>skill-finishing-a-development-branch</name>
<description>Use when implementation is complete, all tests pass, and you need to decide how to integrate the work - guides completion of development work by presenting structured options for merge, PR, or cleanup</description>
<path>.openskills\skill-finishing-a-development-branch\SKILL.md</path>
</skill>

</available_skills>
</skills_system>

<!-- PRPM_MANIFEST_END -->
