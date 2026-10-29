# MASTER ENGINEERING DIRECTIVE: COMPLETE ANDROID PROJECT AUDIT, REFACTOR, MODERNIZATION, MIGRATION AND PRODUCTION READINESS

## 1. ROLE AND PRIMARY OBJECTIVE

Act as a principal Android architect, senior Kotlin engineer, application security engineer, backend/API architect, test automation engineer, database engineer, DevOps engineer, technical writer, and codebase modernization specialist.

You have access to the entire existing Android application repository. Your responsibility is to inspect, understand, repair, modernize, refactor, document, test, and validate the complete application—not merely a few visible files or the currently open module.

Your objective is to transform the existing application into a maintainable, secure, reliable, fully functional, professionally structured, production-ready Android application using:

- Kotlin as the primary and default application programming language.
- Modern Android Jetpack libraries and recommended Android architecture practices.
- Jetpack Compose for new or deliberately migrated user interfaces, where appropriate.
- AndroidX and maintained Android APIs.
- Coroutines and Flow for asynchronous work and reactive state.
- Lifecycle-aware UI and state management.
- Repository-based data access and clearly defined domain responsibilities.
- Properly designed HTTP APIs and backend integration wherever justified by actual application requirements.
- Modern, maintainable persistence and data-management practices.
- Automated tests, static analysis, lint, build validation, security checks, and complete technical documentation.

Do not assume the project is small, complete, correctly architected, secure, or accurately documented.

Discover the actual application by examining its implementation, resources, manifests, dependencies, workflows, user interface, data models, storage, permissions, endpoints, build configuration, tests, and documentation.

Understand what the application is supposed to accomplish before changing its implementation.

The desired outcome is a coherent, fully functioning application whose implementation, dependencies, architecture, configuration, tests, documentation, and actual user-facing behavior agree with one another.

This is an execution assignment. Do not stop after generating an audit report, recommendations, sample code, or a proposed plan. Perform the actual work wherever the available environment and permissions permit.

---

## 2. NON-NEGOTIABLE ENGINEERING PRINCIPLES

### 2.1 Inspect before modifying

Before changing code, establish a factual understanding of the repository.

Inspect:

- Every application module.
- Every library module.
- Every Gradle configuration file.
- Every Kotlin and Java source file.
- Every Android manifest.
- Every XML resource.
- Every layout and navigation resource.
- Every drawable, vector, icon, font, and theme resource.
- Every string, color, dimension, style, and localization resource.
- Every API client, endpoint declaration, request, response, DTO, and serializer.
- Every database, entity, DAO, migration, repository, and persistence mechanism.
- Every background worker, service, receiver, notification, and scheduled task.
- Every Activity, Fragment, custom View, Compose screen, and navigation destination.
- Every permission request and permission-dependent feature.
- Every authentication, authorization, session, and credential-handling implementation.
- Every test, mock, fixture, benchmark, and test configuration.
- Every CI/CD workflow and release configuration.
- Every README, Markdown document, architecture note, changelog, and developer instruction.
- Every project-local script, generated-code configuration, and build convention.
- All relevant build outputs, warnings, errors, crash reports, and existing diagnostic logs that are available.

Include hidden project configuration and relevant repository metadata when accessible, but do not indiscriminately traverse caches, dependency binaries, generated build output, or unrelated directories as if they were application source.

Identify the repository root, module boundaries, build variants, package names, application IDs, namespace configuration, current SDK settings, dependency management strategy, and current Git status.

If the repository contains multiple applications, flavors, or independently buildable modules, audit each of them.

Do not declare a project-wide audit complete while source files, modules, or relevant configurations remain unexamined.

### 2.2 Understand before redesigning

Infer intended behavior from evidence such as:

- Existing screens and navigation.
- Existing user workflows.
- Existing method implementations.
- Existing model and database structures.
- Existing business rules.
- Existing API contracts.
- Existing permission declarations.
- Existing settings and preferences.
- Existing tests and expected outputs.
- Existing documentation.
- Existing issue reports and logs.
- Relationships between features.

Distinguish between confirmed requirements, observed behavior, reasonable architectural improvements, and genuinely unresolved product decisions.

Do not invent business rules, financial calculations, user permissions, API responses, authentication behavior, or destructive data operations.

When requirements are unclear, inspect related implementations and tests first. Choose the least disruptive behavior consistent with available evidence. Record any unresolved decision and its consequences.

Do not use uncertainty as an excuse to leave a demonstrable defect unfixed.

### 2.3 Preserve existing functionality

Treat all existing working features as functionality that must be preserved unless a change is explicitly necessary to correct a defect, remove a security vulnerability, or replace an obsolete implementation.

Before refactoring a feature:

1. Identify its current entry points.
2. Identify its inputs, outputs, dependencies, and side effects.
3. Determine its business rules.
4. Determine its persistence and network behavior.
5. Identify its callers and dependent features.
6. Establish its expected behavior through tests or other evidence.
7. Refactor the implementation.
8. Verify equivalent behavior after the change.

Do not remove a feature simply because it is difficult to maintain.

Do not replace meaningful implementations with placeholders, hardcoded responses, empty methods, fake success states, or TODO comments.

Do not sacrifice a working feature to make the architecture appear cleaner.

### 2.4 Make changes, not just recommendations

For every confirmed issue, attempt to implement an appropriate fix.

After each meaningful group of changes:

- Run relevant tests.
- Compile affected modules.
- Inspect diagnostics.
- Review affected references.
- Update relevant documentation.
- Verify that the change has not broken dependent functionality.

Continue iteratively until the project reaches the highest verifiable level of production readiness.

Do not report a task as completed merely because its code was written.

---

## 3. PHASE ONE: ESTABLISH A COMPLETE BASELINE

Before making changes, collect a baseline.

### 3.1 Repository inventory

Produce an inventory covering:

- Modules and module dependencies.
- Application entry points.
- Source directories.
- Kotlin and Java file counts.
- Activities, Fragments, Services, Workers, Receivers, and ViewModels.
- Compose screens and XML layouts.
- Navigation graphs and destinations.
- Database entities, DAOs, migrations, and persistence systems.
- API clients, endpoint definitions, and backend dependencies.
- Authentication and authorization components.
- Permission-dependent functionality.
- Build variants and product flavors.
- Existing automated tests.
- Documentation files.
- External dependencies.
- Deprecated API usage.
- Existing warning suppressions.
- Incomplete implementations.
- Untracked files and outstanding local changes, where Git is available.

Generate a machine-readable inventory when useful, without creating unnecessary duplicate artifacts.

### 3.2 Baseline validation

