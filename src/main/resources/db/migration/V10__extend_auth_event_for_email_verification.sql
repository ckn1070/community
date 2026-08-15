ALTER TABLE auth_event
    DROP CONSTRAINT chk_auth_event_type;

ALTER TABLE auth_event
    ADD CONSTRAINT chk_auth_event_type CHECK (
        event_type IN (
            'ACCOUNT_SIGN_UP',
            'PASSWORD_LOGIN',
            'LOGOUT',
            'TOKEN_REFRESH',
            'PASSWORD_CHANGE',
            'PASSWORD_CHANGE_REQUIREMENT',
            'ROLE_CHANGE',
            'EMAIL_VERIFICATION_REQUEST',
            'EMAIL_VERIFICATION_CONFIRM'
        )
    );
