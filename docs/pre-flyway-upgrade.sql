-- For a MySQL database created by the former `ddl-auto=update`, from BEFORE roadmap sections 2 and 3 (messages with
-- edits/deletes/replies/reactions, registration, groups, archive/mute, blocking, avatars, last seen).
-- Run it once, by hand, before the first start with Flyway. MySQL has no `add column if not exists`: skip the
-- statements for columns that already exist (`show columns from <table>`). Then V1 is recorded automatically.

alter table messages add column edited_at datetime(6);
alter table messages add column deleted_at datetime(6);
alter table messages add column reply_to_id bigint;

alter table chats add column name varchar(60);
alter table chats add column group_chat bit not null default b'0';

alter table status add column group_admin bit not null default b'0';
alter table status add column archived bit not null default b'0';
alter table status add column muted bit not null default b'0';

alter table users add column avatar_key varchar(255);
alter table users add column last_seen datetime(6);

create table if not exists user_blocks (
    id         bigint not null auto_increment primary key,
    blocker_id bigint,
    blocked_id bigint,
    unique key uk_user_blocks (blocker_id, blocked_id)
) engine = InnoDB;

create table if not exists message_reactions (
    id         bigint not null auto_increment primary key,
    message_id bigint,
    user_id    bigint,
    emoji      varchar(255),
    unique key uk_message_reactions (message_id, user_id)
) engine = InnoDB;

alter table messages add index idx_messages_chat_id (chat_id, id);
alter table status add index idx_status_chat_id (chat_id);
