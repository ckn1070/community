ALTER TABLE refresh_token
    ADD COLUMN account_public_id UUID;

UPDATE refresh_token token
SET account_public_id = account.public_id
FROM account
WHERE token.account_id = account.id
  AND token.account_public_id IS NULL;

ALTER TABLE refresh_token
    ADD CONSTRAINT fk_refresh_token_account_public_id
        FOREIGN KEY (account_public_id) REFERENCES account (public_id);

CREATE INDEX idx_refresh_token_active_account_public
    ON refresh_token (account_public_id, expires_at)
    WHERE revoked_at IS NULL;

CREATE FUNCTION sync_refresh_token_account_identity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    resolved_account_id BIGINT;
    resolved_public_id UUID;
BEGIN
    IF NEW.account_id IS NOT NULL THEN
        SELECT public_id INTO resolved_public_id FROM account WHERE id = NEW.account_id;
        IF resolved_public_id IS NULL THEN
            RAISE EXCEPTION 'unknown refresh_token account_id: %', NEW.account_id;
        END IF;
    END IF;

    IF NEW.account_public_id IS NOT NULL THEN
        SELECT id INTO resolved_account_id FROM account WHERE public_id = NEW.account_public_id;
        IF resolved_account_id IS NULL THEN
            RAISE EXCEPTION 'unknown refresh_token account_public_id: %', NEW.account_public_id;
        END IF;
    END IF;

    IF NEW.account_id IS NOT NULL AND NEW.account_public_id IS NOT NULL
        AND NEW.account_id <> resolved_account_id THEN
        RAISE EXCEPTION 'refresh_token account identity mismatch';
    END IF;

    NEW.account_id = COALESCE(NEW.account_id, resolved_account_id);
    NEW.account_public_id = COALESCE(NEW.account_public_id, resolved_public_id);

    IF NEW.account_id IS NULL OR NEW.account_public_id IS NULL THEN
        RAISE EXCEPTION 'refresh_token account identity is required';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_refresh_token_account_identity
BEFORE INSERT OR UPDATE OF account_id, account_public_id ON refresh_token
FOR EACH ROW
EXECUTE FUNCTION sync_refresh_token_account_identity();
