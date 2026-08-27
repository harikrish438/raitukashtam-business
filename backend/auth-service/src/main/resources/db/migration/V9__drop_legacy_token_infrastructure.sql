-- Phase 4b full cutover: the hand-rolled token stack from Phases 0-3
-- (AuthController/TokenService/JwtAuthenticationFilter) is retired in
-- favor of Spring Authorization Server (Phase 4a/4b). Their backing
-- tables are superseded by oauth2_authorization (V7) and
-- refresh_token_ledger (V8):
--   - refresh_token -> oauth2_authorization + refresh_token_ledger
--   - revoked_tokens -> ReuseDetectingAuthorizationService (session-level
--     revocation) + local JWT validation's natural expiry
--   - token_blacklist -> already orphaned before this phase (its own
--     repository actually operated on the RevokedToken entity, not this
--     table -- see project memory); dropped alongside its sibling rather
--     than left behind half-cleaned.

DROP TABLE refresh_token;
DROP TABLE revoked_tokens;
DROP TABLE token_blacklist;
