package com.swiftdrop.merchant.application;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantApiKey;
import com.swiftdrop.merchant.domain.MerchantApiKeyRepository;
import com.swiftdrop.merchant.domain.MerchantRepository;

/**
 * Manages the lifecycle and authentication of merchant API keys (MER-2).
 *
 * <p>Each key has a signing secret (Phase 5) that the store uses to sign its requests. The secret is
 * encrypted with the webhook keyring, because the server needs the plain secret to check a signature.
 */
@Service
@Lazy
public class MerchantApiKeyService {

    public static final String KEY_PREFIX_HEADER = "sd_live_";
    public static final int MAX_ACTIVE_KEYS = 10;
    private static final int RANDOM_BYTES_COUNT = 24;
    private static final int SIGNING_SECRET_BYTES = 32;

    private final MerchantApiKeyRepository apiKeyRepository;
    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final WebhookSecretCipher cipher;
    private final SecureRandom secureRandom = new SecureRandom();

    public MerchantApiKeyService(
            MerchantApiKeyRepository apiKeyRepository,
            MerchantRepository merchantRepository,
            PasswordEncoder passwordEncoder,
            WebhookSecretCipher cipher
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.merchantRepository = merchantRepository;
        this.passwordEncoder = passwordEncoder;
        this.cipher = cipher;
    }

    /**
     * Creates a key. When encryption is configured, the key also gets a signing secret, returned once.
     * Without encryption the key has no signing secret and can be used without request signatures only.
     */
    @Transactional
    public GeneratedApiKey generateKey(UUID merchantId, String label) {
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(label, "label");

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));

        if (!merchant.isApproved()) {
            throw new MerchantNotApprovedException(merchantId);
        }

        if (apiKeyRepository.countByMerchantIdAndActiveTrue(merchantId) >= MAX_ACTIVE_KEYS) {
            throw new ApiKeyLimitExceededException(merchantId, MAX_ACTIVE_KEYS);
        }

        byte[] randomBytes = new byte[RANDOM_BYTES_COUNT];
        secureRandom.nextBytes(randomBytes);
        String hexSuffix = HexFormat.of().formatHex(randomBytes);

        String rawKey = KEY_PREFIX_HEADER + hexSuffix;
        String prefix = hexSuffix.substring(0, 8);
        String hash = passwordEncoder.encode(rawKey);

        String signingSecret = null;
        MerchantApiKey apiKey = new MerchantApiKey(merchantId, prefix, hash, label.trim());
        if (cipher.isConfigured()) {
            byte[] secret = new byte[SIGNING_SECRET_BYTES];
            secureRandom.nextBytes(secret);
            signingSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
            apiKey.attachSigningSecret(cipher.encrypt(signingSecret, merchantId));
        }
        MerchantApiKey saved = apiKeyRepository.save(apiKey);

        return new GeneratedApiKey(saved.getId(), saved.getLabel(), saved.getKeyPrefix(), rawKey, signingSecret,
                saved.getCreatedAt());
    }

    /** A key that passed authentication, with its signing secret when it has one. */
    public record Authentication(UUID merchantId, String signingSecret) {

        /** Never prints the secret. */
        @Override
        public String toString() {
            return "Authentication[merchantId=" + merchantId + ", signed=" + (signingSecret != null) + "]";
        }
    }

    /**
     * Checks a raw key and returns the merchant and the key's signing secret (null for an old key).
     * Empty if the key is unknown, revoked, wrong, or its merchant is no longer approved.
     */
    @Transactional
    public Optional<Authentication> authenticate(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(KEY_PREFIX_HEADER)
                || rawKey.length() < KEY_PREFIX_HEADER.length() + 8) {
            return Optional.empty();
        }

        String hexBody = rawKey.substring(KEY_PREFIX_HEADER.length());
        String prefix = hexBody.substring(0, 8);

        Optional<MerchantApiKey> candidate = apiKeyRepository.findByKeyPrefixAndActiveTrue(prefix);
        if (candidate.isEmpty()) {
            return Optional.empty();
        }

        MerchantApiKey apiKey = candidate.get();
        if (!passwordEncoder.matches(rawKey, apiKey.getKeyHash())) {
            return Optional.empty();
        }

        // Verify the merchant account is still active and approved
        boolean approved = merchantRepository.findById(apiKey.getMerchantId())
                .map(Merchant::isApproved)
                .orElse(false);

        if (!approved) {
            return Optional.empty();
        }

        apiKey.recordUsed(Instant.now());
        apiKeyRepository.save(apiKey);

        String signingSecret = apiKey.getSigningSecretEncrypted() == null
                ? null
                : cipher.decrypt(apiKey.getSigningSecretEncrypted(), apiKey.getMerchantId());
        return Optional.of(new Authentication(apiKey.getMerchantId(), signingSecret));
    }

    @Transactional
    public Optional<UUID> verifyKey(String rawKey) {
        return authenticate(rawKey).map(Authentication::merchantId);
    }

    @Transactional
    public void revokeKey(UUID keyId, UUID merchantId) {
        Objects.requireNonNull(keyId, "keyId");
        Objects.requireNonNull(merchantId, "merchantId");

        MerchantApiKey key = apiKeyRepository.findByIdAndMerchantId(keyId, merchantId)
                .orElseThrow(() -> new MerchantNotFoundException("API key not found: " + keyId));

        key.revoke();
        apiKeyRepository.save(key);
    }

    @Transactional(readOnly = true)
    public List<MerchantApiKey> listActiveKeys(UUID merchantId) {
        Objects.requireNonNull(merchantId, "merchantId");
        return apiKeyRepository.findByMerchantIdAndActiveTrue(merchantId);
    }
}
