package com.example.pdfconverter.filter;

import com.example.pdfconverter.model.RateLimitResult;
import com.example.pdfconverter.model.SubscriptionType;
import com.example.pdfconverter.model.User;
import com.example.pdfconverter.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Objects;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    private String getUserId(HttpServletRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return Objects.toString(user.getId(), "unknown_user");
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
        try {
            if (request instanceof ContentCachingRequestWrapper) {
                ContentCachingRequestWrapper wrappedRequest = (ContentCachingRequestWrapper) request;

                // For multipart requests
                if (request.getContentType() != null &&
                        request.getContentType().startsWith("multipart/form-data")) {

                    StandardServletMultipartResolver resolver = new StandardServletMultipartResolver();
                    if (resolver.isMultipart(request)) {
                        MultipartHttpServletRequest multipartRequest = resolver.resolveMultipart(request);
                        MultipartFile file = multipartRequest.getFile("file");
                        if (file != null && !file.isEmpty()) {
                            return DigestUtils.md5DigestAsHex(file.getBytes());
                        }
                    }
                }
                // For regular requests
                byte[] content = wrappedRequest.getContentAsByteArray();
                if (content.length > 0) {
                    return DigestUtils.md5DigestAsHex(content);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to calculate full file hash: {}", e.getMessage());
        }

        // Fallback
        String contentType = request.getContentType();
        String contentLength = request.getHeader("Content-Length");
        return String.format("%s_%s_%s",
                contentType != null ? contentType : "unknown",
                contentLength != null ? contentLength : "0",
                request.getRemoteAddr());
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

            log.info("RateLimit check for user={}, subscription={}, size={} bytes, hash={}", userId, subscription, fileSize, fileHash);

            RateLimitResult result = rateLimitService.isAllowed(userId, subscription.name(), fileSize, fileHash);
            if (!result.allowed()) {
                log.warn("Rate limit exceeded for user={} — {}", userId, result.message());
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write(String.format("""
                    {
                        "error": "rate_limit_exceeded",
                        "message": "%s"
                    }
                    """, result.message()));
                return;
            } else {
                log.debug("Rate limit passed for user={}", userId);
            }
        }

        filterChain.doFilter(wrappedRequest, response);
    }
}