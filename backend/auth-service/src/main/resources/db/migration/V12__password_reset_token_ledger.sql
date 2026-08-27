-- Password reset tokens are stateless JWTs with no built-in replay
-- protection -- a leaked reset link was replayable within its TTL. This
-- table tracks consumed tokens by jti so a second use of the same token
-- is rejected. TTL-bounded by nature (the JWT itself expires), but kept
-- indefinitely here rather than pruned -- low volume, not worth a cleanup
-- job for this pass.

CREATE TABLE used_password_reset_token (
    jti character varying(255) NOT NULL,
    used_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT used_password_reset_token_pkey PRIMARY KEY (jti)
);
