package com.chubb.claimops.common.auth;

/**
 * The caller of the current request, as resolved by {@link HeaderAuthInterceptor}.
 *
 * <p>Controllers declare it as a method parameter; {@link CurrentUserArgumentResolver} supplies it.
 *
 * @param name the user's display name, which is also the identity used for ownership and assignment
 * @param role the user's role
 */
public record CurrentUser(String name, UserRole role) {
}
