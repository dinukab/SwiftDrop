package com.swiftdrop.merchant.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.merchant.api.MerchantLookup;
import com.swiftdrop.merchant.api.MerchantSummary;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;

@Service
@Lazy
@Transactional(readOnly = true)
public class MerchantLookupService implements MerchantLookup {

    private final MerchantRepository merchants;

    public MerchantLookupService(MerchantRepository merchants) {
        this.merchants = merchants;
    }

    @Override
    public Optional<MerchantSummary> findById(UUID merchantId) {
        return merchants.findById(merchantId).map(this::toSummary);
    }

    @Override
    public Optional<MerchantSummary> findByUserId(UUID userId) {
        return merchants.findByUserId(userId).map(this::toSummary);
    }

    @Override
    public boolean isApproved(UUID merchantId) {
        return merchants.findById(merchantId)
                .map(Merchant::isApproved)
                .orElse(false);
    }

    private MerchantSummary toSummary(Merchant merchant) {
        return new MerchantSummary(
                merchant.getId(),
                merchant.getUserId(),
                merchant.getBusinessName(),
                merchant.getStatus().name()
        );
    }
}

