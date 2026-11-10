package com.swiftdrop.merchant.application;

import java.security.SecureRandom;
import java.time.Instant;
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
 */
@Service
@Lazy
public class MerchantApiKeyService {

    public static final String KEY_PREFIX_HEADER = "sd_live_";
    public static final int MAX_ACTIVE_KEYS = 10;
    private static final int RANDOM_BYTES_COUNT = 24;

    private final MerchantApiKeyRepository apiKeyRepository;
    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public MerchantApiKeyService(
            MerchantApiKeyRepository apiKeyRepository,
            MerchantRepository merchantRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.merchantRepository = merchantRepository;
        this.passwordEncoder = passwordEncoder;
    }

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

        MerchantApiKey apiKey = new MerchantApiKey(merchantId, prefix, hash, label.trim());
        MerchantApiKey saved = apiKeyRepository.save(apiKey);

        return new GeneratedApiKey(saved.getId(), saved.getLabel(), saved.getKeyPrefix(), rawKey, saved.getCreatedAt());
    }

    @Transactional
    public Optional<UUID> verifyKey(String rawKey) {
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

        return Optional.of(apiKey.getMerchantId());
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

