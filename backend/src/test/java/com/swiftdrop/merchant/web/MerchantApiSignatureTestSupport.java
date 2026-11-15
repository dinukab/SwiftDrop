package com.swiftdrop.merchant.web;

import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.merchant.application.WebhookSecretCipher;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantApiKey;
import com.swiftdrop.merchant.domain.MerchantApiKeyRepository;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.WebhookDeliveryRepository;
import com.swiftdrop.merchant.domain.WebhookEndpointRepository;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;

/**
 * Shared setup for the store-API signature tests: an approved merchant with two keys, one with a signing
 * secret and one created before signing existed.
 */
abstract class MerchantApiSignatureTestSupport {

    static final String KEY_WITH_SECRET = "sd_live_abcdef1234567890abcdef1234567890abcdef1234567890";
    static final String KEY_WITHOUT_SECRET = "sd_live_fedcba0987654321fedcba0987654321fedcba0987654321";
    static final String SIGNING_SECRET = "signing-secret-test-12345";
    static final String STATUS_PATH = "/api/v1/merchant-api/orders/REF-1";
    static final String CREATE_PATH = "/api/v1/merchant-api/orders";

    @Autowired
    MockMvc mvc;
    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    com.swiftdrop.dispatch.domain.CourierProfileRepository courierProfiles;
    @MockitoBean
    ParcelQueries parcelQueries;

    @MockitoBean
    MerchantRepository merchants;
    @MockitoBean
    MerchantApiKeyRepository apiKeys;
    @MockitoBean
    WebhookEndpointRepository webhookEndpoints;
    @MockitoBean
    WebhookDeliveryRepository webhookDeliveries;

    @MockitoBean
    UserRepository users;
    @MockitoBean
    ExternalIdentityRepository identities;
    @MockitoBean
    GoogleLoginCodeRepository googleCodes;
    @MockitoBean
    ParcelRepository parcels;
    @MockitoBean
    ParcelStatusHistoryRepository history;
    @MockitoBean
    DeliveryProofRepository proofs;
    @MockitoBean
    OutboxEventRepository outbox;
    @MockitoBean
    ZoneRepository zones;
    @MockitoBean
    PricingRuleRepository pricingRules;

    /** Stubs the approved merchant and both keys. Call from each test class's setup. */
    void stubMerchantAndKeys() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(UUID.randomUUID(), "Shop", "REG-1", "s@s.com", "123");
        ReflectionTestUtils.setField(merchant, "id", merchantId);
        merchant.approve(UUID.randomUUID());
        when(merchants.findById(merchantId)).thenReturn(Optional.of(merchant));

        MerchantApiKey signed = new MerchantApiKey(merchantId, "abcdef12", passwordEncoder.encode(KEY_WITH_SECRET),
                "Signed");
        signed.attachSigningSecret(new WebhookSecretCipher("test=9YNCoF61LK2O35ShQ1pKG+Roty+tHmUplzzjcEjNz2M=", "test")
                .encrypt(SIGNING_SECRET, merchantId));
        when(apiKeys.findByKeyPrefixAndActiveTrue("abcdef12")).thenReturn(Optional.of(signed));

        MerchantApiKey unsigned = new MerchantApiKey(merchantId, "fedcba09", passwordEncoder.encode(KEY_WITHOUT_SECRET),
                "Before signing");
        when(apiKeys.findByKeyPrefixAndActiveTrue("fedcba09")).thenReturn(Optional.of(unsigned));
    }

    static long now() {
        return Instant.now().getEpochSecond();
    }
}
