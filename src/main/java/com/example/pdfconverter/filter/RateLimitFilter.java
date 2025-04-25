package com.example.pdfconverter.filter;

import com.example.pdfconverter.model.SubscriptionType;
import com.example.pdfconverter.model.User;
import com.example.pdfconverter.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

@Component
@Order(2)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    private String getUserId(HttpServletRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return "anonymous_" + request.getRemoteAddr();
    }

    private SubscriptionType getSubscriptionType() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user.getSubscription();
        }
        return SubscriptionType.FREE;
    }

    private String getFileHash(HttpServletRequest request) {
        String contentType = request.getContentType();
        String contentLength = request.getHeader("Content-Length");
        return (contentType != null ? contentType : "unknown") +
                "_" +
                (contentLength != null ? contentLength : "0");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);

        if ("/api/convert".equals(request.getRequestURI()) && "POST".equalsIgnoreCase(request.getMethod())) {
            String userId = getUserId(request);
            SubscriptionType subscription = getSubscriptionType();
            long fileSize = wrappedRequest.getContentLengthLong();
            String fileHash = getFileHash(wrappedRequest);

            if (!rateLimitService.isAllowed(userId, subscription.name(), fileSize, fileHash)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("""
                    {
                        "error": "rate_limit_exceeded",
                        "message": "You have exceeded your request limit. Please try again later."
                    }
                    """);
                return;
            }
        }

        filterChain.doFilter(wrappedRequest, response);
    }
}