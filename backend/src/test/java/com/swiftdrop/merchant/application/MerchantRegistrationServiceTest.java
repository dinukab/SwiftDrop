package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantStatus;

@ExtendWith(MockitoExtension.class)
class MerchantRegistrationServiceTest {

    @Mock
    private MerchantRepository merchants;

    @Mock
    private UserDirectory userDirectory;

    private MerchantRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new MerchantRegistrationService(merchants, userDirectory);
    }

    @Test
    void registerCreatesUserAndPendingMerchant() {
        UUID userId = UUID.randomUUID();
        when(merchants.existsByRegistrationNumber("REG-999")).thenReturn(false);
        when(userDirectory.createMerchantUser("Jane Doe", "jane@merchant.com", "Password123!"))
                .thenReturn(userId);
        when(merchants.save(any(Merchant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MerchantRegistrationCommand command = new MerchantRegistrationCommand(
                "Jane Doe",
                "Acme Store",
                "REG-999",
                "jane@merchant.com",
                "+94771234567",
                "Password123!"
        );

        Merchant result = service.register(command);

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getBusinessName()).isEqualTo("Acme Store");
        assertThat(result.getRegistrationNumber()).isEqualTo("REG-999");
        assertThat(result.getContactEmail()).isEqualTo("jane@merchant.com");
        assertThat(result.getContactPhone()).isEqualTo("+94771234567");
        assertThat(result.getStatus()).isEqualTo(MerchantStatus.PENDING_APPROVAL);

        ArgumentCaptor<Merchant> captor = ArgumentCaptor.forClass(Merchant.class);
        verify(merchants).save(captor.capture());
        assertThat(captor.getValue().getRegistrationNumber()).isEqualTo("REG-999");
    }

    @Test
    void registerThrowsWhenRegistrationNumberAlreadyExists() {
        when(merchants.existsByRegistrationNumber("REG-EXISTS")).thenReturn(true);

        MerchantRegistrationCommand command = new MerchantRegistrationCommand(
                "Jane Doe",
                "Acme Store",
                "REG-EXISTS",
                "jane@merchant.com",
                "+94771234567",
                "Password123!"
        );

        assertThatThrownBy(() -> service.register(command))
                .isInstanceOf(RegistrationNumberAlreadyRegisteredException.class)
                .hasMessageContaining("REG-EXISTS");
    }
}

