CREATE TABLE roles (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE,
    weight INTEGER NOT NULL DEFAULT 0,
    elevatable_duration_seconds INTEGER
);

CREATE TABLE groups (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE
);

CREATE TABLE role_permissions (
    role_id VARCHAR(36) REFERENCES roles(id) ON DELETE CASCADE,
    permission VARCHAR(255) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

CREATE TABLE group_permissions (
    group_id VARCHAR(36) REFERENCES groups(id) ON DELETE CASCADE,
    permission VARCHAR(255) NOT NULL,
    PRIMARY KEY (group_id, permission)
);

CREATE TABLE role_groups (
    role_id VARCHAR(36) REFERENCES roles(id) ON DELETE CASCADE,
    group_id VARCHAR(36) REFERENCES groups(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, group_id)
);

CREATE TABLE player_roles (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid VARCHAR(36) NOT NULL,
    role_id VARCHAR(36) REFERENCES roles(id) ON DELETE CASCADE,
    world VARCHAR(64),
    UNIQUE (player_uuid, role_id, world)
);

CREATE TABLE elevation_cooldowns (
    player_uuid VARCHAR(36) PRIMARY KEY,
    expires_at TIMESTAMP NOT NULL
);

CREATE TABLE audit_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    player_uuid VARCHAR(36) NOT NULL,
    action VARCHAR(32) NOT NULL,
    role_id VARCHAR(36) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    trigger_source VARCHAR(128) NOT NULL
);