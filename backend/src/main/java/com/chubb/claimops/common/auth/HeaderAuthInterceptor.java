package com.chubb.claimops.common.auth;

import com.chubb.claimops.common.error.AuthenticationRequiredException;
import com.chubb.claimops.common.error.ForbiddenOperationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Resolves the caller from the {@code X-User-Role} and {@code X-User-Name} headers and enforces
 * role boundaries by URL prefix: {@code /api/claimant/**} is for claimants and {@code /api/staff/**} is for staff.
 *
 * <p><b>Trade-off:</b> the headers are trusted as sent. This keeps the time-boxed build focused on the
 * claims domain, and the rest of the code depends only on {@link CurrentUser}. Moving to OAuth2/JWT later means
 * replacing this class with a resource-server filter that builds the same {@code CurrentUser} from verified token
 * claims. Controllers and services stay unchanged.
 *
 * <p>Failures are thrown as exceptions so that {@code GlobalExceptionHandler} renders every error in the same
 * Problem Details format.
 */
@Component
public class HeaderAuthInterceptor implements HandlerInterceptor {

    public static final String ROLE_HEADER = "X-User-Role";
    public static final String NAME_HEADER = "X-User-Name";
    static final String CURRENT_USER_ATTRIBUTE = CurrentUser.class.getName();

    private static final int MAX_NAME_LENGTH = 100;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        CurrentUser user = resolve(request);
        UserRole required = requiredRole(request.getRequestURI());
        if (required != null && user.role() != required) {
            throw new ForbiddenOperationException(
                    "Role " + user.role() + " cannot access " + required.name().toLowerCase() + " endpoints");
        }
        request.setAttribute(CURRENT_USER_ATTRIBUTE, user);
        return true;
    }

    private CurrentUser resolve(HttpServletRequest request) {
        String roleHeader = request.getHeader(ROLE_HEADER);
        String name = request.getHeader(NAME_HEADER);
        if (!StringUtils.hasText(roleHeader) || !StringUtils.hasText(name)) {
            throw new AuthenticationRequiredException(
                    "Headers " + ROLE_HEADER + " and " + NAME_HEADER + " are required");
        }
        String trimmedName = name.trim();
        if (trimmedName.length() > MAX_NAME_LENGTH) {
            throw new AuthenticationRequiredException(NAME_HEADER + " must be at most " + MAX_NAME_LENGTH + " characters");
        }
        try {
            return new CurrentUser(trimmedName, UserRole.valueOf(roleHeader.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new AuthenticationRequiredException(ROLE_HEADER + " must be CLAIMANT or STAFF");
        }
    }

    private static UserRole requiredRole(String path) {
        if (path.startsWith("/api/claimant/")) {
            return UserRole.CLAIMANT;
        }
        if (path.startsWith("/api/staff/")) {
            return UserRole.STAFF;
        }
        return null;
    }
}
