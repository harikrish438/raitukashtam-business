package com.raitukashtam.auth.security;

import com.raitukashtam.auth.entity.RefreshTokenLedgerEntry;
import com.raitukashtam.auth.repository.RefreshTokenLedgerRepository;
import com.raitukashtam.auth.util.TokenHasher;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Wraps a JdbcOAuth2AuthorizationService to reproduce Phase 0's
 * refresh-token reuse detection. Spring's own rotation overwrites the
 * refresh_token_value column in place, so a rotated-away token isn't
 * findable by anyone once superseded -- there's no built-in signal for
 * "this exact value used to be valid." The ledger keeps that history:
 * on save(), if a refresh token value is about to be overwritten, its
 * hash is archived; on findByToken(), a hit against an archived hash
 * means the presented token was already rotated away once, i.e. replay --
 * the whole live authorization (access + refresh token together) is
 * removed and the lookup returns null, which fails the grant with the
 * same invalid_grant a caller sees for any other bad refresh token.
 */
public class ReuseDetectingAuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;
    private final RefreshTokenLedgerRepository ledgerRepository;

    public ReuseDetectingAuthorizationService(OAuth2AuthorizationService delegate,
                                               RefreshTokenLedgerRepository ledgerRepository) {
        this.delegate = delegate;
        this.ledgerRepository = ledgerRepository;
    }

    @Override
    @Transactional
    public void save(OAuth2Authorization authorization) {
        OAuth2Authorization existing = delegate.findById(authorization.getId());
        if (existing != null) {
            OAuth2Authorization.Token<OAuth2RefreshToken> oldRt = existing.getRefreshToken();
            OAuth2Authorization.Token<OAuth2RefreshToken> newRt = authorization.getRefreshToken();
            if (oldRt != null && newRt != null
                    && !oldRt.getToken().getTokenValue().equals(newRt.getToken().getTokenValue())) {
                String hash = TokenHasher.sha256Hex(oldRt.getToken().getTokenValue());
                if (!ledgerRepository.existsByTokenHash(hash)) {
                    ledgerRepository.save(new RefreshTokenLedgerEntry(hash, authorization.getId()));
                }
            }
        }
        delegate.save(authorization);
    }

    @Override
    @Transactional
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    @Transactional
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        boolean checkLedger = tokenType == null || OAuth2TokenType.REFRESH_TOKEN.equals(tokenType);
        if (checkLedger) {
            Optional<RefreshTokenLedgerEntry> ledgerEntry =
                    ledgerRepository.findByTokenHash(TokenHasher.sha256Hex(token));
            if (ledgerEntry.isPresent()) {
                OAuth2Authorization live = delegate.findById(ledgerEntry.get().getAuthorizationId());
                if (live != null) {
                    delegate.remove(live);
                }
                return null;
            }
        }
        return delegate.findByToken(token, tokenType);
    }
}
