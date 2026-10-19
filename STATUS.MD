# STATUS

Roadmap of features to implement next, based on a review of the React frontend
(`connect-messenger-react`: Vite, Redux Toolkit, TypeScript, STOMP/SockJS, axios) and the current backend.

Legend: **FE** = frontend work, **BE** = backend work. Size: S (hours), M (a day or two), L (several days).

## What exists today

Login (JWT in `localStorage`, auto sign-out on expiry), list of 1:1 chats sorted by last message, user search
by query, creating a chat, text and image messages, real-time delivery over STOMP, "seen" avatars driven by
per-user status, sign-out. Chats are always created with exactly two users.

## 0. Prerequisite: migrate the frontend to the new backend API (FE, S–M) — DONE

The frontend now uses the new endpoints. Backend additions made for it: `userId`/`time` optional in `POST /message`
and `PUT /status` (taken from the session and the server clock), `GET /chats/{userId}?includeMessages=false`.
**Remaining backend cleanup** (safe once the frontend is deployed): set `custom.legacy-status-broadcast=false`,
remove the Base64 endpoint, make `includeMessages=false` the default / drop `Chat.messages` eager loading, and stop
accepting client-supplied `userId`/`time`.

| # | Task | Frontend place | Unblocks on the backend |
|---|------|----------------|--------------------------|
| 0.1 ✅ | Subscribe to `/topic/status/{chatId}` in addition to `/topic/messages/{chatId}`; stop discriminating message vs. status with `"content" in parsed` | `ChatAPI.subscribe`, `useChatSync.onMessage` | drop `custom.legacy-status-broadcast` |
| 0.2 ✅ | Load images with `GET /message/file/{name}/raw` (as a `blob` + `URL.createObjectURL`, revoke on unmount) instead of Base64 + magic-byte sniffing | `ChatAPI.loadFile`, `Message.tsx` | remove the Base64 endpoint |
| 0.3 ✅ | Load history from `GET /chats/chat/{chatId}/messages?before=&size=` (infinite scroll upwards, keep scroll position) | `chatsSlice`, `MessagesSection` | return chats without messages from `GET /chats/{userId}` (fixes B10 fully, drops the eager `Chat.messages`) |
| 0.4 ✅ | Stop sending `userId` in `POST /message` and `PUT /status` (the server knows the principal) | `ChatAPI.postMessage`, `updateStatus` | remove client-supplied identity fields (spoofing surface) |
| 0.5 ✅ | Use the server time instead of `DateFormatter.nowToSql()` (client clocks and time zones differ) | `ChatAPI.postMessage`, `updateStatus` | server-assigned timestamps |

## 1. Core messaging gaps (high value) — DONE

| # | Feature | Details | Size |
|---|---------|---------|------|
| 1.1 ✅ | **Unread counters and badges** | Chat list shows no unread state. Count messages with `id > myStatus.messageId`; bold preview and badge in `ChatItem`; unread count in the tab title. BE: return `unreadCount` with the chat list so it works once messages are paged. | M (FE+BE) |
| 1.2 ✅ | **Online presence and "last seen"** | BE: track STOMP connect/disconnect events, publish to `/topic/presence/{userId}`, expose `lastSeen` on `User`. FE: green dot on `Avatar`, "active x min ago" in the chat header. | M (FE+BE) |
| 1.3 ✅ | **Typing indicator** | Ephemeral STOMP message `/app/chats/{chatId}/typing`, relayed to `/topic/typing/{chatId}`; FE shows "... is typing" under the messages, throttled to one event per 2-3 s. No persistence. | S–M |
| 1.4 ✅ | **Message delivery state and send errors** | `NewMessageInput` only logs failures to the console. Optimistic message with sending / sent / failed state and a retry button; disable the send button while a request is in flight. | S |
| 1.5 ✅ | **Reconnect catch-up** | After a websocket reconnect, messages sent while offline are lost until a reload. On `onConnect` fetch messages newer than the last known id (`GET .../messages?after=`) and merge. BE: add the `after` parameter. | M (FE+BE) |
| 1.6 ✅ | **Auto-scroll that respects the reader** | `MessagesSection` scrolls to the bottom on every new message, even when the user is reading old ones. Scroll only when already at the bottom; otherwise show a "new messages" button. | S |

