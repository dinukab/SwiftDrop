/**
 * Public API of the merchant module (ADR 0003).
 * Other modules may import only classes from this package.
 *
 * <p>Registration and approval live behind {@code /api/v1/merchants}. Merchant capabilities
 * additionally require status {@code APPROVED} (D-22); role {@code MERCHANT} alone is not enough.
 */
package com.swiftdrop.merchant.api;