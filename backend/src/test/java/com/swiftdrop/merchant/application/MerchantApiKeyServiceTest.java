package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantApiKey;
import com.swiftdrop.merchant.domain.MerchantApiKeyRepository;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantStatus;

@ExtendWith(MockitoExtension.class)
class MerchantApiKeyServiceTest {

    @Mock
    private MerchantApiKeyRepository apiKeyRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private MerchantApiKeyService service;

    @BeforeEach
    void setUp() {
        service = new MerchantApiKeyService(apiKeyRepository, merchantRepository, passwordEncoder);
    }

    @Test
    void generateKeySucceedsForApprovedMerchant() {
        UUID merchantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Merchant merchant = new Merchant(UUID.randomUUID(), "Acme", "REG-1", "a@a.com", "123");
        merchant.approve(adminId);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(apiKeyRepository.countByMerchantIdAndActiveTrue(merchantId)).thenReturn(2);
        when(passwordEncoder.encode(any())).thenReturn("hashed_key");
        when(apiKeyRepository.save(any(MerchantApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        GeneratedApiKey result = service.generateKey(merchantId, "E-commerce store");

        assertThat(result.rawKey()).startsWith("sd_live_");
        assertThat(result.label()).isEqualTo("E-commerce store");
        assertThat(result.keyPrefix()).hasSize(8);

        ArgumentCaptor<MerchantApiKey> captor = ArgumentCaptor.forClass(MerchantApiKey.class);
        verify(apiKeyRepository).save(captor.capture());
        assertThat(captor.getValue().getMerchantId()).isEqualTo(merchantId);
        assertThat(captor.getValue().getKeyHash()).isEqualTo("hashed_key");
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void generateKeyFailsWhenMerchantNotApproved() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(UUID.randomUUID(), "Acme", "REG-1", "a@a.com", "123");
        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.PENDING_APPROVAL);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));

        assertThatThrownBy(() -> service.generateKey(merchantId, "Store"))
                .isInstanceOf(MerchantNotApprovedException.class);
    }

    @Test
    void generateKeyFailsWhenLimitExceeded() {
        UUID merchantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Merchant merchant = new Merchant(UUID.randomUUID(), "Acme", "REG-1", "a@a.com", "123");
        merchant.approve(adminId);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(apiKeyRepository.countByMerchantIdAndActiveTrue(merchantId)).thenReturn(10);

        assertThatThrownBy(() -> service.generateKey(merchantId, "Store"))
                .isInstanceOf(ApiKeyLimitExceededException.class);
    }

    @Test
    void verifyKeySucceedsWhenValidAndMerchantApproved() {
        String rawKey = "sd_live_12345678abcdef0123456789abcdef0123456789abcdef01";
        String prefix = "12345678";
        UUID merchantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        Merchant merchant = new Merchant(UUID.randomUUID(), "Acme", "REG-1", "a@a.com", "123");
        merchant.approve(adminId);

        MerchantApiKey apiKey = new MerchantApiKey(merchantId, prefix, "stored_hash", "Store");

        when(apiKeyRepository.findByKeyPrefixAndActiveTrue(prefix)).thenReturn(Optional.of(apiKey));
        when(passwordEncoder.matches(rawKey, "stored_hash")).thenReturn(true);
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(apiKeyRepository.save(any(MerchantApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<UUID> result = service.verifyKey(rawKey);

        assertThat(result).isPresent().contains(merchantId);
        assertThat(apiKey.getLastUsedAt()).isNotNull();
        verify(apiKeyRepository).save(apiKey);
    }

    @Test
    void verifyKeyReturnsEmptyWhenPasswordMismatch() {
        String rawKey = "sd_live_12345678abcdef0123456789abcdef0123456789abcdef01";
        String prefix = "12345678";
        UUID merchantId = UUID.randomUUID();

        MerchantApiKey apiKey = new MerchantApiKey(merchantId, prefix, "stored_hash", "Store");

        when(apiKeyRepository.findByKeyPrefixAndActiveTrue(prefix)).thenReturn(Optional.of(apiKey));
        when(passwordEncoder.matches(rawKey, "stored_hash")).thenReturn(false);

        Optional<UUID> result = service.verifyKey(rawKey);

        assertThat(result).isEmpty();
    }

    @Test
    void revokeKeyDeactivatesKey() {
        UUID keyId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        MerchantApiKey apiKey = new MerchantApiKey(merchantId, "12345678", "hash", "Store");

        when(apiKeyRepository.findByIdAndMerchantId(keyId, merchantId)).thenReturn(Optional.of(apiKey));

        service.revokeKey(keyId, merchantId);

        assertThat(apiKey.isActive()).isFalse();
        verify(apiKeyRepository).save(apiKey);
    }
}

