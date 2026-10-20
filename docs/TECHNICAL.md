### >>> <i>CONNECT</i> messenger
Just a small project, which helped me learn React & Java Spring Boot framework <br>
[See this in action :earth_africa:](https://filip1159.github.io/connect-messenger-react) <br>
[Client side app :computer:](https://github.com/Filip1159/connect-messenger-react)

## Running locally

Quick start (no MySQL, no env vars): `SPRING_PROFILES_ACTIVE=dev sh ./gradlew bootRun` - runs on in-memory H2 seeded from `data.sql`, with chat images kept in memory instead of S3.

For a real database (default profile):

Requirements: JDK 25 (Gradle toolchain), MySQL with an empty `connectmessenger` database.

Environment variables:

| Variable | Required | Default | Description |
|---|---|---|---|
| `DB_USERNAME`, `DB_PASSWORD` | yes | - | MySQL credentials |
| `DB_URL` | no | `jdbc:mysql://localhost:3306/connectmessenger` | JDBC url |
| `JWT_SECRET` | yes | - | token signing key, at least 32 characters |
| `ALLOWED_ORIGINS` | no | `http://localhost:3000,https://filip1159.github.io` | comma separated CORS / Socket.IO origins |
| `S3_BUCKET`, `AWS_REGION` | no | `connect-messenger`, `eu-central-1` | where chat images are stored |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | for file messages | - | standard AWS credentials chain |
| `SPRING_PROFILES_ACTIVE` | no | - | `dev` drops and recreates the schema on every start and loads `data.sql` demo data |

```
export DB_USERNAME=root DB_PASSWORD=... JWT_SECRET=$(openssl rand -hex 32) SPRING_PROFILES_ACTIVE=dev
./gradlew bootRun
```

Without the `dev` profile no demo data is loaded and the schema is created and changed by the Flyway migrations in `src/main/resources/db/migration` (Hibernate never changes it; the Testcontainers tests check that the migrations match the entities).

### Docker

`MYSQL_PASSWORD=... JWT_SECRET=$(openssl rand -hex 32) docker compose up --build` starts the backend (port 8080) and MySQL; add `--profile frontend` to also build `../connect-messenger-react` and serve it on port 3000. The compose file turns on JSON logs (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`). Every push to `master` publishes `ghcr.io/filip1159/connect-messenger-java` (see `.github/workflows/ci.yml`).

### Operations

- **Health:** `/actuator/health` (and `/liveness`, `/readiness`) are public and show only `UP`/`DOWN`; `/actuator/metrics` needs a login.
- **Request id:** every response has an `X-Request-Id` header (a proxy's id is reused when it is short and harmless), and every log line carries it.
- **Login throttling:** 5 failed logins within 15 minutes lock the username, and separately the IP address, for 15 minutes (`429` with `Retry-After`). Tune with `custom.login-max-failures`, `custom.login-failure-window`, `custom.login-lockout`. The counters are in memory (per instance). Behind a reverse proxy set `SERVER_FORWARD_HEADERS_STRATEGY=native`, or every client looks like the proxy.
- **Tokens:** a token that is more than half used up is replaced: the response carries a fresh one in the `Authentication` header, and the frontend keeps it, so an active session does not end after an hour.

### API versions

The REST API is under `/api/v1` (the Socket.IO server on port 9092 is not versioned). The earlier unversioned paths (`/chats/chat/{id}`, `/message`, `/user/{text}`, `/login`, the Base64 file endpoint, ...) were removed and answer 404; breaking changes will go to `/api/v2` next to `v1`.

### Websocket events

Realtime uses Socket.IO (netty-socketio, websocket transport only) on its own port, `REALTIME_PORT` / `realtime.port` (default 9092), so a deployment has to publish that port next to 8080 (or proxy it). Connect with `io(url, { transports: ["websocket"], auth: { token: "Bearer <jwt>" } })`; a wrong token is refused (`connect_error`, "unauthorized"), a connection that sends none is closed after 5 seconds, and a browser page from an origin outside `ALLOWED_ORIGINS` is refused. Then `emit("subscribe", room, ack)` (the ack is `{ok: true|false}`) and `emit("unsubscribe", room)` choose what to follow; a new connection starts without rooms, so a client subscribes again after a reconnect. Everything the server pushes is the Socket.IO event `event` with the argument `{"type": "...", "payload": ...}`: `message.created|edited|deleted|reacted` in room `/topic/messages/{chatId}`, `status.updated` in `/topic/status/{chatId}`, `typing` in `/topic/typing/{chatId}`, `presence.changed` in `/topic/presence/{userId}`, `chat.changed` in `/topic/new-chats/{userId}`, `chat.removed` in `/topic/removed-chats/{userId}`. Subscribing is checked per room (chat members only, own user rooms only). A client ignores types it does not know. The only other thing a client sends is `emit("typing", chatId)`.

### Database migrations

New schema changes are new files `V<n>__description.sql` in `src/main/resources/db/migration`; never edit one that was released. A database that was created by the earlier `ddl-auto=update` is adopted automatically as version 1, so if it is older than the roadmap sections 2 and 3 it lacks their columns: run `docs/pre-flyway-upgrade.sql` against it once, before the first start.

### Rotating secrets

The JWT secret `vegetables` and the root/root database credentials were committed in the past and are in the git history, so treat them as public. The application refuses to start with the old secret. In each real environment: set a new `JWT_SECRET` (`openssl rand -hex 32`; everybody has to sign in again), change the database password and the AWS keys, and revoke the old ones.

## API description

`GET /v3/api-docs` (signed-in users) returns the OpenAPI description. `docs/openapi.json` is its committed copy; `ApiSpecTest`
fails when it is out of date. After changing an endpoint or a DTO run `UPDATE_OPENAPI=true sh ./gradlew test --tests '*ApiSpecTest'`,
then copy the file to the frontend (`src/api/openapi.json`) and run `npm run gen:api` there.
