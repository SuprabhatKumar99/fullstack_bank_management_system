package com.cbs.auth_service.entity;


/**
 * CBS platform roles.
 *
 * <p>These role strings are embedded directly into JWT claims under the key {@code role}.
 * Downstream microservices read this claim from the validated token and apply
 * method-level security via {@code @PreAuthorize("hasRole('TELLER')")} etc.
 */
public enum Role {

    /** End customers accessing their own accounts. */
    ROLE_CUSTOMER,

    /** Bank tellers performing counter operations. */
    ROLE_TELLER,

    /** Branch managers with approval authority. */
    ROLE_MANAGER,

    /** System administrators with full access. */
    ROLE_ADMIN,

    /** Internal service accounts for inter-service calls. */
    ROLE_SERVICE
}