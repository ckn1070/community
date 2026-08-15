-- 권한 계층 순서대로 seed해 기본 role id도 상위 권한부터 배정합니다.
INSERT INTO auth_role (code, name, description)
VALUES
    ('ROLE_SUPER_ADMIN', 'Super Admin', '관리자 계정과 관리자 역할을 관리할 수 있는 최고 관리자입니다.'),
    ('ROLE_ADMIN', 'Admin', '운영 관리 권한을 가진 관리자입니다.'),
    ('ROLE_MEMBER', 'Member', '일반 회원 권한입니다.');
