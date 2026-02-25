# AGENTS.md - AI Shopping Project Guidelines

## Build Commands

### Maven (Backend)
```bash
# Full build
D:\apache-maven-3.6.2\bin\mvn clean install

# Compile only
D:\apache-maven-3.6.2\bin\mvn clean compile

# Run app
D:\apache-maven-3.6.2\bin\mvn spring-boot:run -pl backend

# Run all tests
D:\apache-maven-3.6.2\bin\mvn test

# Run single test class
D:\apache-maven-3.6.2\bin\mvn test -pl backend -Dtest=ClassName

# Run single test method
D:\apache-maven-3.6.2\bin\mvn test -pl backend -Dtest=ClassName#methodName

# Package without tests
D:\apache-maven-3.6.2\bin\mvn clean package -DskipTests
```

### npm (Frontend)
```bash
cd frontend

# Install dependencies
npm install

# Start dev server
npm run dev

# Production build
npm run build

# Format with Prettier
npm run format

# Setup Umi (post-install)
npm run setup
```

## Code Style

### Java (Backend)

**Naming**: PascalCase classes, camelCase methods/variables, UPPER_SNAKE_CASE constants, lowercase packages.

**Imports**: Group by java.*, javax.*, org.*, com.*. No wildcards. Static imports last. Remove unused.

**Formatting**: 4 spaces indent, 120 char line limit, K&R braces, 1 blank line between methods, 2 between classes.

**Types**: Use `var` only for obvious types. REST controllers use `@RestController` + `@RequestMapping`. Use `@Valid` for validation, `@Slf4j` for logging. Public APIs return `ResponseEntity<T>`.

**ORM (MyBatis-Plus)**:
- Entities use `@TableName` and `@TableField` annotations
- Extend `BaseMapper<T>` for basic CRUD operations
- Use `QueryWrapper<T>` or `LambdaQueryWrapper<T>` for complex queries
- Use `@TableId` for primary key fields
- Use `IService<T>` and `ServiceImpl<M, T>` for service layer

**Error Handling**: Use `@ControllerAdvice` globally. Custom exceptions extend `RuntimeException`. Error responses include code, message, timestamp, path. Never expose stack traces. Use `Optional<T>` not null.

### TypeScript/React (Frontend)

**Naming**: PascalCase components, camelCase hooks (use* prefix), PascalCase types, match filenames to exports.

**Imports**: React first, third-party second, absolute (@/*) third, relative last. Sort alphabetically within groups.

**Formatting**: 2 spaces indent, single quotes, trailing commas, semicolons required, 100 char line limit.

**Types**: Strict mode enabled. No `any` (use `unknown`). Define interfaces for API responses. Use discriminated unions for complex state.

**React**: Functional components with hooks. Define Props interfaces. Use `React.FC<Props>`. Destructure props in parameters. Custom hooks for reusable logic.

## Project Structure

```
ai-shopping/
├── backend/              # Spring Boot 3.0 + Java 17 + MyBatis-Plus
│   └── src/main/java/com/example/aishopping/
│       ├── config/       # Configuration (MyBatisPlusConfig)
│       ├── controller/   # REST controllers
│       ├── service/      # Business logic (extends ServiceImpl)
│       ├── mapper/       # MyBatis-Plus mappers (extends BaseMapper)
│       ├── entity/       # MyBatis-Plus entities (@TableName)
│       ├── dto/          # Data transfer objects
│       ├── exception/    # Custom exceptions
│       └── util/         # Utilities
│
├── frontend/             # React 18 + Ant Design Pro
│   └── src/
│       ├── pages/        # Page components
│       ├── components/   # Reusable components
│       ├── services/     # API calls
│       ├── models/       # Data models
│       ├── utils/        # Utilities
│       └── hooks/        # Custom hooks
│
└── doc/                  # Documentation
    ├── requirements/     # PRD documents
    └── design/           # Design documents
```

## Testing

**Java**: Use JUnit 5. Test classes: `*Test`. Use `@SpringBootTest` for integration, `@WebMvcTest` for controllers. Mock with Mockito. Name: `shouldDoSomethingWhenCondition()`.

**Frontend**: Jest + React Testing Library. Test files: `*.test.ts`. Test user interactions, not implementation. Mock APIs with MSW.

## Git Workflow

**Commits**: `type(scope): subject`. Types: feat, fix, docs, style, refactor, test, chore. Imperative mood, no period. Example: `feat(product): add search by name`

**Before Commit**:
1. Run tests: `mvn test` (backend), `npm test` (frontend)
2. Format: `npm run format`
3. Check console errors
4. Review: `git diff`

## Environment
- **Java**: 17+
- **Maven**: 3.6.2+ (at D:\apache-maven-3.6.2)
- **Node.js**: 16+
- **npm**: 8+

## API Standards
- Base path: `/api`
- RESTful: GET/POST/PUT/DELETE
- JSON request/response
- Status codes: 200 OK, 201 Created, 400 Bad Request, 404 Not Found, 500 Server Error