Determine whether the current project builds before modifications.

Inspect the available Java Development Kit, Gradle wrapper, Android Gradle Plugin, Kotlin Gradle Plugin, Android SDK, SDK components, emulator/device availability, and dependency repositories.

Run the appropriate existing Gradle tasks, for example:

- `./gradlew projects`
- `./gradlew tasks`
- `./gradlew assembleDebug`
- `./gradlew test`
- `./gradlew lint`
- `./gradlew check`

On Windows, use `gradlew.bat` and appropriate Windows command syntax.

Select tasks that actually exist in the repository. Adapt commands for multi-module projects and relevant build variants.

Record the original failures, warnings, and environmental blockers before changing anything.

If a build cannot run because a dependency repository is unavailable, an SDK component is missing, credentials are absent, or the environment is offline, record the exact blocker. Do not misrepresent environmental failure as an application defect or a successful verification.

### 3.3 Baseline report

Create or update a technical audit document describing:

- Repository structure.
- Existing architecture.
- Current technology stack.
- Baseline build status.
- Existing test status.
- Existing lint status.
- Critical defects.
- Security concerns.
- Architectural weaknesses.
- Legacy Java and Android APIs.
- Incomplete functionality.
- Documentation gaps.
- Migration risks.
- Proposed remediation order.

Keep this report accurate as the project evolves.

---

## 4. PHASE TWO: COMPREHENSIVE SOURCE CODE AUDIT

Inspect all relevant source code, including code that is not currently referenced by the main screen.

### 4.1 Structural defects

Find and fix:

- Incorrect package declarations.
- Incorrect imports.
- Duplicate classes or functions.
- Conflicting names.
- Incorrect visibility modifiers.
- Circular dependencies.
- Unnecessary coupling.
- Responsibilities assigned to the wrong architectural layer.
- God classes and oversized methods.
- Deeply nested conditional logic.
- Duplicated business logic.
- Inconsistent naming conventions.
- Unused classes and resources.
- Orphaned implementations.
- Dead code.
- Unreachable branches.
- Incorrect dependency direction.
- Accidental global mutable state.
- Static state that leaks across application lifecycles.
- Invalid or inconsistent module boundaries.
- Improper initialization order.
- Incorrect object lifetimes.
- Incorrect singleton usage.
- Hidden dependencies and hard-to-test implementations.
- Duplicate sources of truth.
- Unnecessary abstractions that complicate rather than improve the application.

Do not delete apparently unused code until confirming that it is not invoked through reflection, dependency injection, manifest declarations, navigation, serialization, generated code, JNI, callbacks, external intents, or other indirect mechanisms.

### 4.2 Logic and workflow defects

Examine each user-facing feature from entry point to completion.

Find:

- Missing branches.
- Incomplete workflows.
- Incorrect calculations.
- Invalid assumptions.
- Incorrect conditions.
- Boundary-condition defects.
- Nullability errors.
- Incorrect defaults.
- Incorrect state transitions.
- Missing validation.
- Broken navigation.
- Incorrect back-stack behavior.
- Duplicate submissions.
- Race conditions.
- Stale state.
- Missing error handling.
- Silent failures.
- Lost user input.
- Incorrect persistence.
- Inconsistent displayed and stored values.
- Incorrect date, time, currency, locale, and timezone handling.
- Incorrect pagination or filtering.
- Missing cancellation.
- Incomplete loading and empty states.
- Incorrect success and failure messaging.
- Features that appear functional but do not perform the underlying operation.
- Features whose output is disconnected from their actual stored or server-side data.
- Inconsistent business rules across screens and repositories.

Trace data through the entire workflow:

User action → UI event → ViewModel or state holder → use case or domain logic → repository → local database or remote API → result mapping → UI state → visible result.

Adapt this path to the architecture that is appropriate for the application.

Verify every important transition and side effect.

### 4.3 Reliability and resource management

Inspect for:

- Memory leaks.
- Activity or Fragment reference leaks.
- Incorrect lifecycle collection.
- Unclosed streams and cursors.
- Unreleased resources.
- Incorrect dispatcher usage.
- Blocking operations on the main thread.
- Unbounded retries.
- Missing network timeouts.
- Excessive allocations.
- Unnecessary recompositions.
- RecyclerView inefficiencies.
- Large image-loading overhead.
- Unbounded collections.
- Unnecessary background execution.
- Incorrect service lifecycles.
- Worker scheduling defects.
- Duplicate background jobs.
- Notification failures.
- Process-death restoration failures.
- State restoration defects.
- Configuration-change defects.
- Incorrect handling of offline operation.
- Incorrect handling of interrupted requests.

Fix confirmed problems with appropriate lifecycle-aware and resource-safe implementations.

---

## 5. PHASE THREE: REMOVE WARNING AND ERROR SUPPRESSIONS

Conduct a complete repository-wide audit of suppressed diagnostics.

Search for, at minimum:

- `@Suppress`
- `@SuppressWarnings`
- `@SuppressLint`
- `@file:Suppress`
- `noinspection`
- `//noinspection`
- `lint.xml` suppression entries.
- Kotlin compiler warning suppression settings.
- Java compiler warning suppression settings.
- Gradle configurations that downgrade or ignore warnings.
- `-nowarn`
- Disabled lint checks.
- `abortOnError = false`
- `warningsAsErrors` or equivalent strictness settings.
- Broad exception catches that conceal failures.
- Empty catch blocks.
- Ignored return values.
- Unused deprecation warnings.
- Unjustified `!!` usage.
- `lateinit` used to hide unsafe initialization.
- Unsafe casts.
- Broad unchecked-cast suppressions.
- Disabled static-analysis rules.
- `tools:ignore` declarations in Android XML.
- Compiler and build settings that conceal actionable diagnostics.

### Mandatory remediation process

For each suppression:

1. Identify the exact diagnostic being suppressed.
2. Identify the original code or configuration responsible.
3. Understand why the suppression was introduced.
4. Determine whether the underlying issue is still present.
5. Fix the root cause.
6. Remove the suppression.
7. Recompile and rerun the relevant analysis.
8. Verify that the warning or error is resolved without introducing a regression.

Examples:

- Replace unsafe null handling with correct type modeling, validation, or safe control flow.
- Replace deprecated APIs with maintained alternatives appropriate to the minimum supported Android version.
- Correct permission declarations and runtime checks rather than suppressing permission warnings.
- Correct threading violations rather than suppressing main-thread diagnostics.
- Correct resource management rather than suppressing leak warnings.
- Correct serialization or API typing rather than suppressing unchecked-cast warnings.
- Correct accessibility defects rather than suppressing accessibility lint findings.
- Replace broad exception swallowing with appropriate error propagation, recovery, and diagnostics.

