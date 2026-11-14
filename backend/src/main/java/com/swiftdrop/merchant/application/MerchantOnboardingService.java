package com.swiftdrop.merchant.application;

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
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantStatus;
import com.swiftdrop.shared.web.PageLimit;

/**
 * Merchant registration and admin review. Uses {@link UserDirectory} only (ADR 0003: merchant → identity).
 * Registration creates a MERCHANT user and a {@link MerchantStatus#PENDING} merchant atomically (D-22).
 */
@Service
public class MerchantOnboardingService {

    private final MerchantRepository merchants;
    private final UserDirectory userDirectory;

    MerchantOnboardingService(MerchantRepository merchants, UserDirectory userDirectory) {
        this.merchants = merchants;
        this.userDirectory = userDirectory;
    }

    /**
     * Registers a merchant account. The identity user receives the MERCHANT role immediately; merchant
     * capabilities remain blocked until an administrator approves the profile.
     */
    @Transactional
    public Merchant register(String fullName, String email, String rawPassword, String businessName,
            String registrationNumber, String contactPhone) {
        UUID userId;
        try {
            userId = userDirectory.createMerchantUser(fullName, email, rawPassword);
        } catch (EmailTakenException e) {
            throw new MerchantConflictException(e.getMessage());
        }

        Merchant merchant = new Merchant(userId, businessName, registrationNumber, email, contactPhone);
        try {
            return merchants.saveAndFlush(merchant);
        } catch (DataIntegrityViolationException e) {
            throw new MerchantConflictException("A merchant profile already exists for this account");
        }
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

    private Merchant requirePending(UUID merchantId) {
        Merchant merchant = merchants.findById(merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found"));
        if (merchant.getStatus() != MerchantStatus.PENDING_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Merchant is already " + merchant.getStatus().name().toLowerCase());
        }
        return merchant;
    }
}
