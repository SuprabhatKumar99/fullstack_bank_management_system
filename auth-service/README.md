# CBS Authentication Service

> **Port 8080** · Spring Boot 3.2 · Java 21 · PostgreSQL 16 · Redis 7 · Kafka (Confluent 7.6)

The **Authentication Service** is the identity and token-issuing authority for the entire Core Banking System (CBS) platform. It is the **only JWT issuer**. Every other microservice (Customer, Account, Transaction, Ledger) is a JWT resource server that validates tokens signed here.

---

## Table of Contents

- [Responsibilities](#responsibilities)
- [Architecture Overview](#architecture-overview)
- [API Endpoints](#api-endpoints)
- [Security Design](#security-design)
- [Token Lifecycle](#token-lifecycle)
- [Running Locally](#running-locally)
- [Configuration Reference](#configuration-reference)
- [Project Structure](#project-structure)

---

## Responsibilities

| Concern | Implementation |
|---|---|
| User registration | Validates uniqueness, BCrypt-hashes password, persists to `auth_users`, issues token pair immediately |
| Login | Username or email + password via Spring Security `DaoAuthenticationProvider`, issues token pair |
| Access token | HS256 JWT, 15-minute TTL, carries `role`, `userId`, `customerId` claims |
| Refresh token | 256-bit random token, SHA-256 hashed before storage, 7-day TTL |
| Token rotation | Each refresh call revokes the old token and issues a new pair |
| Reuse detection | Presenting a revoked token immediately invalidates ALL sessions for that user |
| Account lockout | 5 failed attempts → 30-minute lockout, enforced in domain entity |
| Audit trail | Every auth event published to `auth.events` Kafka topic |
| Token cleanup | Nightly scheduled job purges expired/revoked DB records |

---

## Architecture Overview

```
Client
  │
  ├─ POST /api/auth/register  ─► AuthController
  ├─ POST /api/auth/login     ─►     │
  ├─ POST /api/auth/refresh   ─►     │
  ├─ GET  /api/auth/me        ─►     │
  └─ POST /api/auth/logout    ─►     │
                                     │
                              AuthService / TokenService
                                     │
                    ┌────────────────┼─────────────────┐
                    │                │                  │
               PostgreSQL          Redis            Kafka
              (auth_users,     (refresh token    (auth.events)
            refresh_tokens)    fast lookup)
```

**Storage split:**
- **PostgreSQL** — source of truth for credentials, refresh token revocation, and audit history.
- **Redis** — fast `O(1)` refresh token lookup by hash; TTL mirrors token expiry so expired entries self-clean.
- **Kafka** — async audit/notification events; publish failures never block auth operations.

---

## API Endpoints

### Public (no token required)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/auth/register` | Register new customer account |
| `POST` | `/api/auth/login` | Authenticate with username/email + password |
| `POST` | `/api/auth/refresh` | Rotate refresh token → new token pair |

### Authenticated

| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/api/auth/me` | Current user profile |
| `POST` | `/api/auth/logout` | Revoke all active sessions |

### Admin only (`ROLE_ADMIN`)

| Method | Path | Description |
|--------|------|-------------|
| `DELETE` | `/api/auth/admin/users/{userId}/sessions` | Force-revoke all sessions for any user |

---

### Request / Response Examples

**Register**
```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "john.doe",
  "email":    "john@example.com",
  "password": "SecurePass@1"
}
```

```json
HTTP 201 Created
{
  "accessToken":          "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken":         "dGhpcyBpcyBhIHJhbmRv...",
  "tokenType":            "Bearer",
  "accessTokenExpiresIn": 900000,
  "issuedAt":             "2024-01-15T10:00:00Z",
  "userId":               "a1b2c3d4-...",
  "username":             "john.doe",
  "role":                 "ROLE_CUSTOMER"
}
```

**Login**
```http
POST /api/auth/login
Content-Type: application/json

{
  "identifier": "john.doe",    // accepts username OR email
  "password":   "SecurePass@1"
}
```

**Refresh**
```http
POST /api/auth/refresh
Content-Type: application/json

{
  "refreshToken": "dGhpcyBpcyBhIHJhbmRv..."
}
```

**Error envelope** (all errors share this shape):
```json
HTTP 401 Unauthorized
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status":    401,
  "error":     "Unauthorized",
  "message":   "Invalid username/email or password",
  "path":      "/api/auth/login"
}
```

---

## Security Design

### Why this service is an issuer, not a resource server

All other CBS microservices use `spring-security-oauth2-resource-server` and validate JWTs without contacting this service. This service uses a traditional `DaoAuthenticationProvider` to verify credentials, then signs the JWT. The symmetric HS256 key must be shared with all resource servers via the `JWT_SECRET` environment variable.

> **Production upgrade path:** Switch to RS256 with a public/private key pair. Resource servers only need the public key, eliminating the shared-secret risk.

### JWT Claims

```json
{
  "jti":        "uuid-unique-token-id",
  "iss":        "cbs-auth-service",
  "sub":        "john.doe",
  "role":       "ROLE_CUSTOMER",
  "userId":     "a1b2c3d4-...",
  "customerId": "e5f6g7h8-...",
  "iat":        1705312800,
  "exp":        1705313700
}
```

Downstream services read `role` for method-level security (`@PreAuthorize`) and `customerId` to scope data access to the authenticated customer.

### Password Policy

Enforced via JSR-380 validation on registration:
- Minimum 8 characters, maximum 72 (BCrypt limit)
- Must contain: uppercase, lowercase, digit, special character (`@$!%*?&`)
- BCrypt strength 12 (~300 ms hash time on modern hardware)

### Account Lockout

After **5 consecutive failed logins**, the account is locked for **30 minutes**. The `lockedUntil` timestamp is stored on the entity and checked in `isAccountNonLocked()` before the `AuthenticationManager` is invoked.

---

## Token Lifecycle

```
Login
  │
  └─► Generate access token (JWT, 15 min)
  └─► Generate raw refresh token (256-bit random)
  └─► Hash refresh token (SHA-256)
  └─► Persist hash to PostgreSQL (refresh_tokens)
  └─► Cache hash→userId in Redis (TTL = 7 days)
  └─► Return both tokens to client

Refresh
  │
  ├─ Hash presented token
  ├─ Lookup hash in PostgreSQL
  │
  ├─ Token REVOKED? → REUSE DETECTED
  │     └─► Revoke ALL tokens for user
  │     └─► Evict all Redis entries for user
  │     └─► Publish TOKEN_REUSE_DETECTED event
  │     └─► Throw TokenReuseException (401)
  │
  ├─ Token EXPIRED? → Throw InvalidTokenException (401)
  │
  └─ Token VALID?
        └─► Revoke old token (DB + Redis)
        └─► Issue new access token + refresh token
        └─► Persist new hash
        └─► Return new pair

Logout
  └─► Revoke all active refresh tokens for user (DB + Redis)
  └─► Publish LOGOUT event
  └─► Return 204 (client discards access token locally)
```

---

## Running Locally

### Prerequisites

- Docker Desktop
- Java 21 (only needed if running outside Docker)
- Maven 3.9+ (only needed if running outside Docker)

### Start with Docker Compose

```bash
# From the auth-service directory
docker compose up -d

# Check service health
curl http://localhost:8080/api/actuator/health

# Register a user
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"john.doe","email":"john@example.com","password":"SecurePass@1"}'
```

### Run locally (against Docker dependencies)

```bash
# Start only the dependencies
docker compose up -d postgres-auth redis kafka zookeeper

# Run the service
./mvnw spring-boot:run
```

### Run tests

```bash
./mvnw test
```

---

## Configuration Reference

All configuration via environment variables (12-factor):

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `cbs_auth` | Database name |
| `DB_USER` | `cbs_user` | Database username |
| `DB_PASS` | `cbs_pass` | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASS` | _(empty)_ | Redis password (if AUTH enabled) |
| `KAFKA_BOOTSTRAP` | `localhost:9092` | Kafka bootstrap servers |
| `JWT_SECRET` | _(see yml)_ | **MUST be overridden in prod** — min 32 chars |

JWT tuning (in `application.yml`, not typically env-var overridden):

| Property | Default | Description |
|---|---|---|
| `jwt.access-token-expiry-ms` | `900000` | Access token TTL (15 min) |
| `jwt.refresh-token-expiry-ms` | `604800000` | Refresh token TTL (7 days) |
| `jwt.issuer` | `cbs-auth-service` | JWT `iss` claim |

---

## Project Structure

```
auth-service/
├── src/main/java/com/suprab/cbs/auth/
│   ├── AuthServiceApplication.java          # Entry point
│   │
│   ├── config/
│   │   ├── JwtProperties.java               # Typed binding for jwt.* config
│   │   ├── SecurityConfig.java              # Filter chain, BCrypt bean, AuthManager
│   │   ├── RedisConfig.java                 # RedisTemplate<String,String>
│   │   └── KafkaConfig.java                 # auth.events topic declaration
│   │
│   ├── entity/
│   │   ├── AuthUser.java                    # UserDetails impl, lockout domain logic
│   │   ├── RefreshToken.java                # Token hash, revocation, expiry
│   │   └── Role.java                        # CUSTOMER, TELLER, MANAGER, ADMIN, SERVICE
│   │
│   ├── repository/
│   │   ├── AuthUserRepository.java          # JPA — findByUsername, findByEmail
│   │   └── RefreshTokenRepository.java      # JPA — revokeAll, deleteExpiredAndRevoked
│   │
│   ├── security/
│   │   └── JwtService.java                  # Token generation, validation, claims
│   │
│   ├── filter/
│   │   └── JwtAuthenticationFilter.java     # Validates JWT on each request
│   │
│   ├── service/
│   │   ├── UserDetailsServiceImpl.java      # Bridges JPA → Spring Security
│   │   ├── AuthService.java                 # Registration, login, lockout
│   │   ├── TokenService.java                # Issue, rotate, revoke token pairs
│   │   ├── AuthEventPublisher.java          # Kafka producer
│   │   └── TokenCleanupJob.java             # Nightly DB purge (@Scheduled)
│   │
│   ├── controller/
│   │   └── AuthController.java              # /register /login /refresh /me /logout
│   │
│   ├── mapper/
│   │   └── AuthUserMapper.java              # MapStruct: AuthUser → UserProfileResponse
│   │
│   ├── dto/
│   │   ├── request/
│   │   │   ├── RegisterRequest.java         # username, email, password (validated)
│   │   │   ├── LoginRequest.java            # identifier, password
│   │   │   └── RefreshRequest.java          # refreshToken
│   │   └── response/
│   │       ├── AuthResponse.java            # Token pair + user metadata
│   │       ├── UserProfileResponse.java     # /me endpoint payload
│   │       └── AuthEvent.java               # Kafka message model
│   │
│   └── exception/
│       ├── GlobalExceptionHandler.java      # @RestControllerAdvice, error envelope
│       ├── UserAlreadyExistsException.java  # 409
│       ├── InvalidCredentialsException.java # 401
│       ├── AccountLockedException.java      # 423
│       ├── InvalidTokenException.java       # 401
│       └── TokenReuseException.java         # 401
│
├── src/main/resources/
│   ├── application.yml                      # All configuration
│   └── db/migration/
│       ├── V1__create_auth_users.sql        # auth_users table + admin seed
│       └── V2__create_refresh_tokens.sql    # refresh_tokens table
│
├── src/test/java/com/suprab/cbs/auth/
│   ├── service/
│   │   └── AuthServiceTest.java             # Unit tests (Mockito)
│   └── controller/
│       └── AuthControllerTest.java          # MockMvc slice tests
│
├── Dockerfile                               # Multi-stage build (Maven → JRE)
├── docker-compose.yml                       # Full stack: auth + postgres + redis + kafka
└── README.md                                # This file
```

---

## Known Production Gaps (by design for portfolio scope)

| Gap | Production solution |
|---|---|
| Shared JWT secret (HS256) | Switch to RS256; resource servers use public key only |
| Redis SCAN on logout | Maintain a reverse index: `auth:user:{userId}:tokens → Set<hash>` |
| No rate limiting on `/login` | Add `spring-cloud-gateway` rate limiter or bucket4j at service level |
| Single admin seed user | Admin provisioning API with role guard |
| No email verification on register | Add verification token flow, email via Notification Service |
| Outbox pattern for Kafka | Wrap DB save + Kafka publish in transactional outbox to guarantee delivery |