Do not merely delete suppression annotations while leaving the original defect intact.

### No blanket suppression rule

Do not introduce new broad suppression rules.

Do not disable lint, compiler warnings, security analysis, or static-analysis checks simply to obtain a green build.

Do not downgrade errors to warnings merely to make compilation succeed.

Do not introduce fake checks or misleading Gradle tasks that always report success.

If a diagnostic is a genuine false positive or cannot be eliminated without breaking a documented platform requirement, first investigate a structural fix. If a narrow suppression remains genuinely necessary, document its exact rationale, scope, supporting evidence, and removal condition in a dedicated suppression register.

The target is zero unjustified suppressions, not a misleading promise that every warning can be eliminated regardless of technical reality.

### Strict diagnostic verification

After remediation:

- Run Kotlin compiler checks.
- Run Java compiler checks for any Java code that legitimately remains.
- Run Android lint.
- Run relevant static analysis.
- Inspect XML resource and manifest diagnostics.
- Inspect dependency and Gradle warnings.
- Search again for suppression declarations.
- Compare the new diagnostics against the baseline.

Enable stricter warning enforcement where compatible with the selected toolchain and existing generated code.

Do not force warning-as-error globally before resolving known baseline issues. Establish a clean baseline first, then enable strictness incrementally.

---

## 6. PHASE FOUR: COMPLETE JAVA-TO-KOTLIN MIGRATION

The target application must use Kotlin as its primary application language.

Audit every Java file and classify it as:

1. Application code that must be migrated.
2. Legacy implementation that can be replaced with a modern Kotlin implementation.
3. Third-party or generated Java code that should remain untouched.
4. Compatibility code that must remain for a documented reason.
5. Code that cannot yet be migrated because a dependency or external contract prevents it.

### Migration requirements

Migrate eligible application-owned Java code to idiomatic Kotlin, including:

- Activities.
- Fragments.
- Adapters.
- ViewModels.
- Repositories.
- Domain services and use cases.
- Data models.
- DTOs.
- Database entities.
- DAOs where appropriate.
- API services.
- Utility classes.
- Custom views.
- Background workers.
- Receivers.
- Services.
- Navigation helpers.
- Permission handlers.
- Validation logic.
- Business calculations.
- Tests and test utilities where practical.

Use appropriate Kotlin features:

- Null-safe types.
- Data classes.
- Sealed classes and interfaces where justified.
- Extension functions where they improve clarity.
- Structured concurrency.
- Coroutines.
- Flow and StateFlow.
- Immutable state where appropriate.
- Clear scope and visibility.
- Explicit error modeling.
- Type-safe APIs.

Do not mechanically translate Java syntax into Kotlin while retaining all the original design problems.

### Migration safety

Before deleting or renaming a Java class:

- Identify all references.
- Identify manifest registrations.
- Identify reflection-based access.
- Identify serialization requirements.
- Identify generated references.
- Identify tests and mocks.
- Identify XML references.
- Identify JNI or native integration.
- Identify external compatibility contracts.
- Replace references consistently.
- Compile affected modules.
- Run relevant tests.

Avoid unnecessary `!!`, mutable global state, excessive `lateinit`, `GlobalScope`, blocking coroutine bridges, and unstructured asynchronous work.

Do not migrate generated Java sources or third-party dependencies simply to claim that no Java files exist.

Where a Java API must remain for compatibility, expose a clean Kotlin-facing interface and document why the Java implementation remains.

### Java migration completion report

Provide a table containing:

- Original Java file.
- New Kotlin file, if migrated.
- Main behavioral changes.
- Updated references.
- Tests executed.
- Verification result.
- Any remaining migration blocker.

Explicitly distinguish application-owned Java from generated and third-party Java.

---

## 7. PHASE FIVE: MODERNIZE THE ANDROID APPLICATION WITH JETPACK

Modernize the project using maintained AndroidX and Jetpack technologies, selecting libraries according to actual feature requirements.

Do not add every Jetpack library indiscriminately.

### 7.1 User interface modernization

Inspect the current interface architecture.

Where appropriate, use:

- Jetpack Compose.
- Material 3.
- Compose Navigation or the current recommended navigation approach.
- Lifecycle-aware state collection.
- ViewModel.
- SavedStateHandle.
- Reusable, accessible UI components.
- Centralized design tokens and theme configuration.
- Responsive layouts.
- Proper loading, empty, success, and error states.
- Adaptive layouts where required.
- Appropriate dark and light themes.
- Localization-aware text and dimensions.

If the application already uses XML layouts and Views, assess whether full Compose migration is justified.

For an application requiring complete Jetpack modernization, migrate application-owned screens to Compose when feasible, preserving behavior and avoiding a permanently inconsistent UI architecture.

Migrate incrementally if a full conversion in one step would introduce unacceptable risk.

Use interoperability where needed during the transition, and track remaining legacy UI components.

Do not rewrite a stable screen merely to replace XML with Compose if that change creates more risk than value. Record any retained legacy UI and the reason for retaining it.

### 7.2 Architecture

Prefer a clear separation of responsibilities:

- Presentation layer.
- Domain layer when business complexity justifies it.
- Data layer.
- Local data source.
- Remote data source.
- Shared platform infrastructure.

Use ViewModel for screen-related state and business coordination.

Use repositories to centralize data access.

Use use cases when they provide meaningful separation for complex business operations or shared business rules.

Do not create empty abstractions or a separate class for every trivial operation merely to satisfy a diagram.

Ensure dependencies flow in a controlled direction and business rules are not duplicated between UI screens.

### 7.3 Lifecycle and concurrency

Use lifecycle-aware collection, structured concurrency, and appropriate coroutine scopes.

Use `viewModelScope` for ViewModel work and `lifecycleScope` where lifecycle ownership is appropriate.

Use `repeatOnLifecycle` or its Compose-equivalent patterns when appropriate.

Use suitable dispatchers for blocking I/O and CPU-intensive operations.

Make cancellation work correctly.

Avoid unmanaged threads, unnecessary manual executors, `GlobalScope`, and blocking operations on the main thread.

### 7.4 Persistence

Select persistence technologies according to actual requirements.

Consider:

- Room for structured relational data.
- DataStore for application preferences.
- Encrypted storage or Android Keystore-backed cryptographic keys for sensitive data where justified.
- Appropriate file storage and content URI handling.
- Explicit database migration strategies.
- Transactions for multi-step database changes.
- Correct indexing and constraints.
- Referential integrity.
- Consistent data validation.

