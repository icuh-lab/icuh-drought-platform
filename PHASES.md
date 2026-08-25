# icuh-drought-platform Migration Phases

## Phase 0 - Baseline

- Preserve the original `icuh-platform`, `icuh-platform-admin`, and `icuh-platform-api` repositories.
- Create this new multi-module workspace as the migration target.
- Keep the first migration step behavior-preserving: each existing app remains runnable as its own Boot application module.
- Status: completed.

## Phase 1 - Multi-Module Shell

- Root Gradle project manages Java, Spring Boot, dependency management, repositories, and test defaults.
- Execution modules:
  - `public-api`: user-facing platform API, migrated from `icuh-platform`.
  - `admin-api`: administrator UI/API, migrated from `icuh-platform-admin`.
  - `open-api`: external/open data API, migrated from `icuh-platform-api`.
  - `batch`: future data collection, prediction result loading, report generation jobs.
- Library modules:
  - `common`: response, exception, error, page, and shared utility types.
  - `core-domain`: domain model, enums, value objects, domain rules.
  - `core-persistence`: JPA entities, repositories, QueryDSL configuration.
  - `core-application`: shared use cases and query services.
- Status: completed. The project currently builds as a multi-module Gradle workspace.

## Phase 2 - Common Extraction

- Move duplicate response and error support from execution modules into `common`.
- Keep domain-specific enums and policies out of `common`.
- Convert execution modules to depend on `common`.
- Status: completed.
- Result:
  - `common` now owns the shared public/admin response and business-error types.
  - `common` also owns the open API response/error envelope used by `open-api`.
  - The duplicated response/error classes were removed from `public-api`, `admin-api`, and `open-api`.

## Phase 3 - Persistence Consolidation

- Move duplicated JPA mappings and repository contracts into `core-persistence`.
- Centralize QueryDSL and database access.
- Prevent execution modules from defining duplicate entities for the same database tables.
- Status: completed.
- Result:
  - `open-api` JPA entities, projection/domain lookup types, and repositories now live under `core-persistence`.
  - Shared article reference data mappings for `document_types` and `subject_domains` now live under `core-persistence`.
  - `public-api`, `admin-api`, and `open-api` depend on `core-persistence` instead of keeping those duplicated persistence classes locally.
  - Application modules declare explicit JPA scan boundaries for the persistence packages they use.

## Phase 4 - Application Use Cases

- Move dashboard, forecast, index, report, admin-review, API-key, and usage workflows into `core-application`.
- Let controllers call use cases instead of repositories directly.
- Status: completed for the `open-api` migration scope. Public/admin workflows remain candidates for later expansion.
- Phase 4-1 status: completed.
- Phase 4-1 result:
  - `open-api` service classes now live under `core-application`.
  - Related open API request and response DTOs now live under `core-application`.
  - `open-api` controllers depend on application services and DTOs from `core-application`.
  - `open-api` scans `re.kr.icuh.drought.application.openapi` for application services.
- Phase 4-2 status: completed.
- Phase 4-2 result:
  - `open-api` controllers now call use cases from `core-application`.
  - Controller request and response types are imported from `core-application`.
  - No controller imports remain for the old `open-api` service or response packages.
- Phase 4-3 status: completed.
- Phase 4-3 result:
  - `core-application` depends on `core-persistence` for repository-backed use cases.
  - `open-api` depends on `core-application` for application services.
  - Persistence scanning remains isolated in `PersistenceScanConfig`.
- Phase 4-4 status: completed.
- Phase 4-4 result:
  - The full multi-module build passes after the application-layer move.

## Phase 5 - Runtime Separation

- Keep `public-api`, `admin-api`, `open-api`, and `batch` as separate deployable applications.
- Split runtime configuration by module and profile.
- Move all secrets to environment variables.
- Status: completed.
- Result:
  - Runtime ports and application names are separated by execution module.
  - Plaintext admin DB and AWS credentials were removed from source configuration.
  - Public, admin, and open API datasource/S3 settings are environment-variable driven.
  - Test profiles provide isolated H2 and dummy AWS values where context tests need them.
  - Runtime environment variables are documented in `RUNTIME_CONFIG.md`.

## Phase 6 - Cleanup

- Normalize package names under `re.kr.icuh.drought`.
- Remove obsolete copied wrappers/settings from child modules.
- Add integration tests for representative endpoints and shared persistence.
- Status: completed.
- Result:
  - Execution module packages were normalized to `re.kr.icuh.drought.publicapi`, `re.kr.icuh.drought.adminapi`, and `re.kr.icuh.drought.openapi`.
  - Obsolete empty package roots, copied IDE metadata, and copied `.claude` helper directories were removed.
  - `core-persistence` now has a representative `@DataJpaTest` slice that verifies shared JPA entity/repository wiring with H2.
  - The full multi-module `clean build` passes after cleanup.
