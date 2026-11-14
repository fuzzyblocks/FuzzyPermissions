CREATE TABLE roles (
    id UUID PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE,
    weight INT NOT NULL DEFAULT 0,
    elevatable_duration_seconds INT
);

CREATE TABLE groups (
    id UUID PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE
);

CREATE TABLE role_permissions (
    role_id UUID REFERENCES roles(id) ON DELETE CASCADE,
    permission VARCHAR(255) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

CREATE TABLE group_permissions (
    group_id UUID REFERENCES groups(id) ON DELETE CASCADE,
    permission VARCHAR(255) NOT NULL,
    PRIMARY KEY (group_id, permission)
);

CREATE TABLE role_groups (
    role_id UUID REFERENCES roles(id) ON DELETE CASCADE,
    group_id UUID REFERENCES groups(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, group_id)
);

CREATE TABLE player_roles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    role_id UUID REFERENCES roles(id) ON DELETE CASCADE,
    world VARCHAR(64),
    UNIQUE (player_uuid, role_id, world)
);

CREATE TABLE elevation_cooldowns (
    player_uuid VARCHAR(36) PRIMARY KEY,
    expires_at TIMESTAMP NOT NULL
);

CREATE TABLE audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    player_uuid VARCHAR(36) NOT NULL,
    action VARCHAR(32) NOT NULL,
    role_id UUID NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    trigger_source VARCHAR(128) NOT NULL
);