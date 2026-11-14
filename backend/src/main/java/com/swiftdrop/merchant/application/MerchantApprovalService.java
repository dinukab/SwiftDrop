package com.swiftdrop.merchant.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantStatus;
import com.swiftdrop.shared.web.PageLimit;

/**
 * Handles administrator review, approval, and lifecycle actions for merchants (MER-1, ADM-3).
 */
@Service
@Lazy
public class MerchantApprovalService {

    private final MerchantRepository merchants;

    public MerchantApprovalService(MerchantRepository merchants) {
        this.merchants = merchants;
    }

    @Transactional
    public Merchant approve(UUID merchantId, UUID adminUserId) {
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(adminUserId, "adminUserId");

        Merchant merchant = merchants.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));
        merchant.approve(adminUserId);
        return merchants.save(merchant);
    }

    @Transactional
    public Merchant reject(UUID merchantId, UUID adminUserId, String reason) {
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(adminUserId, "adminUserId");

        Merchant merchant = merchants.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));
        merchant.reject(adminUserId, reason);
        return merchants.save(merchant);
    }

    @Transactional
    public Merchant suspend(UUID merchantId, UUID adminUserId) {
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(adminUserId, "adminUserId");

        Merchant merchant = merchants.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));
        merchant.suspend(adminUserId);
        return merchants.save(merchant);
    }

    @Transactional(readOnly = true)
    public Page<Merchant> findMerchants(int page, int size, MerchantStatus statusFilter) {
        int pageNumber = PageLimit.clampPage(page);
        int pageSize = PageLimit.clampSize(size);
        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        if (statusFilter != null) {
            return merchants.findByStatus(statusFilter, pageable);
        }
        return merchants.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Merchant getMerchant(UUID merchantId) {
        return merchants.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));
    }

    @Transactional(readOnly = true)
    public Merchant getMerchantByUserId(UUID userId) {
        return merchants.findByUserId(userId)
                .orElseThrow(() -> new MerchantNotFoundException("No merchant profile for user: " + userId));
    }
}

