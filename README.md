# ⚡ NotifySystem — Real-Time Notification System
### Spring Boot + WebSockets | Production-Grade Architecture

---

## Table of Contents
1. [Tech Stack](#tech-stack)
2. [Architecture Overview](#architecture-overview)
3. [Folder Structure](#folder-structure)
4. [Data Flow](#data-flow)
5. [WebSocket Message Flow](#websocket-message-flow)
6. [REST API Reference](#rest-api-reference)
7. [Security Design](#security-design)
8. [Database Schema](#database-schema)
9. [How to Run Locally](#how-to-run-locally)
10. [How to Deploy](#how-to-deploy)
11. [Configuration Reference](#configuration-reference)
12. [Scalability Notes](#scalability-notes)

---

## Tech Stack

| Layer       | Technology                             | Reason                                      |
|-------------|----------------------------------------|---------------------------------------------|
| Language    | Java 17                                | LTS, modern records, pattern matching        |
| Framework   | Spring Boot 3.2                        | Auto-config, production-ready out of the box |
| WebSocket   | Spring WebSocket + STOMP               | Protocol-level pub/sub over WS              |
| WS Fallback | SockJS                                 | Works in restricted network environments     |
| Auth        | Spring Security + JWT (JJWT 0.11.5)   | Stateless, scales horizontally              |
| Database    | H2 (dev) / PostgreSQL (prod)           | Easy local dev, robust production DB        |
| ORM         | Spring Data JPA + Hibernate            | Clean repository abstraction                 |
| Validation  | Bean Validation (Jakarta)              | Declarative, standard                       |
| Build       | Maven                                  | Standard, well-supported in CI              |
| Frontend    | Vanilla JS + SockJS + StompJS          | Zero dependencies, fast, simple             |
| Monitoring  | Spring Actuator                        | /health, /metrics, /info endpoints          |
| Container   | Docker + Docker Compose                | One-command deployment                      |

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│                         BROWSER / CLIENT                         │
│                                                                  │
│  ┌──────────────┐      ┌─────────────────────────────────────┐  │
│  │  REST (HTTP) │      │    WebSocket (STOMP over SockJS)    │  │
│  │  - Login     │      │    - /ws  endpoint                  │  │
│  │  - Register  │      │    - JWT in CONNECT header          │  │
│  │  - CRUD      │      │    - Sub: /user/{name}/queue/notif  │  │
│  └──────┬───────┘      │    - Sub: /topic/broadcast          │  │
│         │ Bearer JWT   └──────────────┬──────────────────────┘  │
└─────────┼────────────────────────────┼────────────────────────-─┘
          │                            │
          ▼                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                       SPRING BOOT APP                           │
│                                                                 │
│  ┌──────────────┐   ┌──────────────────────────────────────┐   │
│  │  JwtAuthFilter│   │ WebSocketAuthInterceptor              │   │
│  │  (HTTP layer) │   │ (STOMP CONNECT frame)                 │   │
│  └──────┬───────┘   └───────────────┬──────────────────────┘   │
│         │                           │                           │
│  ┌──────▼───────────────────────────▼──────────────────────┐   │
│  │                   Controllers                            │   │
│  │  AuthController  NotificationController  WsController   │   │
│  └──────────────────────────┬───────────────────────────────┘   │
│                             │                                   │
│  ┌──────────────────────────▼───────────────────────────────┐   │
│  │                    Services                              │   │
│  │  AuthService   NotificationService   ScheduledService   │   │
│  └────────────┬──────────────────────────┬──────────────────┘   │
│               │                          │                      │
│  ┌────────────▼────────────┐  ┌──────────▼───────────────────┐  │
│  │     Repositories        │  │  SimpMessagingTemplate       │  │
│  │  UserRepo  NotifRepo    │  │  (WebSocket push)            │  │
│  └────────────┬────────────┘  └──────────────────────────────┘  │
│               │                                                 │
└───────────────┼─────────────────────────────────────────────────┘
                │
                ▼
┌───────────────────────────────┐
│         DATABASE              │
│  H2 (dev) / PostgreSQL (prod) │
│  - users table                │
│  - notifications table        │
└───────────────────────────────┘
```

---

## Folder Structure

```
notification-system/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
└── src/
    ├── main/
    │   ├── java/com/notifysystem/
    │   │   │
    │   │   ├── NotificationSystemApplication.java   ← Entry point
    │   │   │
    │   │   ├── config/
    │   │   │   ├── WebSocketConfig.java             ← STOMP broker, endpoints
    │   │   │   ├── SecurityConfig.java              ← JWT, auth rules
    │   │   │   └── DataInitializer.java             ← Seed data on startup
    │   │   │
    │   │   ├── controller/
    │   │   │   ├── AuthController.java              ← POST /api/auth/login|register
    │   │   │   ├── NotificationController.java      ← CRUD + send
    │   │   │   └── WebSocketController.java         ← @MessageMapping handlers
    │   │   │
    │   │   ├── service/
    │   │   │   ├── AuthService.java                 ← Register/login logic
    │   │   │   ├── NotificationService.java         ← Core: persist + push WS
    │   │   │   └── ScheduledNotificationService.java← Cron-based triggers
    │   │   │
    │   │   ├── repository/
    │   │   │   ├── UserRepository.java
    │   │   │   └── NotificationRepository.java      ← Paginated queries, bulk update
    │   │   │
    │   │   ├── model/
    │   │   │   ├── User.java                        ← JPA entity
    │   │   │   └── Notification.java                ← JPA entity
    │   │   │
    │   │   ├── dto/
    │   │   │   ├── LoginRequest.java
    │   │   │   ├── RegisterRequest.java
    │   │   │   ├── AuthResponse.java
    │   │   │   ├── NotificationDTO.java             ← Sent over REST + WebSocket
    │   │   │   ├── SendNotificationRequest.java
    │   │   │   └── ApiResponse.java                 ← Standard envelope wrapper
    │   │   │
    │   │   ├── security/
    │   │   │   ├── JwtUtil.java                     ← Token gen/validation
    │   │   │   ├── JwtAuthFilter.java               ← HTTP filter
    │   │   │   ├── UserDetailsServiceImpl.java       ← DB-backed auth
    │   │   │   └── WebSocketAuthInterceptor.java     ← STOMP CONNECT auth
    │   │   │
    │   │   ├── enums/
    │   │   │   ├── NotificationType.java            ← INFO, SUCCESS, WARNING, ALERT, ERROR
    │   │   │   └── NotificationPriority.java        ← LOW, NORMAL, HIGH, CRITICAL
    │   │   │
    │   │   └── exception/
    │   │       ├── ResourceNotFoundException.java
    │   │       └── GlobalExceptionHandler.java      ← @RestControllerAdvice
    │   │
    │   └── resources/
    │       ├── application.properties               ← Dev config (H2)
    │       ├── application-prod.properties          ← Prod config (PostgreSQL)
    │       └── static/
    │           ├── index.html                       ← Single-page frontend
    │           ├── css/style.css
    │           └── js/app.js
    └── test/
        └── java/com/notifysystem/
```

---

## Data Flow

### Login Flow
```
Browser                 AuthController         AuthService           JwtUtil
   │                         │                     │                   │
   │── POST /api/auth/login ─▶│                     │                   │
   │                         │── login(request) ───▶│                   │
   │                         │                     │── authenticate() ─▶│
   │                         │                     │── generateToken() ─▶
   │                         │                     │◀── JWT ────────────│
   │◀── { token, username } ─│◀── AuthResponse ────│                   │
```

### Real-Time Notification Flow
```
Admin Browser           REST API          NotificationService     User Browser
      │                    │                      │                    │
      │─ POST /api/notifications/send ───────────▶│                    │
      │                    │                      │── save to DB ──────▶(DB)
      │                    │                      │                    │
      │                    │                      │── convertAndSendToUser()
      │                    │                      │        │           │
      │                    │                      │        ▼           │
      │                    │                      │    STOMP broker    │
      │                    │                      │        │           │
      │                    │                      │        └──────────▶│ PUSH
      │                    │                      │                    │ (instant)
      │◀─────── 200 OK ────│◀─────────────────────│                    │
```

### WebSocket Connection Lifecycle
```
Client                              Server
  │                                    │
  │── HTTP GET /ws (SockJS upgrade) ──▶│
  │◀── 101 Switching Protocols ────────│
  │                                    │
  │── STOMP CONNECT                    │
  │   Authorization: Bearer <JWT> ────▶│
  │                         WebSocketAuthInterceptor validates JWT
  │◀── STOMP CONNECTED ───────────────│
  │                                    │
  │── STOMP SUBSCRIBE                  │
  │   /user/{name}/queue/notifications─▶│
  │── STOMP SUBSCRIBE                  │
  │   /topic/broadcast ───────────────▶│
  │                                    │
  │   [notification arrives]           │
  │◀── STOMP MESSAGE ─────────────────│  ← real-time push
  │                                    │
  │── STOMP DISCONNECT ───────────────▶│
  │◀── TCP close ─────────────────────│
  │                                    │
  [client auto-reconnects after 5s if dropped]
```

---

## REST API Reference

### Auth Endpoints (Public)

| Method | Path                  | Body                              | Response           |
|--------|-----------------------|-----------------------------------|--------------------|
| POST   | /api/auth/register    | `{username, email, password}`     | `{token, username, role}` |
| POST   | /api/auth/login       | `{username, password}`            | `{token, username, role}` |

### Notification Endpoints (JWT Required)

| Method | Path                              | Auth        | Description              |
|--------|-----------------------------------|-------------|--------------------------|
| GET    | /api/notifications?page=0&size=20 | User+Admin  | Paginated notification feed |
| GET    | /api/notifications/unread-count   | User+Admin  | `{"count": 5}`           |
| PATCH  | /api/notifications/{id}/read      | User+Admin  | Mark single as read      |
| PATCH  | /api/notifications/{id}/unread    | User+Admin  | Mark single as unread    |
| POST   | /api/notifications/mark-all-read  | User+Admin  | Bulk mark read           |
| DELETE | /api/notifications/{id}           | User+Admin  | Delete notification      |
| POST   | /api/notifications/send           | Admin only  | Send/broadcast           |

#### POST /api/notifications/send (Admin only)
```json
{
  "title": "Server Update",
  "message": "Patch deployed successfully.",
  "type": "SUCCESS",
  "priority": "NORMAL",
  "targetUsername": "demo"   // omit to broadcast to ALL users
}
```

### Standard Response Envelope
```json
{
  "success": true,
  "message": "optional description",
  "data": { ... }
}
```

### Error Response
```json
{
  "success": false,
  "message": "Username is already taken."
}
```

### WebSocket Endpoints

| Destination                          | Direction | Description                  |
|--------------------------------------|-----------|------------------------------|
| `/ws` (SockJS endpoint)              | Handshake | Initial WS upgrade           |
| `/app/notify`                        | C → S     | Admin sends notification     |
| `/app/ping`                          | C → S     | Heartbeat                    |
| `/user/{name}/queue/notifications`   | S → C     | User-specific push           |
| `/topic/broadcast`                   | S → C     | Broadcast to all subscribers |

---

## Security Design

### HTTP Layer
- All requests pass through `JwtAuthFilter` (extends `OncePerRequestFilter`)
- Public routes: `/api/auth/**`, `/ws/**`, `/`, static assets
- JWT token extracted from `Authorization: Bearer <token>` header
- Token validated against HMAC-SHA256 signature + expiry

### WebSocket Layer
- SockJS handshake hits the `/ws` endpoint — Spring Security HTTP rules apply
- After upgrade, STOMP frames go through `WebSocketAuthInterceptor`
- On `STOMP CONNECT`, JWT is read from the `Authorization` header
- On success, `Principal` is set on the STOMP session
- Spring uses this Principal to route `/user/{name}/queue/**` correctly

### Authorization
- `ROLE_USER`:  can only read/manage their OWN notifications
- `ROLE_ADMIN`: can send notifications to any user or broadcast
- `@PreAuthorize("hasRole('ADMIN')")` on the send endpoint
- Ownership check in service layer: throws `SecurityException` if user accesses another's notification

### Passwords
- BCrypt with strength 12 (industry standard)
- Never stored in plaintext
- Never returned in any response

---

## Database Schema

```sql
CREATE TABLE users (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    username   VARCHAR(50)  UNIQUE NOT NULL,
    email      VARCHAR(100) UNIQUE NOT NULL,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(20)  NOT NULL,    -- ROLE_USER | ROLE_ADMIN
    enabled    BOOLEAN      NOT NULL,
    created_at TIMESTAMP    NOT NULL
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email    ON users(email);

CREATE TABLE notifications (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL REFERENCES users(id),
    title      VARCHAR(200) NOT NULL,
    message    VARCHAR(2000) NOT NULL,
    type       VARCHAR(20)  NOT NULL,    -- INFO|SUCCESS|WARNING|ALERT|ERROR
    priority   VARCHAR(20)  NOT NULL,    -- LOW|NORMAL|HIGH|CRITICAL
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL,
    read_at    TIMESTAMP
);

CREATE INDEX idx_notif_user_created ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notif_user_read    ON notifications(user_id, is_read);
```

**Relationships:**
- `users` ← one-to-many → `notifications`
- `notifications.user_id` → FK to `users.id` (cascade delete)

---

## How to Run Locally

### Prerequisites
- Java 17+
- Maven 3.8+ (or use included `./mvnw`)
- Git

### Step 1 — Clone
```bash
git clone <repo-url>
cd notification-system
```

### Step 2 — Run (H2 in-memory, zero config)
```bash
./mvnw spring-boot:run
```
Or on Windows:
```cmd
mvnw.cmd spring-boot:run
```

### Step 3 — Open
```
http://localhost:8080
```

### Default Credentials
| User  | Password   | Role       |
|-------|------------|------------|
| admin | admin123   | ROLE_ADMIN |
| demo  | demo123    | ROLE_USER  |

### H2 Database Console (dev)
```
http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:notifydb
Username: sa
Password: (blank)
```

### Testing the System
1. Open two browser tabs: one as **admin**, one as **demo**
2. In admin tab → Sign In with `admin / admin123`
3. In demo tab → Sign In with `demo / demo123`
4. In admin's **"Send Notification"** panel → send to `demo`
5. Watch the toast and bell appear in demo's tab **instantly**
6. Try broadcasting (leave Target User blank) → both tabs get it

---

## How to Deploy

### Option A — JAR Deployment (Single Server)

```bash
# 1. Build
./mvnw clean package -DskipTests

# 2. Set environment variables
export JWT_SECRET="your-base64-encoded-secret-min-64-chars"
export DB_HOST="your-postgres-host"
export DB_USER="notifyuser"
export DB_PASSWORD="your-db-password"

# 3. Run with prod profile
java -jar target/notification-system-1.0.0.jar \
     --spring.profiles.active=prod \
     -Xmx512m
```

### Option B — Docker (Recommended)

```bash
# Development (H2 — no database needed)
docker build -t notifysystem .
docker run -p 8080:8080 notifysystem

# Production (PostgreSQL)
# Edit docker-compose.yml → set JWT_SECRET
docker compose up -d

# View logs
docker compose logs -f app

# Stop
docker compose down
```

### Option C — Cloud Deployment

#### AWS Elastic Beanstalk
```bash
# Package
./mvnw clean package -DskipTests

# EB CLI
eb init notifysystem --platform java
eb create production --envvars "SPRING_PROFILES_ACTIVE=prod,JWT_SECRET=...,DB_HOST=..."
eb deploy
```

#### Railway / Render / Fly.io
```bash
# railway.app (zero-config)
railway login
railway init
railway up

# Set env vars in Railway dashboard:
# SPRING_PROFILES_ACTIVE=prod
# JWT_SECRET=...
# DATABASE_URL (auto-injected by Railway PostgreSQL plugin)
```

#### Kubernetes (Production Scale)
```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: notifysystem
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: app
        image: notifysystem:1.0.0
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: prod
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: notifysystem-secrets
              key: jwt-secret
        ports:
        - containerPort: 8080
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
        resources:
          requests: { memory: "256Mi", cpu: "250m" }
          limits:   { memory: "512Mi", cpu: "500m" }
```

> **Note for K8s / multi-instance:** Replace the in-memory STOMP broker  
> with a RabbitMQ relay broker (add `spring-boot-starter-reactor-netty`  
> + configure `config.enableStompBrokerRelay(...)` in WebSocketConfig).

---

## Configuration Reference

| Property                    | Default         | Description                          |
|-----------------------------|-----------------|--------------------------------------|
| `server.port`               | 8080            | HTTP port                            |
| `app.jwt.secret`            | *(dev only)*    | Base64-encoded HMAC-SHA256 key       |
| `app.jwt.expiration`        | 86400000 (24h)  | Token TTL in milliseconds            |
| `spring.datasource.url`     | H2 in-memory    | JDBC URL                             |
| `spring.jpa.ddl-auto`       | create-drop     | Use `validate` in production         |
| `spring.h2.console.enabled` | true            | Disable in production                |

### Generating a secure JWT secret
```bash
openssl rand -base64 64
```

---

## Scalability Notes

### Current Design (Single Instance)
- In-memory STOMP broker (SimpleBroker)
- H2 or PostgreSQL
- All WebSocket state is local to the JVM
- Works perfectly for thousands of concurrent connections

### Scaling to Multiple Instances
Replace the in-memory broker with a message relay:

**Step 1 — Add RabbitMQ dependency**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-reactor-netty</artifactId>
</dependency>
```

**Step 2 — Update WebSocketConfig**
```java
@Override
public void configureMessageBroker(MessageBrokerRegistry config) {
    config.enableStompBrokerRelay("/topic", "/queue")
          .setRelayHost("rabbitmq-host")
          .setRelayPort(61613)
          .setClientLogin("guest")
          .setClientPasscode("guest");
    config.setApplicationDestinationPrefixes("/app");
    config.setUserDestinationPrefix("/user");
}
```

### Additional Production Hardening
- [ ] Rate limiting on auth endpoints (Spring Cloud Gateway / Resilience4j)
- [ ] Notification delivery receipts (confirm WebSocket receipt)
- [ ] User notification preferences (per-type mute settings)
- [ ] Redis for session sharing (if using session-based features)
- [ ] CDN for static assets
- [ ] TLS termination at load balancer level
- [ ] Structured logging with Logstash/Splunk
- [ ] Distributed tracing (Spring Cloud Sleuth + Zipkin)

---

## Monitoring

### Actuator Endpoints
```
GET /actuator/health   → App + DB health
GET /actuator/info     → App version info
GET /actuator/metrics  → JVM, HTTP, custom metrics
```

### Key Metrics to Watch
- `http.server.requests` — API response times
- `jvm.memory.used` — heap usage
- `hikaricp.connections.active` — DB connection pool
- `spring.integration.channels.queues.size` — WS message queue depth

---

*Built with ❤️ using Spring Boot 3.2 + Java 17*