Notes on what was built:

- **Unread (1.1):** `ChatDto.unreadCount` from the server (messages of others after my last seen one); live increments on the
  client, reset when my read status is accepted; badge in the chat list, `(n) CONNECT` tab title.
- **Presence (1.2):** `PresenceService` counts websocket sessions per user, stores `User.lastSeen` on the last disconnect,
  `GET /presence` (contacts only) and `/topic/presence/{userId}` (allowed only for people you share a chat with).
  `User.lastSeen` is a new column, so production needs the schema change (ddl-auto=update does it; see 5.7).
- **Typing (1.3):** `SEND /app/chats/{id}/typing` (members only; every other client SEND is still refused) relayed to
  `/topic/typing/{id}`; the client throttles to one event per 2.5 s and hides it after 4 s or when the message arrives.
- **Sending (1.4):** optimistic outbox with sending / failed + retry / discard.
- **Catch-up (1.5):** `GET /chats/chat/{id}/messages?after=` plus a refresh of statuses, unread counts, new chats and
  presence after every reconnect.
- **Scrolling (1.6):** follows new messages only at the bottom or for my own; otherwise a "New messages" button.
- Verified against a real backend (dev profile) with a STOMP client: presence online/offline events, typing relay, and that
  foreign presence/typing subscriptions are refused. Not covered by automated tests: the browser's real websocket.

## 2. Message features — DONE

| # | Feature | Details | Size |
|---|---------|---------|------|
| 2.1 ✅ | **Image preview and lightbox** | Click an image to open it full size; show a loading placeholder and an error state instead of an empty `<img>`. Needs 0.2 (blob URLs), BE can add thumbnails. | S–M |
| 2.2 ✅ | **Emoji picker, multi-line input, Enter/Shift+Enter** | The input is a single-line `<input type="text">`; switch to an auto-growing textarea. | S |
| 2.3 ✅ | **Drag and drop / paste images, attachment preview with remove** | Today a chosen file replaces the icon, and cannot be cancelled except by typing. Validate type/size (`MaxUploadSizeExceeded` returns 413) before upload and show the message. | S–M |
| 2.4 ✅ | **Delete and edit own messages** | BE: `DELETE /message/{id}`, `PATCH /message/{id}`, soft delete, broadcast an event to `/topic/messages/{chatId}` (needs an event type field, see 5.2). FE: context menu, "edited" label. | M (FE+BE) |
| 2.5 ✅ | **Message search within a chat** | BE: `GET /chats/chat/{id}/messages?query=` (LIKE, later full text). FE: search box in the conversation header. | M (FE+BE) |
| 2.6 ✅ | **Links and formatting** | Linkify URLs (safely, `rel="noopener noreferrer"`), preserve newlines. Never render message content as HTML. | S |
| 2.7 ✅ | **Reactions and replies** | Emoji reactions; reply-to quoting. Needs schema additions (`message_reaction`, `reply_to_id`). | L |

Notes on what was built:

- **Images (2.1):** loading placeholder, error state, click opens a lightbox (Esc / click outside / button; focus returns to the image). No thumbnails: the original is loaded.
- **Input (2.2):** auto-growing textarea (Enter sends, Shift+Enter new line, IME-safe), a small built-in emoji picker (no dependency).
- **Attachments (2.3):** preview with a remove button, drag and drop on the form, paste into the field; type (JPG/PNG/GIF) and size (5 MB) are checked before the upload. With text and an image, the image is sent first, then the text.
- **Edit and delete (2.4):** `PATCH /message/{id}` (`{textContent}`) and `DELETE /message/{id}` (soft: `deletedAt`, the DTO has `deleted=true` and no content, the file is no longer served). `Message.editedAt` / `deletedAt` are new columns.
  No separate event type: the updated `MessageDto` (same id) is published on `/topic/messages/{chatId}` and the client replaces the known message.
