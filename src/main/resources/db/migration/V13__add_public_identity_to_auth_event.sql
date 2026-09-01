ALTER TABLE auth_event
    ADD COLUMN account_public_id UUID;

UPDATE auth_event event
SET account_public_id = account.public_id
FROM account
WHERE event.account_id = account.id
  AND event.account_public_id IS NULL;

ALTER TABLE auth_event
    ADD CONSTRAINT fk_auth_event_account_public_id
        FOREIGN KEY (account_public_id) REFERENCES account (public_id);

CREATE INDEX idx_auth_event_account_public_occurred_at
    ON auth_event (account_public_id, occurred_at DESC);

CREATE FUNCTION sync_auth_event_account_identity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    resolved_account_id BIGINT;
    resolved_public_id UUID;
BEGIN
    IF NEW.account_id IS NULL AND NEW.account_public_id IS NULL THEN
        RETURN NEW;
    END IF;

    IF NEW.account_id IS NOT NULL THEN
        SELECT public_id INTO resolved_public_id FROM account WHERE id = NEW.account_id;
        IF resolved_public_id IS NULL THEN
            RAISE EXCEPTION 'unknown auth_event account_id: %', NEW.account_id;
        END IF;
    END IF;

    IF NEW.account_public_id IS NOT NULL THEN
        SELECT id INTO resolved_account_id FROM account WHERE public_id = NEW.account_public_id;
        IF resolved_account_id IS NULL THEN
            RAISE EXCEPTION 'unknown auth_event account_public_id: %', NEW.account_public_id;
        END IF;
    END IF;

    IF NEW.account_id IS NOT NULL AND NEW.account_public_id IS NOT NULL
        AND NEW.account_id <> resolved_account_id THEN
        RAISE EXCEPTION 'auth_event account identity mismatch';
    END IF;

    NEW.account_id = COALESCE(NEW.account_id, resolved_account_id);
    NEW.account_public_id = COALESCE(NEW.account_public_id, resolved_public_id);
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_auth_event_account_identity
BEFORE INSERT OR UPDATE OF account_id, account_public_id ON auth_event
FOR EACH ROW
EXECUTE FUNCTION sync_auth_event_account_identity();
