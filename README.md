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
| `ALLOWED_ORIGINS` | no | `http://localhost:3000,https://filip1159.github.io` | comma separated CORS / WebSocket origins |
| `S3_BUCKET`, `AWS_REGION` | no | `connect-messenger`, `eu-central-1` | where chat images are stored |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | for file messages | - | standard AWS credentials chain |
| `SPRING_PROFILES_ACTIVE` | no | - | `dev` drops and recreates the schema on every start and loads `data.sql` demo data |

```
export DB_USERNAME=root DB_PASSWORD=... JWT_SECRET=$(openssl rand -hex 32) SPRING_PROFILES_ACTIVE=dev
./gradlew bootRun
```

Without the `dev` profile the schema is only updated (`ddl-auto=update`) and no demo data is loaded.