- **Search (2.5):** `GET /chats/chat/{id}/messages?query=` (case-insensitive substring of text messages, newest first, `before` pages, 1-100 chars). The panel jumps to a result by loading older pages until it is there (up to 40 pages).
- **Links (2.6):** http(s) only, `rel="noopener noreferrer nofollow"`, content is never rendered as HTML, newlines kept.
- **Replies and reactions (2.7):** `POST /message` takes `replyToId` (must be in the same chat); `MessageDto.replyToId`. The quote is built from the loaded history or fetched with `GET /message/{id}`.
  `PUT /message/{id}/reaction` (`{emoji}`, one per user, replaces the previous) and `DELETE /message/{id}/reaction`, from a fixed set (`ReactionService.ALLOWED_EMOJIS`, mirrored in `src/reactions.ts`).
  `MessageDto.reactions = [{emoji, userIds}]`, loaded with one query per page. New table `message_reactions` (unique per message and user) and column `messages.reply_to_id`.
  Reactions are not part of the legacy `GET /chats/{userId}` with messages, only of the paged message endpoints.
- **Schema:** `messages.edited_at`, `messages.deleted_at`, `messages.reply_to_id` and `message_reactions` need a migration in production (5.7).
- **Known gap:** catch-up after a reconnect (`?after=`) only brings new messages, so an edit, delete or reaction made while the client was offline shows after a reload.
- Also fixed: the invisible hover tooltip of a message could cover buttons of other messages (`pointer-events: none`).

## 3. Chats and users — DONE

| # | Feature | Details | Size |
|---|---------|---------|------|
| 3.1 ✅ | **Account registration** | There is no sign-up; users only come from `data.sql`. BE: `POST /users` (validation, password policy, unique username, BCrypt). FE: registration page and link from the login page. | M (FE+BE) |
| 3.2 ✅ | **Profile editing** | Change name, surname, password; real avatar upload (S3) instead of the generated `Avatar` from the username. `UserDetailsSection` is read-only today. | M (FE+BE) |
| 3.3 ✅ | **Group chats** | Backend model already supports `Chat.users` with N users, but `createChat` always sends two ids and `getRecipient` assumes one other participant. FE: multi-select in search, group name, avatars stack; BE: chat title, add/remove member, admin role, authorisation on those. | L |
| 3.4 ✅ | **Prevent duplicate 1:1 chats** | Creating a chat with the same person twice makes a second chat. BE: return the existing one (idempotent create); FE: just activate it. | S |
| 3.5 ✅ | **Leave / archive / mute chat** | Per-user chat flags; hide archived chats from the list. | M (FE+BE) |
| 3.6 ✅ | **Block user** | Prevents new chats and messages from a blocked user. | M (FE+BE) |

Notes on what was built:

- **Registration (3.1):** `POST /users` is public (`{username, name, surname, password}` -> 201 `UserDto`). Username 3-30 of `[A-Za-z0-9._-]`, unique ignoring case
  (409; also a unique column), password 8-72 characters with a letter and a digit (BCrypt, `{bcrypt}` prefix like the seed data), names 1-50.
  The frontend page `/register` validates with the same rules, then signs in. **Registration is open to anyone: add 5.4 (rate limiting) before exposing it.**
- **Profile (3.2):** `GET /users/me`, `PATCH /users/me` (`{name, surname}`), `PUT /users/me/password` (`{currentPassword, newPassword}`, 403 on a wrong one),
  `PUT /users/me/avatar` (multipart `file`, JPG/PNG/GIF by magic bytes, 2 MB) and `DELETE /users/me/avatar`, `GET /users/{id}/avatar`.
  `UserDto.avatarKey` is the uploaded file (new on every upload); without it the frontend falls back to the bundled `/images/avatars/{username}.png`.
  The frontend takes the current profile from `GET /users/me` after sign-in (the JWT keeps the name from sign-in time).
