package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.WebhookEndpoint;
import com.swiftdrop.merchant.domain.WebhookEndpointRepository;

@ExtendWith(MockitoExtension.class)
class WebhookEndpointServiceTest {

    @Mock
    private WebhookEndpointRepository webhookRepository;

    @Mock
    private MerchantRepository merchantRepository;

    private WebhookEndpointService service;

    @BeforeEach
    void setUp() {
        service = new WebhookEndpointService(webhookRepository, merchantRepository);
    }

    @Test
    void registerEndpointSucceedsForApprovedMerchant() {
        UUID merchantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Merchant merchant = new Merchant(UUID.randomUUID(), "Acme", "REG-1", "a@a.com", "123");
        merchant.approve(adminId);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(webhookRepository.countByMerchantIdAndActiveTrue(merchantId)).thenReturn(1);
        when(webhookRepository.save(any(WebhookEndpoint.class))).thenAnswer(inv -> inv.getArgument(0));

        WebhookEndpoint result = service.registerEndpoint(
                merchantId, "https://api.acme.com/webhooks", "super-secret-key-12345"
        );

        assertThat(result.getMerchantId()).isEqualTo(merchantId);
        assertThat(result.getUrl()).isEqualTo("https://api.acme.com/webhooks");
        assertThat(result.isActive()).isTrue();
        assertThat(result.getSecretEncrypted()).isNotEmpty();
        verify(webhookRepository).save(any(WebhookEndpoint.class));
    }

    @Test
    void registerEndpointFailsForUnapprovedMerchant() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(UUID.randomUUID(), "Acme", "REG-1", "a@a.com", "123");

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));

        assertThatThrownBy(() -> service.registerEndpoint(merchantId, "https://api.acme.com/webhooks", "secret-12345678901"))
                .isInstanceOf(MerchantNotApprovedException.class);
    }

    @Test
    void registerEndpointFailsForInvalidUrl() {
        UUID merchantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Merchant merchant = new Merchant(UUID.randomUUID(), "Acme", "REG-1", "a@a.com", "123");
        merchant.approve(adminId);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));

        assertThatThrownBy(() -> service.registerEndpoint(merchantId, "ftp://bad-url", "secret-12345678901"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deactivateEndpointSetsActiveFalse() {
        UUID endpointId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        WebhookEndpoint endpoint = new WebhookEndpoint(merchantId, "https://acme.com/webhook", "encrypted");

        when(webhookRepository.findByIdAndMerchantId(endpointId, merchantId)).thenReturn(Optional.of(endpoint));

        service.deactivateEndpoint(endpointId, merchantId);

        assertThat(endpoint.isActive()).isFalse();
        verify(webhookRepository).save(endpoint);
    }
}

