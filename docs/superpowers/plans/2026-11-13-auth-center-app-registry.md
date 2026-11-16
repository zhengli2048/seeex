# Authentication Center Application Registry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Extend the existing authentication center with application registration, CI route metadata reporting, permission-filtered dynamic menus, and one-time cross-application SSO exchange.

**Architecture:** Add an application-registry module in `web` with an in-memory implementation for the first delivery and a Flyway schema for durable storage migration. CI authenticates with an application-scoped token; user-facing menu and SSO endpoints use the existing bearer session. Route metadata is versioned and publishable, while legacy applications can be registered manually through the same management APIs.

**Tech Stack:** Java 21, Spring Boot 4, Spring Security, MyBatis-Plus conventions, MySQL/Flyway, React 19, TypeScript, Axios.

**Spec:** `docs/design/05-auth-and-callback-contract.md`, `docs/design/02-rbac-authorization.md`, and the user requirement for independent AI applications.

## Global Constraints

- Keep `ApiResponse` as the public envelope and stable uppercase error codes.
- Backend authorization remains authoritative; frontend menu filtering is presentation only.
- CI credentials are application-scoped and never returned in user-facing responses.
- SSO exchange codes are one-time and short-lived.
- Legacy applications may be registered manually without source-code refactoring.

## Review Focus

- Duplicate or expired metadata reports must be rejected or treated idempotently.
- A user without the application permission must not receive its menu.
- An SSO exchange code must not be reusable or valid after expiry.
- Disabled applications and unpublished route versions must not appear in menus.
- CI tokens must not be accepted as user bearer tokens.

### Task 1: Application registry contract and storage schema

- Add application, route metadata, release, and SSO exchange-code tables.
- Add request/response records and stable error codes.

### Task 2: Registry service and CI/manual endpoints

- Register/update applications.
- Report, publish, rollback, and list route metadata.
- Authenticate CI reports with application-scoped token.
- Allow manual metadata registration with normal admin bearer context.

### Task 3: Dynamic menus and SSO

- Resolve visible routes from current user permissions.
- Add `/api/me/menus`, `/api/me/apps`, and one-time `/api/sso/token/exchange`.
- Keep cross-application redirect information in the response.

### Task 4: Frontend integration

- Load menus from the authentication center.
- Preserve static fallback routes for legacy applications.
- Add an application registry page shell and route metadata types.

### Task 5: Verification

- Run `mvn verify`, frontend build, and targeted endpoint/service tests where available.
