package com.swiftdrop.identity.application;

import com.swiftdrop.identity.api.MerchantApprovalRequest;
import com.swiftdrop.identity.api.UserStatusUpdateRequest;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.identity.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AdminUserService {

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void updateUserStatus(UUID userId, UserStatusUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
        
        user.setEnabled(request.enabled());
        userRepository.save(user);
    }

    @Transactional
    public void approveMerchant(UUID merchantId, MerchantApprovalRequest request) {
        // Merchant status update logic (UUIDs are used across modules)
        User merchantUser = userRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant user not found with id: " + merchantId));

        if (request.approved()) {
            merchantUser.setEnabled(true);
        } else {
            merchantUser.setEnabled(false);
        }
        userRepository.save(merchantUser);
    }
}