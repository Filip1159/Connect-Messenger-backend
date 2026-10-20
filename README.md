# >>> *CONNECT* messenger

A real-time chat: talk one-to-one or in groups, send photos, react to messages with emoji, edit or delete them, and see who is online, who is typing and who has read what. It comes with a dark and a light theme, several languages, desktop notifications and profile pictures. This is the server (Java, Spring Boot); the web app lives in [connect-messenger-react](https://github.com/Filip1159/connect-messenger-react).

## Screenshots

<p align="center">
  <img src="docs/screenshots/login.png" width="48%" alt="Sign in">
  <img src="docs/screenshots/conversation-dark.png" width="48%" alt="A conversation in the dark theme">
</p>
<p align="center">
  <img src="docs/screenshots/group.png" width="48%" alt="A group chat">
  <img src="docs/screenshots/conversation-light.png" width="48%" alt="The light theme">
</p>
<p align="center">
  <img src="docs/screenshots/settings.png" width="48%" alt="Settings">
</p>

```sh
# start with demo data, no database needed
SPRING_PROFILES_ACTIVE=dev sh ./gradlew bootRun
```

```sh
# run the tests
sh ./gradlew test
```

```sh
# start the server with a database in Docker
MYSQL_PASSWORD=secret JWT_SECRET=$(openssl rand -hex 32) docker compose up --build
```