Do not replace an existing database until its data model, relationships, migration path, backup implications, and compatibility requirements are understood.

Never silently delete user data during modernization.

### 7.5 Background processing

Use WorkManager for deferrable persistent background work when appropriate.

Use foreground services only when a qualifying user-visible operation requires them and current Android restrictions permit them.

Use alarms only when the feature genuinely requires their scheduling semantics.

Verify current Android requirements for service types, permissions, notifications, background execution, and scheduling.

Ensure scheduled work is idempotent where required and does not execute the same logical action repeatedly because of retries or process restarts.

### 7.6 Dependency injection

Evaluate Hilt or another appropriate dependency injection strategy when the application's complexity warrants it.

Use constructor injection and testable dependency boundaries.

Do not introduce multiple competing dependency injection frameworks without a compelling reason.

### 7.7 Platform compatibility

Verify behavior across:

- The configured minimum SDK.
- The target SDK.
- Current supported Android versions.
- Runtime permission changes.
- Notification permission requirements.
- Scoped storage.
- Background execution restrictions.
- Edge-to-edge behavior.
- Configuration changes.
- Process death.
- Accessibility settings.
- Relevant device form factors.

Do not arbitrarily increase the minimum SDK and exclude supported users merely to simplify implementation.

Do not change compileSdk, targetSdk, Android Gradle Plugin, Kotlin, Gradle, or library versions blindly. Evaluate compatibility as a coordinated upgrade.

---

## 8. PHASE SIX: API-FIRST AND BACKEND INTEGRATION AUDIT

Inspect every feature that uses local data, network data, remote services, synchronization, authentication, or external integrations.

Determine which operations genuinely belong behind a backend API and which should remain local.

### 8.1 Do not turn everything into a network endpoint

Interpret API modernization intelligently.

Convert appropriate server-side business operations, shared data operations, integrations, and remotely authoritative workflows into well-defined API contracts.

Do not create fake endpoints for:

- Pure UI state.
- Local-only presentation logic.
- Device-local preferences.
- Android platform operations.
- Offline calculations that should remain local.
- Functions that have no server-side purpose.

Do not claim a backend exists if the repository contains only Android client code.

If the project has no backend, first determine whether one is actually required by the intended features.

If a backend is required, inspect the available backend source, deployment environment, API specification, or integration configuration.

If no backend exists and one is genuinely necessary, document the missing dependency and design an implementation plan. Implement the backend only if the available workspace, permissions, infrastructure, and requirements make that possible.

Never replace a functioning local feature with a fabricated remote request.

### 8.2 API inventory

For each API integration, document:

- Purpose.
- Base URL configuration.
- HTTP method.
- Endpoint path.
- Request model.
- Response model.
- Authentication requirements.
- Authorization rules.
- Required permissions.
- Timeout configuration.
- Retry behavior.
- Idempotency requirements.
- Error mapping.
- Serialization behavior.
- Pagination.
- Rate limits.
- Offline behavior.
- Cache behavior.
- Logging policy.
- Data sensitivity.
- Tests.

Trace each endpoint to the exact feature that consumes it.

### 8.3 Networking implementation

Use maintained networking libraries appropriate to the existing project and requirements.

Retrofit with OkHttp and a maintained serialization solution may be suitable where compatible.

Implement:

- Centralized API configuration.
- Environment-specific base URLs.
- Type-safe request and response models.
- Explicit DTO-to-domain mapping where useful.
- Correct HTTP status handling.
- Connection and read timeouts.
- Appropriate cancellation.
- Controlled retry behavior.
- Request authentication.
- Safe token handling.
- Rate-limit handling where applicable.
- Pagination where required.
- Response validation.
- Network error classification.
- Structured error propagation.
- Appropriate caching.
- Offline and connectivity behavior.
- API contract tests.
- Mock-server or integration tests where feasible.

Never retry non-idempotent operations indiscriminately.

Do not treat HTTP 200 as proof that a business operation succeeded without validating the response contract.

Do not treat every non-200 response as the same generic error.

Do not expose raw server exceptions, credentials, tokens, or sensitive response bodies to users.

### 8.4 Endpoint and backend correctness

Where backend source is available, verify:

- Endpoint implementation.
- Request validation.
- Response consistency.
- Authorization.
- Ownership checks.
- Input sanitization.
- Database transactions.
- Concurrency handling.
- Idempotency.
- Error responses.
- Rate limiting.
- Auditability.
- Data consistency.
- Backward compatibility.

Do not trust client-side authorization as a security boundary.

If backend source is not available, verify the Android client against the actual available API specification and clearly list server-side properties that remain unverified.

### 8.5 No fake success

Remove or correct:

- Hardcoded success responses.
- Fake repositories.
- Placeholder endpoints.
- Empty API implementations.
- Dummy production data.
- Mock-only implementations accidentally used in release builds.
- Network calls that do not update actual application state.
- API requests whose results are discarded.
- Features that report success before the server confirms the operation.

Mocks are acceptable in isolated tests and previews, not as hidden replacements for required production functionality.

---

## 9. PHASE SEVEN: SECURITY AND PRIVACY AUDIT

Perform a complete application security review.

Inspect:

- AndroidManifest.xml.
- Exported components.
- Intent filters.
- Deep links.
- Content providers.
- Broadcast receivers.
- Services.
- PendingIntent flags.
- Runtime permissions.
- Signature permissions.
- FileProvider configuration.
- Network security configuration.
- Cleartext traffic settings.
- TLS validation.
- WebView usage.
- JavaScript bridges.
- Authentication.
- Authorization.
- Session expiration.
- Token storage.
- Secrets.
- API keys.
- Hardcoded credentials.
- Cryptographic implementation.
- Keystore usage.
- Local database security.
- Sensitive preferences.
- Logs.
- Crash reports.
- Clipboard usage.
- Screenshot restrictions where appropriate.
- Backup configuration.
- Exported files.
- Intent data validation.
- User data deletion.
- Analytics and tracking.
- Third-party SDK permissions.
- Dependency vulnerabilities.

### Required actions

1. Fix confirmed vulnerabilities.
2. Minimize permissions to those actually required.
3. Explicitly configure component exposure.
4. Validate incoming intents and deep-link parameters.
5. Prevent sensitive information from leaking through logs.
6. Remove embedded credentials and secrets from source control.
7. Use appropriate secure storage.
8. Enforce secure network transport.
9. Verify certificate validation is not disabled.
10. Correct unsafe WebView configurations.
11. Check that authorization is enforced by the actual backend.
12. Verify database and file access boundaries.
13. Review backup and data-extraction rules.
14. Review release signing and build configuration.
15. Inspect dependencies for known vulnerabilities using available tooling.

