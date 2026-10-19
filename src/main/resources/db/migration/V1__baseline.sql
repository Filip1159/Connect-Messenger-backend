-- Baseline: the schema as the entities describe it (what `ddl-auto=update` built up to the end of roadmap section 3).
-- Existing databases are marked as being at this version (spring.flyway.baseline-on-migrate), see README.

create table users (
    id         bigint       not null auto_increment primary key,
    username   varchar(255) unique,
    name       varchar(255),
    surname    varchar(255),
    avatar_key varchar(255),
    last_seen  datetime(6),
    password   varchar(255)
) engine = InnoDB;

create table chats (
    id         bigint      not null auto_increment primary key,
    name       varchar(60),
    group_chat bit         not null default b'0'
) engine = InnoDB;

create table messages (
    id          bigint not null auto_increment primary key,
    chat_id     bigint,
    user_id     bigint,
    time        datetime(6),
    type        enum ('TEXT', 'FILE'),
    content     varchar(1000),
    edited_at   datetime(6),
    deleted_at  datetime(6),
    reply_to_id bigint,
    index idx_messages_chat_id (chat_id, id)
) engine = InnoDB;

create table status (
    user_id     bigint not null,
    chat_id     bigint not null,
    message_id  bigint,
    time        datetime(6),
    group_admin bit    not null default b'0',
    archived    bit    not null default b'0',
    muted       bit    not null default b'0',
    primary key (user_id, chat_id),
    index idx_status_chat_id (chat_id)
) engine = InnoDB;

create table user_blocks (
    id         bigint not null auto_increment primary key,
    blocker_id bigint,
    blocked_id bigint,
    unique key uk_user_blocks (blocker_id, blocked_id)
) engine = InnoDB;

create table message_reactions (
    id         bigint not null auto_increment primary key,
    message_id bigint,
    user_id    bigint,
    emoji      varchar(255),
    unique key uk_message_reactions (message_id, user_id)
) engine = InnoDB;