- **Groups (3.3):** `Chat.name` and `Chat.groupChat`; the admin flag lives on the membership row (`status.group_admin`). `POST /chats` `{userIds, name?}` makes a group when
  there is a name or more than two users (at most 50); the creator is its admin. `PATCH /chats/chat/{id}` renames (admin), `POST .../members` `{userIds}` (admin; a new member starts with
  nothing unread but can read the history), `DELETE .../members/{userId}` (admin removes, or a member leaves = removes themselves; the first remaining member becomes admin when the last admin goes),
  `PUT|DELETE .../admins/{userId}` (admin; a group keeps at least one admin). All other changes: 403 for non-admins, 400 for 1:1 chats.
  `ChatDto` gained `name`, `group`, `adminIds`, `archived`, `muted`.
  Changes are pushed to every member's `/topic/new-chats/{userId}` (the chat as that member sees it); the removed user gets `/topic/removed-chats/{userId}` and the server
  also drops their STOMP subscriptions to the chat's topics (`ChatNotifier`; verified against a real STOMP client), otherwise they would keep receiving messages.
  Frontend: "New group" dialog next to the search, chat details dialog (info button in the conversation header), author names above received messages, member count / who is typing in the header,
  no "seen" avatar in groups.
- **One chat per pair (3.4):** `POST /chats` with two users returns the existing 1:1 chat (and un-archives it for the caller) instead of creating a second one; no broadcast then.
  The frontend opens it.
- **Archive and mute (3.5):** `PATCH /chats/chat/{id}/settings` `{archived?, muted?}`, personal (stored on the membership row, nobody else is told). Archived chats are listed in a collapsible
  "Archived" section; muted chats don't count in the tab title (and are ready for the notifications in 4.3). Leaving applies to groups only (a 1:1 chat is archived instead).
- **Blocking (3.6):** `PUT|DELETE /users/{id}/block`, `GET /users/blocked`. Either direction of a block stops new 1:1 chats (403), messages in an existing 1:1 chat (403)
  and adding the person to a group; people who blocked you are missing from the user search. Groups themselves are not affected. The frontend replaces the input with a notice (and an Unblock button)
  and lists blocked people in the profile dialog.
- **Fixed on the way:** CORS allowed only GET/POST/PUT, so `PATCH` and `DELETE` (message edit/delete from 2.4) would have been refused by browsers on another origin; `PUT /status` replaced the
  whole membership row (it would now reset the admin/archived/muted flags), it updates the row instead; the sync hook restarted on every profile change (it now follows the user id only).
- **Schema:** `chats.name`, `chats.group_chat`, `status.group_admin`, `status.archived`, `status.muted`, `users.avatar_key`, unique `users.username`, table `user_blocks` need a migration in production (5.7).
  The boolean columns default to false, so existing rows are 1:1 chats with no flags.
- **Known gaps:** a changed name or avatar of another person shows after the next reload or reconnect (no broadcast); there are no system messages ("X added Y"); a new message does not un-archive a chat;
  messages of a blocked person in a *group* are still shown; the person who was blocked only sees a failed message (403), not why; no avatar for a group.

## 4. UX, accessibility, mobile — DONE (frontend only)

| # | Feature | Details | Size |
|---|---------|---------|------|
| 4.1 ✅ | **Responsive layout** | Three fixed sections; on narrow screens show the chat list, then the conversation with a back button. | M |
| 4.2 ✅ | **Connection status banner** | Show "reconnecting..." when the websocket drops; the STOMP errors currently only go to `console.error`. | S |
| 4.3 ✅ | **Desktop and tab notifications** | `Notification` API and sound for messages in non-active chats or a hidden tab (uses `usePageVisible`); per-chat mute (3.5). | S–M |
| 4.4 ✅ | **Dark theme and user settings** | SCSS variables already exist in `_variables.scss`; add a theme toggle persisted in `localStorage`. | S |
| 4.5 ✅ | **i18n** | `strings.ts` mixes Polish ("Szukaj:", "Brak wiadomości") and English. Pick one language per locale and add a locale switch (e.g. `Intl`-based date formatting in `DateFormatter`). | S–M |
| 4.6 ✅ | **Accessibility pass** | Live region for incoming messages, focus management when switching chats, keyboard navigation of the chat list, check colour contrast. Buttons already have roles and labels. | S–M |
| 4.7 ✅ | **Empty and error states** | Search with no results, chat list load failure (currently `console.error` and an empty list), loading skeletons. | S |

Notes on what was built (all in `connect-messenger-react`; the backend needed nothing):

- **Responsive (4.1):** done with the redesign in section 2: chat list and conversation take turns below 768 px (back button), the right column hides below 1200 px.
  Dialogs, the banner and the new screens are checked at phone width in the e2e tests.