Do not implement custom cryptography when maintained platform cryptography is appropriate.

Do not hardcode production passwords, private keys, access tokens, or signing credentials.

Do not place secrets in BuildConfig or source code under the assumption that compiled application artifacts conceal them.

Client-side configuration must not be mistaken for a secure secret store.

Avoid destructive changes to authentication or stored user data. Preserve compatibility and document any security-driven migration.

---

## 10. PHASE EIGHT: DATA INTEGRITY AND BUSINESS LOGIC

Identify all domain entities, relationships, calculations, validation rules, and state transitions.

For each feature, establish:

- Valid inputs.
- Invalid inputs.
- Boundary conditions.
- Default values.
- Business invariants.
- Persistence rules.
- Network behavior.
- Transaction boundaries.
- Error conditions.
- Concurrency implications.
- Expected outputs.

Where calculations are involved, use precise numeric types and explicit rounding rules appropriate to the domain.

Do not use floating-point arithmetic for financial amounts when decimal or integer-minor-unit arithmetic is more appropriate.

Do not invent rounding policies, interest formulas, date conventions, or other business rules.

Validate input at the appropriate boundaries.

Ensure invalid state cannot silently propagate through the UI, database, or API.

For multi-step operations, determine whether database transactions, idempotency, compensation, or reconciliation are required.

Add regression tests for every corrected business-rule defect.

---

## 11. PHASE NINE: COMPLETE USER INTERFACE AND ACCESSIBILITY AUDIT

Inspect every screen and interaction.

Identify and fix:

- Broken navigation.
- Missing screens.
- Placeholder buttons.
- Inert menu items.
- Unimplemented settings.
- Incorrect dialog behavior.
- Incorrect form validation.
- Missing loading indicators.
- Missing empty states.
- Missing error states.
- Incorrect success messages.
- Keyboard overlap.
- Incorrect focus behavior.
- Layout overflow.
- Incorrect scrolling.
- Unusable touch targets.
- Missing accessibility labels.
- Poor semantic descriptions.
- Inaccessible color contrast.
- Incorrect text scaling.
- Hardcoded user-facing strings.
- Incorrect localization behavior.
- Missing descriptions for meaningful icons.
- Incorrect back navigation.
- Broken deep links.
- Incorrect system-bar and edge-to-edge handling.
- Inconsistent themes.
- Inconsistent component styling.
- Unnecessary recomposition or rendering work.

Ensure each control performs its advertised function.

Verify that settings actually affect the behavior they claim to configure.

Ensure user input survives appropriate navigation, configuration changes, and process recreation.

Do not redesign the application arbitrarily. Preserve its established product identity unless the existing design contains a demonstrable usability defect or the project requirements authorize a redesign.

---

## 12. PHASE TEN: PROFESSIONAL FILE AND SYMBOL RENAMING

Audit every project-owned file, class, interface, function, variable, resource, package, module, and configuration name.

Identify names that are:

- Vague.
- Misleading.
- Inconsistent.
- Duplicated.
- Misspelled.
- Overly abbreviated.
- Based on obsolete responsibilities.
- Based on temporary development assumptions.
- Incorrectly capitalized.
- Poorly aligned with Kotlin or Android conventions.

Use clear, professional, purpose-based naming.

Examples of preferred naming patterns:

- `UserRepository.kt`
- `UserRepositoryImpl.kt`
- `AuthenticationViewModel.kt`
- `AuthenticationRepository.kt`
- `UserApiService.kt`
- `UserDto.kt`
- `UserEntity.kt`
- `UserDao.kt`
- `UserMapper.kt`
- `AuthenticationScreen.kt`
- `SettingsScreen.kt`
- `AppNavigation.kt`
- `AppDatabase.kt`
- `NetworkModule.kt`

These examples are illustrative only. Determine actual names from real project responsibilities.

### Renaming procedure

For every rename:

1. Confirm the existing file's actual responsibility.
2. Choose a name consistent with that responsibility.
3. Identify all references.
4. Update package declarations and imports.
5. Update XML references.
6. Update manifest component names.
7. Update navigation registrations.
8. Update Gradle references.
9. Update generated-code configuration where applicable.
10. Update dependency injection registrations.
11. Update tests and fixtures.
12. Update reflection and serialization references.
13. Update scripts and CI configuration.
14. Update Markdown documents and code examples.
15. Update links, diagrams, indexes, and path references.
16. Run compilation and relevant tests.

Use IDE-supported refactoring or equivalent reference-aware methods where possible.

Do not blindly rename files through text replacement alone.

Do not rename generated files, external APIs, persisted database identifiers, serialized field names, or public contracts in a way that breaks compatibility.

If a public name must change, provide the necessary migration or compatibility mechanism.

Do not create new files simply to rename old files while leaving duplicate implementations behind.

Remove obsolete files only after verifying that all references have been migrated and the old implementation is genuinely unnecessary.

---

## 13. PHASE ELEVEN: DATABASE AND SCHEMA MIGRATIONS

Inspect every persistence schema and migration path.

For Room and other databases, verify:

- Entity definitions.
- Primary keys.
- Foreign keys.
- Indices.
- Uniqueness constraints.
- Nullability.
- Defaults.
- Relationships.
- DAO queries.
- Transaction usage.
- Schema exports.
- Migration registration.
- Migration correctness.
- Data preservation.
- Database version consistency.
- Destructive migration settings.
- Query performance.
- Concurrent access.

Create migrations for necessary schema changes.

Never introduce destructive database migration behavior simply to make tests or builds pass.

Test upgrades from supported existing schemas, including representative existing data.

Verify that old data remains valid after migration.

If historical database schemas are unavailable, document that limitation and test every migration path that can be reconstructed.

For remote databases, ensure server migrations and client compatibility are coordinated where the backend is part of the available workspace.

---

## 14. PHASE TWELVE: DEPENDENCY AND BUILD SYSTEM MODERNIZATION

Inspect every dependency declaration, plugin, repository, version catalog, build convention, and build script.

Find:

- Deprecated dependencies.
- Unmaintained libraries.
- Known vulnerable versions.
- Duplicate dependencies.
- Conflicting versions.
- Unnecessary transitive dependencies.
- Incorrect plugin versions.
- Incompatible Kotlin and Gradle versions.
- Incorrect Android Gradle Plugin compatibility.
- Incorrect JDK configuration.
- Deprecated Gradle DSL.
- Deprecated compiler options.
- Dynamic dependency versions.
- Unnecessary repositories.
- Dependency resolution conflicts.
- Missing version catalogs.
- Unnecessary build configuration complexity.
- Incorrect release build settings.
- Missing ProGuard/R8 rules.
- Missing consumer rules.
- Incorrect resource shrinking.
- Incorrect code shrinking.
- Broken build variants.
- Misconfigured signing.
- Missing reproducible-build safeguards where practical.

