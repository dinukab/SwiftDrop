package com.swiftdrop.merchant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class MerchantTest {

    @Test
    void newMerchantHasPendingStatusAndTrimmedFields() {
        UUID userId = UUID.randomUUID();
        Merchant merchant = new Merchant(
                userId,
                "  Acme Corp  ",
                "  REG-12345  ",
                "  contact@acme.com  ",
                "  +94771234567  "
        );

        assertThat(merchant.getUserId()).isEqualTo(userId);
        assertThat(merchant.getBusinessName()).isEqualTo("Acme Corp");
        assertThat(merchant.getRegistrationNumber()).isEqualTo("REG-12345");
        assertThat(merchant.getContactEmail()).isEqualTo("contact@acme.com");
        assertThat(merchant.getContactPhone()).isEqualTo("+94771234567");
        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.PENDING_APPROVAL);
        assertThat(merchant.isApproved()).isFalse();
        assertThat(merchant.getReviewedBy()).isNull();
        assertThat(merchant.getReviewedAt()).isNull();
        assertThat(merchant.getCreatedAt()).isNotNull();
        assertThat(merchant.getUpdatedAt()).isNotNull();
    }

    @Test
    void approveSetsApprovedStatusAndReviewer() {
        Merchant merchant = new Merchant(
                UUID.randomUUID(), "Acme Corp", "REG-123", "a@a.com", "123"
        );
        UUID adminId = UUID.randomUUID();

        merchant.approve(adminId);

        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.APPROVED);
        assertThat(merchant.isApproved()).isTrue();
        assertThat(merchant.getReviewedBy()).isEqualTo(adminId);
        assertThat(merchant.getReviewedAt()).isNotNull();
        assertThat(merchant.getRejectionReason()).isNull();
    }

    @Test
    void rejectSetsRejectedStatusAndReason() {
        Merchant merchant = new Merchant(
                UUID.randomUUID(), "Acme Corp", "REG-123", "a@a.com", "123"
        );
        UUID adminId = UUID.randomUUID();

        merchant.reject(adminId, "Incomplete documentation");

        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.REJECTED);
        assertThat(merchant.isApproved()).isFalse();
        assertThat(merchant.getReviewedBy()).isEqualTo(adminId);
        assertThat(merchant.getReviewedAt()).isNotNull();
        assertThat(merchant.getRejectionReason()).isEqualTo("Incomplete documentation");
    }

    @Test
    void suspendSetsSuspendedStatus() {
        Merchant merchant = new Merchant(
                UUID.randomUUID(), "Acme Corp", "REG-123", "a@a.com", "123"
        );
        UUID adminId = UUID.randomUUID();

        merchant.approve(adminId);
        assertThat(merchant.isApproved()).isTrue();

        merchant.suspend(adminId);
        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.SUSPENDED);
        assertThat(merchant.isApproved()).isFalse();
    }

    @Test
    void requiredFieldsCannotBeNull() {
        assertThatThrownBy(() -> new Merchant(null, "Acme", "REG", "a@a.com", "123"))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new Merchant(UUID.randomUUID(), null, "REG", "a@a.com", "123"))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new Merchant(UUID.randomUUID(), "Acme", null, "a@a.com", "123"))
                .isInstanceOf(NullPointerException.class);
    }
}