- **Connection banner (4.2):** `ConnectionBanner` under the header, a `role="status"` region. It shows "Connection lost. Reconnecting..." when the websocket drops after having been up,
  "You're offline" from the browser's `online`/`offline` events, and "Connecting..." only when the first connection takes more than 3 s.
- **Notifications (4.3):** settings dialog (gear in the header): desktop notifications (asks for the browser permission, explains a refusal) and a beep (made with WebAudio, no file).
  A notification is shown for a new message of somebody else when its chat is not the open one, or the tab is hidden; never for muted chats (3.5), own, deleted or edited messages.
  One per chat (`tag`), a click opens the chat. Not done: notifications while the page is closed (needs a service worker and web push, i.e. backend work).
- **Themes (4.4):** the colours are CSS variables (`base.scss`), `data-theme` on `<html>` is set before the first render; "same as the device" (follows changes), light, dark.
  The yellow brand colour is used only for fills; as text or an outline a darker variant is used on the light theme. Reaction chips stay dark gray in both themes.
  Settings (theme, language, notifications, sound) are kept in `localStorage`.
- **i18n (4.5):** `src/i18n/en.ts` (the shape) and `pl.ts`; `strings` reads the current language at use time, so a switch shows at once without losing the open dialogs or the typed text.
  The default is the browser language (Polish or English). Weekday names use `Intl`; "last seen" texts are translated. A test checks that both languages have every key.
  The mix of Polish and English in the old `strings.ts` is gone (English is the base, Polish is a full translation).
- **Accessibility (4.6):** live region that reads out new messages (`Announcer`, not for muted chats), the focus moves to the conversation title when a chat is chosen from the list, arrow keys / Home / End in the chat list,
  skip link to the message input, labelled regions (`Chats`, `Messages` log), dialogs trap and restore the focus. Contrast of every text/surface pair of both themes is a unit test (`contrast.test.ts`, WCAG AA 4.5:1;
  3:1 for focus rings); the orange of badges and the banner was darkened to pass.
- **States (4.7):** skeletons while the chat list loads, "try again" when it fails, an explanation when there are no chats and a prompt instead of an empty input when none is open,
  search messages ("type at least 2 characters", "searching", "nobody found", failure), "could not load the messages" with a retry for the first page and for older pages, "loading older messages".
- **Not covered by automated tests:** a real browser notification (the e2e tests replace `Notification`) and the sound; the real websocket (as before).


## 5. Security and platform (backend-heavy) — MOSTLY DONE (open: 5.2, rotation in 5.1, refresh tokens in 5.3)