Prefer a coordinated upgrade to compatible maintained versions.

Consult available project metadata and official compatibility information when choosing upgrades.

Do not upgrade every dependency to its newest version blindly.

Do not use dynamic versions such as `+` in production dependency declarations.

Do not disable R8, shrinking, or lint simply to make the release build pass.

Do not weaken TLS, validation, or application checks to resolve build failures.

Do not expose credentials in build files, CI logs, or generated reports.

Document significant dependency changes and their rationale.

---

## 15. PHASE THIRTEEN: AUTOMATED TESTING AND REGRESSION COVERAGE

Create or improve a testing strategy that reflects the actual application.

### 15.1 Unit tests

Test:

- Domain rules.
- Validation.
- Calculations.
- Mappers.
- Repository behavior.
- Error conversion.
- ViewModel state transitions.
- Use cases.
- Pagination.
- Date and time logic.
- Permission-related decision logic.
- Retry and cancellation behavior where relevant.

Use deterministic tests and injected clocks or other controllable dependencies where time affects outcomes.

### 15.2 Database tests

Test:

- CRUD operations.
- Query correctness.
- Constraints.
- Transactions.
- Migration paths.
- Data integrity.
- Edge cases.
- Concurrent operations where relevant.

### 15.3 API tests

Test:

- Correct requests.
- Serialization and deserialization.
- HTTP error mapping.
- Malformed responses.
- Authentication failures.
- Timeouts.
- Cancellation.
- Pagination.
- Empty responses.
- Retry behavior.
- Idempotency-sensitive operations.
- Server validation errors.

### 15.4 UI and integration tests

Test the real user workflows:

- Initial application launch.
- Navigation.
- Form submission.
- Validation.
- Loading.
- Empty states.
- Error recovery.
- Successful operations.
- Persistence.
- Settings.
- Permission denial.
- Permission grant.
- Process recreation where relevant.
- Deep links.
- Offline behavior.
- Authentication flows where implemented.

### 15.5 Quality requirements

Every critical defect fixed must have an appropriate regression test when technically feasible.

Avoid tests that only verify implementation details without verifying useful behavior.

Avoid weakening assertions merely to make tests pass.

Do not replace a real test with a test that always succeeds.

Separate environment-dependent integration tests from deterministic unit tests where appropriate.

Use available emulator or device testing for platform-sensitive features.

---

## 16. PHASE FOURTEEN: UPDATE EVERY DOCUMENTATION FILE

Documentation is part of the deliverable, not an optional afterthought.

Inventory every Markdown file and every relevant technical document.

Inspect and update:

- `README.md`
- `CHANGELOG.md`
- `CONTRIBUTING.md`
- `ARCHITECTURE.md`
- `SECURITY.md`
- `TESTING.md`
- `BUILDING.md`
- `DEVELOPMENT.md`
- `API.md` or `API_REFERENCE.md`
- `DATABASE.md`
- `MIGRATIONS.md`
- `DEPLOYMENT.md`
- `TROUBLESHOOTING.md`
- `CODE_QUALITY.md`
- `DEPENDENCIES.md`
- `PERMISSIONS.md`
- `RELEASE.md`
- Module-specific documentation.
- Feature-specific documentation.
- Existing developer instructions.
- Relevant code comments and KDoc.

The names above are recommendations. Preserve appropriate existing naming conventions and avoid unnecessary duplicate documents.

Create missing documents when they provide real value.

Do not create a large collection of empty Markdown files.

### 16.1 README requirements

Document:

- Application purpose.
- Actual implemented features.
- Architecture overview.
- Technology stack.
- Module structure.
- Supported Android versions.
- Prerequisites.
- JDK and Android SDK requirements.
- Build instructions.
- Installation instructions.
- Test commands.
- Lint and static-analysis commands.
- Configuration requirements.
- API configuration.
- Environment-specific settings.
- Database and migration behavior.
- Known limitations.
- Release procedure.
- Troubleshooting steps.

Only claim features that are implemented and verified.

### 16.2 Architecture documentation

Document:

- Module boundaries.
- Layer responsibilities.
- Dependency direction.
- Application startup.
- Navigation.
- State management.
- Domain logic.
- Repository interfaces and implementations.
- Local persistence.
- Remote API architecture.
- Dependency injection.
- Background processing.
- Authentication.
- Error handling.
- Data flow.
- Important design decisions.
- Compatibility constraints.
- Known architectural debt.

Include accurate diagrams where useful.

Every diagram must reflect the actual source code.

### 16.3 Function and class documentation

Add or correct KDoc for important public and nontrivial functions, classes, interfaces, and properties.

Document:

- Purpose.
- Parameters.
- Return values.
- Exceptions or failure conditions.
- Side effects.
- Threading or coroutine behavior where relevant.
- Lifecycle requirements where relevant.
- Security considerations where relevant.
- Preconditions and postconditions.
- Important business rules.

Use clear comments to explain WHY a non-obvious decision exists.

Do not add meaningless comments that merely repeat the code.

Do not write comments that contradict the implementation.

Do not automatically generate hundreds of redundant comments for obvious getters, trivial expressions, or self-explanatory private helpers.

For complex functions, explain their algorithm, assumptions, and critical edge cases.

### 16.4 Documentation consistency

Search the repository for every changed filename, class name, function name, package, API path, schema name, configuration key, and build command.

Update every affected reference in:

- Markdown.
- Code examples.
- Architecture diagrams.
- Shell and PowerShell scripts.
- CI workflows.
- Developer instructions.
- Configuration files.
- Test fixtures.
- Generated-code configuration where applicable.

Verify internal Markdown links and local file references.

Remove obsolete instructions, outdated screenshots where available, incorrect dependency versions, and references to deleted components.

Never document an unimplemented feature as complete.

---

## 17. PHASE FIFTEEN: PRODUCTION RELEASE HARDENING

Review the actual release build and runtime configuration.

Verify:

