CREATE TABLE account_profile (
    account_id BIGINT NOT NULL,
    display_name VARCHAR(30) NOT NULL,
    display_name_normalized VARCHAR(30) NOT NULL,
    profile_image_url VARCHAR(500),
    bio VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_account_profile PRIMARY KEY (account_id),

    CONSTRAINT fk_account_profile_account FOREIGN KEY (account_id) REFERENCES account (id),

    -- 표시 이름 중복은 domain 정규화 결과를 저장한 normalized 값으로만 판단합니다.
    CONSTRAINT uk_account_profile_display_name_normalized UNIQUE (display_name_normalized),

    CONSTRAINT chk_account_profile_updated_at CHECK (updated_at >= created_at),
    -- 앞뒤 공백만 있는 이름이나 저장 한계를 넘는 이름이 adapter 우회 경로로 들어오지 못하게 합니다.
    CONSTRAINT chk_account_profile_display_name_length CHECK (
        CHAR_LENGTH(BTRIM(display_name)) BETWEEN 1 AND 30
        AND CHAR_LENGTH(BTRIM(display_name_normalized)) BETWEEN 1 AND 30
    )
);
