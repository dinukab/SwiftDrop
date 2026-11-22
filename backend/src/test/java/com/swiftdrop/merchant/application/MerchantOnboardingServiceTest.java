package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.swiftdrop.identity.api.EmailTakenException;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantPickupAddress;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantStatus;

@ExtendWith(MockitoExtension.class)
class MerchantOnboardingServiceTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID MERCHANT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID ADMIN_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID ZONE_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final MerchantPickupAddress ADDRESS = new MerchantPickupAddress(
            "Ada Warehouse", "+94912234567", "12 Warehouse Rd", null, "Galle", "80000", ZONE_ID);

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

        Merchant created = service.register("Ada", "ada@shop.example", "password12", "Ada's Shop", "REG-ADA-1", "0770000000", ADDRESS);

        assertThat(created.getId()).isEqualTo(MERCHANT_ID);
        assertThat(created.getUserId()).isEqualTo(USER_ID);
        assertThat(created.getBusinessName()).isEqualTo("Ada's Shop");
        assertThat(created.getRegistrationNumber()).isEqualTo("REG-ADA-1");
        assertThat(created.getContactEmail()).isEqualTo("ada@shop.example");
        assertThat(created.getStatus()).isEqualTo(MerchantStatus.PENDING_APPROVAL);

        ArgumentCaptor<Merchant> captor = ArgumentCaptor.forClass(Merchant.class);
        verify(merchants).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getContactPhone()).isEqualTo("0770000000");
        assertThat(captor.getValue().getPickupAddress()).isSameAs(ADDRESS);
    }

    @Test
    void registerMapsUnknownPickupZoneToInvalidZone() {
        when(userDirectory.createMerchantUser(any(), any(), any())).thenReturn(USER_ID);
        when(merchants.saveAndFlush(any(Merchant.class))).thenThrow(new DataIntegrityViolationException(
                "insert into merchants violates foreign key constraint \"fk_merchants_pickup_zone\""));

        // The exception escapes the transactional method, so the login user is rolled back with it
        assertThatThrownBy(() -> service.register("Ada", "ada@shop.example", "password12", "Shop", "REG-ADA-3",
                "0770000000", ADDRESS))
                .isInstanceOf(InvalidPickupZoneException.class);
    }

    @Test
    void registerStillMapsOtherIntegrityViolationsToConflict() {
        when(userDirectory.createMerchantUser(any(), any(), any())).thenReturn(USER_ID);
        when(merchants.saveAndFlush(any(Merchant.class))).thenThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"merchants_user_id_key\""));

        assertThatThrownBy(() -> service.register("Ada", "ada@shop.example", "password12", "Shop", "REG-ADA-4",
                "0770000000", ADDRESS))
                .isInstanceOf(MerchantConflictException.class);
    }

    @Test
    void pendingMerchantCanChangeItsPickupAddress() {
        Merchant merchant = new Merchant(USER_ID, "Ada's Shop", "REG-ADA-1", "ada@shop.example", "0770000000");
        when(merchants.findByUserId(USER_ID)).thenReturn(Optional.of(merchant));
        when(merchants.saveAndFlush(merchant)).thenReturn(merchant);

        Merchant changed = service.changePickupAddress(USER_ID, ADDRESS);

        assertThat(changed.getStatus()).isEqualTo(MerchantStatus.PENDING_APPROVAL);
        assertThat(changed.getPickupAddress()).isSameAs(ADDRESS);
    }

    @Test
    void changePickupAddressMapsUnknownZone() {
        Merchant merchant = new Merchant(USER_ID, "Ada's Shop", "REG-ADA-1", "ada@shop.example", "0770000000");
        when(merchants.findByUserId(USER_ID)).thenReturn(Optional.of(merchant));
        when(merchants.saveAndFlush(merchant)).thenThrow(new DataIntegrityViolationException(
                "violates foreign key constraint \"fk_merchants_pickup_zone\""));

        assertThatThrownBy(() -> service.changePickupAddress(USER_ID, ADDRESS))
                .isInstanceOf(InvalidPickupZoneException.class);
    }

    @Test
    void changePickupAddressNeedsAMerchantProfile() {
        when(merchants.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePickupAddress(USER_ID, ADDRESS))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Merchant profile not found");
    }

    @Test
    void registerMapsDuplicateEmailToConflict() {
        when(userDirectory.createMerchantUser(any(), any(), any())).thenThrow(new EmailTakenException());

        assertThatThrownBy(() -> service.register("Ada", "ada@shop.example", "password12", "Shop", "REG-ADA-2", "0770000000", ADDRESS))
                .isInstanceOf(MerchantConflictException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void registerRejectsDuplicateRegistrationNumberBeforeCreatingUser() {
        when(merchants.existsByRegistrationNumber("REG-ADA-1")).thenReturn(true);

        assertThatThrownBy(() -> service.register("Ada", "ada@shop.example", "password12", "Shop", " REG-ADA-1 ", "0770000000", ADDRESS))
                .isInstanceOf(RegistrationNumberAlreadyRegisteredException.class)
                .hasMessageContaining("REG-ADA-1");
        verifyNoInteractions(userDirectory);
    }

    @Test
    void suspendOnlyApprovedMerchants() {
        Merchant merchant = pendingMerchant();
        merchant.approve(ADMIN_ID);
        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant));
        when(merchants.saveAndFlush(merchant)).thenReturn(merchant);

        Merchant suspended = service.suspend(MERCHANT_ID, ADMIN_ID);

        assertThat(suspended.getStatus()).isEqualTo(MerchantStatus.SUSPENDED);
        assertThat(suspended.isApproved()).isFalse();
    }

    @Test
    void suspendRejectsPending() {
        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(pendingMerchant()));

        assertThatThrownBy(() -> service.suspend(MERCHANT_ID, ADMIN_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void suspendUnknownMerchantIsNotFound() {
        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suspend(MERCHANT_ID, ADMIN_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
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
        Merchant merchant = new Merchant(USER_ID, "Ada's Shop", "REG-ADA-1", "ada@shop.example", "0770000000");
        ReflectionTestUtils.setField(merchant, "id", MERCHANT_ID);
        return merchant;
    }
}