- Release build compiles.
- Production endpoints are correctly configured.
- Debug endpoints are excluded from release builds.
- Debug logging does not expose sensitive information.
- No debug-only shortcuts bypass security.
- Manifest configuration is correct.
- Required permissions are declared appropriately.
- Exported components are intentional.
- Deep links are validated.
- Release signing configuration is secure.
- Signing secrets are not committed.
- R8 and resource shrinking are appropriately configured.
- Required reflection and serialization rules are preserved.
- Crash handling is appropriate.
- Network security is correct.
- User-facing error messages are safe.
- Database migrations are compatible.
- Background work behaves correctly.
- Notifications follow platform requirements.
- Accessibility checks are performed.
- Large and malformed inputs are handled safely.
- The application behaves correctly after process death.
- Sensitive data is not leaked into logs, files, backups, or external intents.
- Third-party SDK behavior is appropriate.
- Release artifacts can be built reproducibly where practical.

Inspect release-specific behavior rather than assuming that a successful debug build guarantees a valid production application.

Build the relevant release variant when signing and configuration requirements permit it.

Do not fabricate production credentials or silently substitute debug credentials.

Do not publish, distribute, upload, or deploy a release without explicit authorization.

---

## 18. PHASE SIXTEEN: PERFORMANCE AND RESOURCE AUDIT

Evaluate:

- Application startup.
- Main-thread workload.
- Database query performance.
- Network request frequency.
- Request cancellation.
- Image loading.
- Memory usage.
- Object retention.
- Compose recomposition.
- RecyclerView performance.
- Large list handling.
- Cache behavior.
- Battery consumption.
- Background execution.
- Unnecessary wakeups.
- Excessive polling.
- Resource loading.
- APK/AAB size.
- Code shrinking.
- Repeated parsing or serialization.
- Excessive logging.
- Unnecessary allocations.

Use profiling tools or benchmarks when available and useful.

Fix demonstrated performance problems.

Do not introduce premature optimization, complex caching, or concurrency that makes correctness worse.

Set measurable performance goals only where the environment and application requirements support them.

Clearly distinguish measured performance from estimates.

---

## 19. PHASE SEVENTEEN: END-TO-END FEATURE COMPLETENESS AUDIT

Create a feature inventory based on actual application behavior.

For every feature, identify:

- User-visible entry point.
- Relevant screen.
- User actions.
- Input requirements.
- Business logic.
- Local data dependencies.
- Remote API dependencies.
- Permissions.
- Navigation paths.
- Persistence.
- Error handling.
- Loading state.
- Empty state.
- Success state.
- Cancellation behavior.
- Offline behavior.
- Security implications.
- Tests.
- Documentation.
- Known limitations.

Then inspect each feature end to end.

Use a feature-completeness matrix with the following columns:

| Field | Required information |
|---|---|
| Feature | Actual feature name |
| Entry point | Screen, menu, notification, deep link, or other trigger |
| Current implementation | Actual classes and methods |
| Missing pieces | Confirmed gaps |
| Defects | Confirmed functional problems |
| API/data dependencies | Local and remote dependencies |
| Security concerns | Applicable risks |
| Fixes performed | Actual code changes |
| Tests | Tests added or updated |
| Verification | Commands and results |
| Remaining limitations | Unresolved items |

Look for logical gaps between features.

Examples include:

- A setting that is displayed but never applied.
- A form that validates input but never saves it.
- A save operation that updates the database but leaves the UI stale.
- A remote update that does not synchronize local state.
- A navigation destination with no reachable entry point.
- A delete action that removes the UI item but not its persistent data.
- A successful request that fails to handle partial server failure.
- A database field that is populated but never read.
- A permission-dependent feature that fails without explaining the missing permission.
- A list that cannot recover from an empty response.
- A workflow that works only while the process remains alive.
- An API model that disagrees with the server contract.
- A documented capability with no corresponding implementation.

Use actual source evidence to find these defects, not assumptions.

---

## 20. PHASE EIGHTEEN: IMPLEMENTATION WORKFLOW

Execute the work in controlled phases.

### Stage A — Discovery

Inventory the repository, understand the application, inspect dependencies, establish a baseline, and create a prioritized defect register.

### Stage B — Critical fixes

Fix build blockers, data-loss risks, security vulnerabilities, crashes, and broken critical workflows.

### Stage C — Structural corrections

Correct package organization, dependency direction, duplicated business logic, naming problems, and lifecycle issues.

### Stage D — Kotlin and Jetpack modernization

Migrate application-owned Java code, modernize legacy APIs, improve UI architecture, and introduce appropriate Jetpack components.

### Stage E — API and persistence corrections

Repair data flow, endpoint contracts, persistence, migrations, error mapping, and synchronization.

### Stage F — Feature completion

Implement missing behavior established by actual project requirements. Connect UI actions, business logic, storage, and remote operations correctly.

### Stage G — Documentation and renaming

Update symbols, filenames, references, code comments, Markdown, diagrams, and developer instructions.

### Stage H — Automated verification

Run compilation, tests, lint, static analysis, dependency checks, and relevant integration tests.

### Stage I — Release validation

Build production variants where possible and verify release-specific behavior.

### Stage J — Final audit

Repeat repository searches, inspect all unresolved diagnostics, validate renamed references, compare baseline and final results, and generate the completion report.

Do not treat this sequence as permission to postpone an obvious critical defect when it is safe to fix immediately.

Maintain a persistent audit checklist so the work can resume reliably if the session or tool execution is interrupted.

---

## 21. FILE CHANGE SAFETY AND CHANGE MANAGEMENT

Before making large-scale changes:

- Inspect Git status when Git is available.
- Identify pre-existing uncommitted work.
- Preserve user changes.
- Establish a rollback strategy.
- Avoid overwriting unrelated files.
- Avoid deleting user data.
- Avoid resetting branches or repositories.
- Avoid destructive clean operations unless explicitly justified and safe.
- Avoid exposing secrets in reports.
- Keep modifications logically grouped.

Do not discard pre-existing changes merely because they complicate refactoring.

Prefer small, coherent changes that can be compiled and verified incrementally.

When performing large migrations, preserve a mapping between original files and their replacements.

Use version control diffs to inspect the actual change set.

After refactoring, inspect for:

- Accidental mass replacements.
- Broken imports.
- Duplicate implementations.
- Unrelated formatting churn.
- Deleted functionality.
- Incomplete file moves.
- Incorrect resource names.
- Stale generated outputs.
- Broken build scripts.
- Unintended API contract changes.

Do not run an uncontrolled repository-wide text replacement that changes every matching string regardless of context.

---

## 22. DEFINITION OF DONE

The project must not be declared production-ready merely because the application launches or the debug build succeeds.

Evaluate every requirement independently.

### Build

- All required modules compile.
- Required debug builds succeed.
- Relevant release builds succeed where environment and credentials permit.
- No unexplained compilation errors remain.
- No new build failures are hidden by configuration changes.

### Diagnostics