| # | Item | Status | Notes | Size |
|---|------|--------|-------|------|
| 5.1 | **Rotate secrets** | guard DONE, rotation is yours | The app refuses to start with the leaked JWT secret `vegetables` (`CustomProperties`), and README ("Rotating secrets") lists what to change. Still to do by hand in every real environment: new `JWT_SECRET`, DB password, AWS keys; consider scrubbing the git history. | S |
| 5.2 | **Typed websocket events** | OPEN | Message, status, chat, deletion and presence currently differ only by field presence. Introduce `{type, payload}` envelopes (a topic per concern is already used for status). FE+BE, together with the legacy status broadcast removal. | M (FE+BE) |
| 5.3 | **Token strategy** | sliding expiry DONE | The token was already sent as `Bearer <jwt>` (the stored value is the whole header). Now a token past half of its life is replaced: the response carries a fresh one in `Authentication` and `api/http.ts` stores it (only while signed in), so an active session does not end after an hour. Still open: refresh tokens / revocation, and an httpOnly cookie instead of `localStorage`. | M (FE+BE) |
| 5.4 | **Login rate limiting / lockout** | DONE | `LoginThrottle`: 5 failures in 15 min lock the username and, separately, the IP for 15 min; `429` + `Retry-After`. In memory, so per instance; behind a proxy set `SERVER_FORWARD_HEADERS_STRATEGY=native`. Registration (`POST /users`) is still not throttled. | S |
| 5.5 | **Persistence tests** | DONE | `persistence/PersistenceIT` on MySQL 8.4 (Testcontainers): paging, catch-up, unread counts, search, direct-chat lookup, unique constraints, emoji round trip, adopting a pre-Flyway database. Skipped when there is no Docker. | M |
| 5.6 | **Real S3 and STOMP integration tests** | DONE | `WebsocketIT` (real STOMP client over SockJS against `RANDOM_PORT`: delivery, new-chat push, a stranger gets nothing, bad token refused) and `S3IT` (`AwsS3Client` against LocalStack). | M |
| 5.7 | **Database migrations** | DONE | Flyway, `V1__baseline.sql` is the schema of sections 0-4; `ddl-auto=none` (the `dev` profile keeps H2 + `create`). The ITs run with `validate`, so drift between migrations and entities fails CI. A database from the `ddl-auto=update` era is baselined at V1; if it predates sections 2-3 run `docs/pre-flyway-upgrade.sql` first. | M |
| 5.8 | **Observability** | DONE | Actuator: `/actuator/health[/liveness,/readiness]` public (no details), `/actuator/metrics` behind login; `X-Request-Id` on every response and in every log line (MDC); JSON logs with `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (set in compose). No Prometheus registry yet. | S |
| 5.9 | **Containerisation and deployment** | image DONE, deployment open | `Dockerfile` (multi-stage, JRE, non-root, healthcheck), `docker-compose.yml` (MySQL + backend, `--profile frontend` adds the frontend, which has its own `Dockerfile` + `nginx.conf`), and CI publishes `ghcr.io/<repo>:latest|<sha>` from master. No deploy step: it depends on where this runs. | M |
| 5.10 | **REST path cleanup** | DONE | The API is under `/api/v1`: `GET /chats` (the caller's chats, no user id in the path), `/chats/{id}`, `/chats/{id}/messages|settings|members|admins`, `/messages`, `/messages/{id}[/reaction]`, `/messages/files/{name}` (raw bytes), `GET /users?query=` (search), `/users/me...`, `/status`, `/presence`, `/login`. The former unversioned paths and the Base64 file endpoint are removed (404); the frontend uses `/api/v1` only. The websocket (`/websocket`, `/topic/**`, `/app/**`) is not versioned. Anyone else calling the old paths (other clients, scripts) has to move. | M (FE+BE) |

## 6. Frontend quality (FE)

| # | Item | Details | Size |
|---|------|---------|------|
| 6.1 | **Typed API layer** | Prepared: `ChatAPI` (a class of statics mixing auth, REST and the websocket) is gone. `src/api/` has `http.ts` (axios, token, 401), `session.ts`, `rest/{chats,messages,users}.ts` (plain functions, contract-tested), `socket.ts` (a `createSocket()` factory), and `store/chats/chatSync.ts` (plain, tested without React) behind a thin `useChatSync`. Still open: caching, error and loading state and abort handling, e.g. RTK Query on top of `rest/*`. | M |
| 6.2 | **Contract tests** | Share an OpenAPI spec (springdoc on the backend) and generate TS types so `types.ts` cannot drift from the DTOs. | M (FE+BE) |
| 6.3 | **More e2e coverage** | Playwright covers login and basic chat with a mocked backend; add scenarios for image sending, creating a chat, reconnect, expired token. Optionally run e2e against the `dev` profile (H2 + in-memory S3) in CI. | M |
| 6.4 | **Performance of long conversations** | List virtualization once paging (0.3) is in; memoize `Message`, avoid recomputing `DateFormatter` for every row on each render. | S–M |

## Suggested order

1. **0.1–0.5** migrate the frontend to the new API, then delete the legacy broadcast, the Base64 endpoint and the eager messages on the backend.
2. **1.4, 1.6, 3.4, 4.2, 4.7**: cheap fixes to everyday rough edges.
3. **1.1, 1.5, 1.3, 1.2**: unread counters, catch-up, typing, presence.
4. **3.1, 3.2**: registration and profile, so the app is usable beyond seeded data (with 5.7 migrations first).
5. **2.x** message features and **3.3** group chats (needs 5.2 typed events and 5.7 migrations).
6. Platform items (5.1, 5.4, 5.5, 5.6, 5.8, 5.9) in parallel, 5.1 first.
