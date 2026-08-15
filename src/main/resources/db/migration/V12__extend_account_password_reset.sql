-- 비밀번호 재설정 token은 이메일 인증 token과 같은 one-time token 테이블을 공유하되 purpose로 생명주기를 분리합니다.
ALTER TABLE account_verification_token
    DROP CONSTRAINT chk_account_verification_token_purpose;

-- 기존 EMAIL_VERIFICATION 허용값을 유지하면서 PASSWORD_RESET purpose만 추가합니다.
ALTER TABLE account_verification_token
    ADD CONSTRAINT chk_account_verification_token_purpose CHECK (
        purpose IN (
            'EMAIL_VERIFICATION',
            'PASSWORD_RESET'
        )
    );

-- PostgreSQL check constraint는 허용값 append가 불가능하므로 최신 auth_event taxonomy constraint를 교체합니다.
ALTER TABLE auth_event
    DROP CONSTRAINT chk_auth_event_type;

-- V11 관리자 초대 event type을 유지하면서 비밀번호 재설정 요청/완료 event type을 추가합니다.
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
            'EMAIL_VERIFICATION_CONFIRM',
            'ADMIN_INVITATION_CREATED',
            'ADMIN_INVITATION_ACCEPTED',
            'ADMIN_INVITATION_REVOKED',
            'PASSWORD_RESET_REQUESTED',
            'PASSWORD_RESET_COMPLETED'
        )
    );