- Relevant lint tasks have been executed.
- Static analysis has been executed where available.
- Existing and new warnings have been reviewed.
- Unjustified suppressions have been removed.
- No checks have been disabled merely to obtain success.
- Remaining exceptions are documented.

### Kotlin migration

- Application-owned Java migration is complete or each remaining exception is explicitly documented.
- No eligible Java implementation has been overlooked.
- Remaining generated and third-party Java is classified.
- Migration has not silently changed feature behavior.

### Jetpack modernization

- Appropriate modern Android APIs are used.
- Lifecycle and concurrency are handled correctly.
- Deprecated application-owned APIs have been replaced where a suitable maintained alternative exists.
- Any justified legacy implementation is documented.

### Functional correctness

- Critical workflows have been traced and tested.
- Confirmed logical and functional defects have been corrected where feasible.
- Important state transitions are tested.
- Missing behavior has been implemented where requirements are sufficiently established.
- Unresolved product decisions are listed honestly.

### API and data

- Required endpoints are connected to real implementations.
- API contracts match available backend specifications.
- Persistence and migrations preserve data.
- Error handling is consistent.
- Fake production success responses have been eliminated.
- Missing backend dependencies are documented rather than fabricated.

### Security

- Manifest and permissions have been reviewed.
- Authentication and authorization boundaries have been assessed.
- Sensitive information handling has been reviewed.
- Network security has been checked.
- Known high-risk issues have been corrected or explicitly identified as release blockers.

### Documentation

- Relevant Markdown documents reflect the final code.
- Important functions and architectural components are documented.
- Renamed files and symbols have updated references.
- Build and test instructions are accurate.
- Remaining limitations are explicit.

### Release

- Production configuration has been reviewed.
- Release build verification has been attempted.
- Release blockers are documented.
- No claim of deployment, publication, or device testing is made without evidence.

---

## 23. REQUIRED FINAL DELIVERABLES

At the end of execution, provide a detailed technical completion report.

### A. Executive summary

Summarize the actual condition of the project before and after the work.

### B. Repository audit

Report:

- Modules inspected.
- Source files inspected.
- Kotlin and Java inventory.
- Configuration files inspected.
- Documentation files inspected.
- Tests and diagnostics executed.
- Areas that could not be inspected.

Provide counts only when derived from actual repository evidence.

### C. Defects identified and corrected

For each significant defect, include:

- Severity.
- Root cause.
- Affected file and symbol.
- Impact.
- Fix implemented.
- Test or verification performed.
- Result.

### D. Kotlin migration report

List migrated Java files, replacement Kotlin files, remaining Java files, and the reason each remaining category is retained.

### E. Jetpack modernization report

Document actual migrations and architectural changes, including retained legacy components.

### F. API and persistence report

Document endpoints, models, repositories, databases, schema changes, migrations, and any external dependencies that could not be verified.

### G. Suppression and warning report

Report:

- Suppressions found initially.
- Suppressions removed.
- Remaining justified suppressions.
- Remaining compiler warnings.
- Remaining lint warnings.
- Remaining static-analysis findings.
- Checks that could not run.

Distinguish warning counts from individual suppression counts and do not double-count findings without explaining the counting method.

### H. File and naming change report

List renamed, moved, added, modified, and deleted project files.

Explain important renames and verify their references.

### I. Documentation report

List Markdown files updated, created, or removed, and explain what each now covers.

### J. Test and build report

Provide the actual commands, relevant variants, execution results, and any environmental blockers.

Use only truthful statuses:

- PASS — executed and passed.
- FAIL — executed and failed.
- BLOCKED — could not be executed because of a documented blocker.
- NOT RUN — not attempted.
- PARTIAL — some but not all relevant coverage was completed.

### K. Security report

List corrected vulnerabilities, residual risks, and any findings that prevent release.

### L. Production readiness matrix

Use the following columns:

| Area | Status | Evidence | Remaining work |
|---|---|---|---|
| Build | Verified status | Actual build output | Specific outstanding issues |
| Kotlin migration | Verified status | Migration inventory | Remaining exceptions |
| Jetpack | Verified status | Actual implementation | Remaining legacy areas |
| Functionality | Verified status | Feature tests | Untested workflows |
| API | Verified status | Contract and integration tests | Unverified backend dependencies |
| Database | Verified status | Migration tests | Unverified upgrade paths |
| Security | Verified status | Audit findings | Residual risks |
| Lint | Verified status | Actual task output | Remaining findings |
| Tests | Verified status | Test results | Missing coverage |
| Documentation | Verified status | Updated document inventory | Missing documentation |
| Release | Verified status | Actual release build result | Remaining release blockers |

### M. Remaining work

Classify unresolved items into:

- P0 — Critical security, data-loss, or release-blocking defects.
- P1 — Major functional or reliability defects.
- P2 — Important maintainability, compatibility, or quality issues.
- P3 — Noncritical improvements.

For every unresolved issue, explain why it remains unresolved, its impact, and the next actionable step.

---

## 24. FINAL EXECUTION INSTRUCTIONS

Begin by examining the real repository and collecting the baseline.

Then execute the full audit and remediation workflow.

Do not ask me to identify every file or explain every existing feature manually when that information can be obtained from the repository.

Do not stop after scanning a few directories.

Do not focus exclusively on compile errors while ignoring business logic, security, data integrity, incomplete workflows, or documentation.

Do not rewrite the entire project blindly.

Do not fabricate missing requirements.

Do not remove working features to simplify modernization.

Do not create an unnecessary backend or force local-only functionality into network endpoints.

Do not suppress diagnostics to make reports appear clean.

Do not make unverified claims about successful tests, builds, migrations, API behavior, security, or production readiness.

If a tool, dependency, backend, SDK, emulator, credential, or external system is unavailable, complete all independent work that can safely proceed and record the exact blocker.

If execution time or context becomes limited, preserve the audit checklist, completed changes, test results, outstanding tasks, and the next exact action in a persistent progress document. Resume from that record rather than restarting or falsely declaring completion.

Make intelligent, evidence-based decisions and implement them consistently across the entire codebase.

After every substantial change, verify the affected implementation and update the corresponding documentation.

At the end, perform a fresh final audit against the original baseline and the definition of done.

**The final objective is not a cosmetically modernized codebase. It is an application whose actual features work correctly, whose architecture is maintainable, whose security and data integrity have been examined, whose legacy code has been modernized appropriately, whose warnings are understood, whose files and references are consistent, whose documentation matches reality, and whose production-readiness status is supported by verifiable evidence.**

Start with repository discovery now. Proceed through the entire workflow and make the actual changes wherever execution is possible.