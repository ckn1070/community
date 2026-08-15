CREATE TABLE account_password_credential (
    account_id BIGINT NOT NULL,
    -- 비밀번호 원문은 저장하지 않고 Spring Security encoder가 만든 hash만 저장합니다.
    password_hash VARCHAR(255) NOT NULL,
    password_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    password_change_required BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_count INTEGER NOT NULL DEFAULT 0,
    last_failed_login_at TIMESTAMPTZ,
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_account_password_credential PRIMARY KEY (account_id),

    CONSTRAINT fk_account_password_credential_account FOREIGN KEY (account_id) REFERENCES account (id),

    CONSTRAINT chk_account_password_credential_updated_at CHECK (updated_at >= created_at),
    -- 로그인 실패 횟수는 잠금 정책의 누적값이므로 음수가 될 수 없습니다.
    CONSTRAINT chk_account_password_credential_failed_login_count CHECK (failed_login_count >= 0)
);
