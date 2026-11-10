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
import com.swiftdrop.merchant.domain.MerchantStatus;

@ExtendWith(MockitoExtension.class)
class MerchantApprovalServiceTest {

    @Mock
    private MerchantRepository merchants;

    private MerchantApprovalService service;

    @BeforeEach
    void setUp() {
        service = new MerchantApprovalService(merchants);
    }

    @Test
    void approveSetsMerchantToApproved() {
        UUID merchantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Merchant merchant = new Merchant(UUID.randomUUID(), "Acme", "REG-1", "a@a.com", "123");

        when(merchants.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(merchants.save(any(Merchant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Merchant result = service.approve(merchantId, adminId);

        assertThat(result.getStatus()).isEqualTo(MerchantStatus.APPROVED);
        assertThat(result.getReviewedBy()).isEqualTo(adminId);
        assertThat(result.getReviewedAt()).isNotNull();
        verify(merchants).save(merchant);
    }

    @Test
    void rejectSetsMerchantToRejectedWithReason() {
        UUID merchantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Merchant merchant = new Merchant(UUID.randomUUID(), "Acme", "REG-1", "a@a.com", "123");

        when(merchants.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(merchants.save(any(Merchant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Merchant result = service.reject(merchantId, adminId, "Invalid documents");

        assertThat(result.getStatus()).isEqualTo(MerchantStatus.REJECTED);
        assertThat(result.getReviewedBy()).isEqualTo(adminId);
        assertThat(result.getRejectionReason()).isEqualTo("Invalid documents");
        verify(merchants).save(merchant);
    }

    @Test
    void suspendSetsMerchantToSuspended() {
        UUID merchantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Merchant merchant = new Merchant(UUID.randomUUID(), "Acme", "REG-1", "a@a.com", "123");

        when(merchants.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(merchants.save(any(Merchant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Merchant result = service.suspend(merchantId, adminId);

        assertThat(result.getStatus()).isEqualTo(MerchantStatus.SUSPENDED);
        verify(merchants).save(merchant);
    }

    @Test
    void throwsNotFoundWhenMerchantDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        when(merchants.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(missingId, adminId))
                .isInstanceOf(MerchantNotFoundException.class);
    }
}

