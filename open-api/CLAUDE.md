# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build
./gradlew build
./gradlew build -x test    # skip tests

# Run
./gradlew bootRun

# Test
./gradlew test
./gradlew test --tests "re.kr.icuh.drought.openapi.IcuhPlatformApiApplicationTests"
```

## Architecture Overview

**Stack:** Spring Boot 3.5.6, Java 17, Spring Data JPA, MySQL

**Package structure:** `re.kr.icuh.drought.openapi.core`

```
core/
├── api/           # REST controllers + request/response DTOs (one subdirectory per domain)
├── domain/        # Business logic + JPA entities + repositories + domain value objects
└── support/       # Cross-cutting: error handling, ApiResponse wrapper
```

**Four business domains:**
- `agrimarket` — Agricultural market predictions (monthly/daily price, trends)
- `freshfood` — Fresh vegetable/fruit indices by province and grade
- `hydropower` — Dam/reservoir predictions and generation data
- `wildfire` — Wildfire risk index forecasts and news articles

## Key Patterns

**Layered architecture:** Controller → Service → Repository (no direct entity exposure to API layer)

**API response wrapper:** All endpoints return `ApiResponse<T>` from `core/support/response/ApiResponse.java`. Use `ApiResponse.success(data)` in controllers.

**Error handling:** Throw `CoreException(ErrorType.XXX)` in services; `ApiControllerAdvice` handles globally. Add new error types to `ErrorType` enum (with HTTP status, `ErrorCode`, message, and log level).

**DTOs:** Each domain has separate Request/Response DTOs under `core/api/<domain>/`. Use Lombok `@Builder`. Entities are never returned directly from controllers.

**Domain value objects:** Each domain may have intermediate record types (e.g., `Sigungu`, `FreshVegetableIndex`, `FreshFruitIndex`) that live under `core/domain/<domain>/`. These hold business classification logic (e.g., grade/risk-level thresholds) and are mapped from entities via a static `of()` factory method before being passed to response DTOs.

**Repositories:** Use `@Query` JPQL annotations for complex queries (e.g., filtering by year/month/damName). A single repository interface may return multiple entity types via separate `@Query` methods.

**Request params:** Query parameters are bound via Java records annotated with `@ModelAttribute` (implicit). Year/month/day/location are typically passed as `String`.

## Database

- DB: MySQL, schema name `ACTUAL_DRGHT`
- `ddl-auto: none` — schema is pre-existing, do not modify it via Hibernate
- SQL logging enabled (`show-sql: true`, `format_sql: true`)

## HTTP Test Files

Manual API tests are in `src/test/http/` (`.http` files per domain). Use these to verify endpoints during development.
