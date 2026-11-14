package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.swiftdrop.identity.api.EmailTakenException;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantStatus;

@ExtendWith(MockitoExtension.class)
class MerchantOnboardingServiceTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID MERCHANT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID ADMIN_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock
    private MerchantRepository merchants;

    @Mock
    private UserDirectory userDirectory;

    private MerchantOnboardingService service;

    @BeforeEach
    void setUp() {
        service = new MerchantOnboardingService(merchants, userDirectory);
    }

    @Test
    void registerCreatesMerchantUserAndPendingProfile() {
        when(userDirectory.createMerchantUser("Ada", "ada@shop.example", "password12")).thenReturn(USER_ID);
        when(merchants.saveAndFlush(any(Merchant.class))).thenAnswer(call -> {
            Merchant merchant = call.getArgument(0);
            ReflectionTestUtils.setField(merchant, "id", MERCHANT_ID);
            return merchant;
        });

        Merchant created = service.register("Ada", "ada@shop.example", "password12", "Ada's Shop", "0770000000");

        assertThat(created.getId()).isEqualTo(MERCHANT_ID);
        assertThat(created.getUserId()).isEqualTo(USER_ID);
        assertThat(created.getBusinessName()).isEqualTo("Ada's Shop");
        assertThat(created.getStatus()).isEqualTo(MerchantStatus.PENDING);

        ArgumentCaptor<Merchant> captor = ArgumentCaptor.forClass(Merchant.class);
        verify(merchants).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getContactPhone()).isEqualTo("0770000000");
    }

    @Test
    void registerMapsDuplicateEmailToConflict() {
        when(userDirectory.createMerchantUser(any(), any(), any())).thenThrow(new EmailTakenException());

        assertThatThrownBy(() -> service.register("Ada", "ada@shop.example", "password12", "Shop", null))
                .isInstanceOf(MerchantConflictException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void approveOnlyPendingMerchants() {
        Merchant pending = pendingMerchant();
        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(pending));
        when(merchants.saveAndFlush(pending)).thenReturn(pending);

        Merchant approved = service.approve(MERCHANT_ID, ADMIN_ID);

        assertThat(approved.getStatus()).isEqualTo(MerchantStatus.APPROVED);
        assertThat(approved.getReviewedBy()).isEqualTo(ADMIN_ID);
        assertThat(approved.getReviewedAt()).isNotNull();
    }

    @Test
    void approveRejectsNonPending() {
        Merchant approved = pendingMerchant();
        approved.approve(ADMIN_ID);
        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.approve(MERCHANT_ID, ADMIN_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void rejectOnlyPendingMerchants() {
        Merchant pending = pendingMerchant();
        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(pending));
        when(merchants.saveAndFlush(pending)).thenReturn(pending);

        Merchant rejected = service.reject(MERCHANT_ID, ADMIN_ID, "Incomplete documentation");

        assertThat(rejected.getStatus()).isEqualTo(MerchantStatus.REJECTED);
        assertThat(rejected.getReviewedBy()).isEqualTo(ADMIN_ID);
        assertThat(rejected.getRejectionReason()).isEqualTo("Incomplete documentation");
        assertThat(rejected.getReviewedAt()).isNotNull();
    }

    @Test
    void rejectRejectsNonPending() {
        Merchant approved = pendingMerchant();
        approved.approve(ADMIN_ID);
        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.reject(MERCHANT_ID, ADMIN_ID, "Too late"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void requireApprovedBlocksPending() {
        when(merchants.findByUserId(USER_ID)).thenReturn(Optional.of(pendingMerchant()));

        assertThatThrownBy(() -> service.requireApproved(USER_ID))
                .isInstanceOf(MerchantNotApprovedException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void requireApprovedBlocksRejected() {
        Merchant rejected = pendingMerchant();
        rejected.reject(ADMIN_ID, "Incomplete documentation");
        when(merchants.findByUserId(USER_ID)).thenReturn(Optional.of(rejected));

        assertThatThrownBy(() -> service.requireApproved(USER_ID))
                .isInstanceOf(MerchantNotApprovedException.class)
                .hasMessageContaining("REJECTED");
    }

    @Test
    void requireApprovedAllowsApproved() {
        Merchant merchant = pendingMerchant();
        merchant.approve(ADMIN_ID);
        when(merchants.findByUserId(USER_ID)).thenReturn(Optional.of(merchant));

        assertThat(service.requireApproved(USER_ID).isApproved()).isTrue();
        verify(merchants).findByUserId(eq(USER_ID));
    }

    private static Merchant pendingMerchant() {
        Merchant merchant = new Merchant(USER_ID, "Ada's Shop", null);
        ReflectionTestUtils.setField(merchant, "id", MERCHANT_ID);
        return merchant;
    }
}
