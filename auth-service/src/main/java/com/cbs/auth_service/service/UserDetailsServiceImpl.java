package com.cbs.auth_service.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cbs.auth_service.repository.AuthUserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Bridges Spring Security's {@link UserDetailsService} contract with our
 * {@link com.suprab.cbs.auth.repository.AuthUserRepository}.
 *
 * <p>Supports login by both username and email: if the supplied identifier
 * contains an '@', it is treated as an email address.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final AuthUserRepository authUserRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        if (identifier.contains("@")) {
            return authUserRepository.findByEmail(identifier)
                .orElseThrow(() -> new UsernameNotFoundException(
                    "No user found with email: " + identifier));
        }
        return authUserRepository.findByUsername(identifier)
            .orElseThrow(() -> new UsernameNotFoundException(
                "No user found with username: " + identifier));
    }
}