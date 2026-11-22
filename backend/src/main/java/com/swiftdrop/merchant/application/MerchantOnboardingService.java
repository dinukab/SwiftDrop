package com.swiftdrop.merchant.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.swiftdrop.identity.api.EmailTakenException;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantPickupAddress;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantStatus;
import com.swiftdrop.shared.web.PageLimit;

/**
 * Merchant registration and admin review. Uses {@link UserDirectory} only (ADR 0003: merchant → identity).
 * Registration creates a MERCHANT user and a {@link MerchantStatus#PENDING} merchant atomically (D-22).
 */
@Service
public class MerchantOnboardingService {

    /** The foreign key that checks the pickup zone exists (merchant may not ask pricing, ADR 0003). */
    static final String PICKUP_ZONE_CONSTRAINT = "fk_merchants_pickup_zone";

    private final MerchantRepository merchants;
    private final UserDirectory userDirectory;

    MerchantOnboardingService(MerchantRepository merchants, UserDirectory userDirectory) {
        this.merchants = merchants;
        this.userDirectory = userDirectory;
    }

    /**
     * Registers a merchant account with its pickup (warehouse) address. The identity user receives the MERCHANT
     * role immediately; merchant capabilities remain blocked until an administrator approves the profile.
     *
     * @throws InvalidPickupZoneException if the address names a zone that does not exist (nothing is created)
     */
    @Transactional
    public Merchant register(String fullName, String email, String rawPassword, String businessName,
            String registrationNumber, String contactPhone, MerchantPickupAddress pickupAddress) {
        Objects.requireNonNull(pickupAddress, "pickupAddress");
        // Checked before the login user is created so a duplicate leaves no account behind; the unique
        // constraint below still catches a concurrent duplicate.
        if (merchants.existsByRegistrationNumber(registrationNumber.trim())) {
            throw new RegistrationNumberAlreadyRegisteredException(registrationNumber.trim());
        }
        UUID userId;
        try {
            userId = userDirectory.createMerchantUser(fullName, email, rawPassword);
        } catch (EmailTakenException e) {
            throw new MerchantConflictException(e.getMessage());
        }

        Merchant merchant = new Merchant(userId, businessName, registrationNumber, email, contactPhone);
        merchant.changePickupAddress(pickupAddress);
        try {
            return merchants.saveAndFlush(merchant);
        } catch (DataIntegrityViolationException e) {
            // Thrown inside the transaction, so the login user created above is rolled back too
            if (violates(e, PICKUP_ZONE_CONSTRAINT)) {
                throw new InvalidPickupZoneException();
            }
            throw new MerchantConflictException("A merchant profile already exists for this account");
        }
    }

    /**
     * Sets or replaces the authenticated merchant's pickup address, in any status (a pending merchant may correct it
     * before review).
     *
     * @throws InvalidPickupZoneException if the address names a zone that does not exist
     */
    @Transactional
    public Merchant changePickupAddress(UUID userId, MerchantPickupAddress pickupAddress) {
        Merchant merchant = requireOwnMerchant(userId);
        merchant.changePickupAddress(pickupAddress);
        try {
            return merchants.saveAndFlush(merchant);
        } catch (DataIntegrityViolationException e) {
            if (violates(e, PICKUP_ZONE_CONSTRAINT)) {
                throw new InvalidPickupZoneException();
            }
            throw e;
        }
    }

    private static boolean violates(DataIntegrityViolationException e, String constraint) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(constraint);
    }

    /** Returns the merchant profile for the authenticated merchant user. */
    @Transactional(readOnly = true)
    public Merchant requireOwnMerchant(UUID userId) {
        return merchants.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant profile not found"));
    }

    /**
     * Ensures the merchant for {@code userId} is {@link MerchantStatus#APPROVED}. Call before any
     * merchant capability endpoint (API keys, orders, webhooks).
     */
    @Transactional(readOnly = true)
    public Merchant requireApproved(UUID userId) {
        Merchant merchant = requireOwnMerchant(userId);
        if (!merchant.isApproved()) {
            throw new MerchantNotApprovedException(
                    "Merchant account is " + merchant.getStatus().name()
                            + "; an administrator must approve the application before this feature is available");
        }
        return merchant;
    }

    @Transactional(readOnly = true)
    public Page<Merchant> list(int page, int size, MerchantStatus statusFilter) {
        int clampedSize = PageLimit.clampSize(size);
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (statusFilter != null) {
            return merchants.findByStatus(statusFilter, pageable);
        }
        return merchants.findAll(pageable);
    }

    @Transactional
    public Merchant approve(UUID merchantId, UUID adminId) {
        Merchant merchant = requirePending(merchantId);
        merchant.approve(adminId);
        return merchants.saveAndFlush(merchant);
    }

    @Transactional
    public Merchant reject(UUID merchantId, UUID adminId, String reason) {
        Merchant merchant = requirePending(merchantId);
        merchant.reject(adminId, reason);
        return merchants.saveAndFlush(merchant);
    }

    /** Suspends an APPROVED merchant; its API keys and portal features stop working immediately. */
    @Transactional
    public Merchant suspend(UUID merchantId, UUID adminId) {
        Merchant merchant = requireStatus(merchantId, MerchantStatus.APPROVED);
        merchant.suspend(adminId);
        return merchants.saveAndFlush(merchant);
    }

    private Merchant requirePending(UUID merchantId) {
        return requireStatus(merchantId, MerchantStatus.PENDING_APPROVAL);
    }

    private Merchant requireStatus(UUID merchantId, MerchantStatus expected) {
        Merchant merchant = merchants.findById(merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found"));
        if (merchant.getStatus() != expected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Merchant is already " + merchant.getStatus().name().toLowerCase());
        }
        return merchant;
    }
}
