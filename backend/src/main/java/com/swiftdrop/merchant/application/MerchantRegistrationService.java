package com.swiftdrop.merchant.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;

/**
 * Handles merchant registration requests (MER-1).
 */
@Service
@Lazy
public class MerchantRegistrationService {

    private final MerchantRepository merchants;
    private final UserDirectory userDirectory;

    public MerchantRegistrationService(MerchantRepository merchants, UserDirectory userDirectory) {
        this.merchants = merchants;
        this.userDirectory = userDirectory;
    }

    @Transactional
    public Merchant register(MerchantRegistrationCommand command) {
        Objects.requireNonNull(command, "command");

        if (merchants.existsByRegistrationNumber(command.registrationNumber().trim())) {
            throw new RegistrationNumberAlreadyRegisteredException(command.registrationNumber().trim());
        }

        UUID userId = userDirectory.createMerchantUser(
                command.fullName().trim(),
                command.contactEmail().trim(),
                command.password()
        );

        Merchant merchant = new Merchant(
                userId,
                command.businessName().trim(),
                command.registrationNumber().trim(),
                command.contactEmail().trim(),
                command.contactPhone().trim()
        );

        return merchants.save(merchant);
    }
}

