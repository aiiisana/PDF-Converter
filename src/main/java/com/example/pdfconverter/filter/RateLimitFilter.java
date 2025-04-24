package com.example.pdfconverter.filter;

import com.example.pdfconverter.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    public RateLimitFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    private String getUserIdFromRequest(HttpServletRequest request) {
        // Implement your logic to get user ID (could be from headers, tokens, etc.)
        return request.getHeader("X-User-Id") != null ?
                request.getHeader("X-User-Id") :
                request.getRemoteAddr(); // Fallback to IP if no user ID
    }

    private String getSubscriptionType(String userId) {
        // Implement your logic to get subscription type
        // This could be from a database or user service
        return "free"; // Default to free tier
    }

    private String getFileHash(HttpServletRequest request) {
        // Implement logic to generate a hash of the file
        // For simplicity, we'll use the content length and some headers
        return String.valueOf(request.getContentLengthLong()) +
                request.getHeader("Content-Type") +
                System.currentTimeMillis();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);

        if ("/api/convert".equals(request.getRequestURI()) && "POST".equalsIgnoreCase(request.getMethod())) {
            String userId = getUserIdFromRequest(wrappedRequest); // Implement your user identification logic
            String subscriptionType = getSubscriptionType(userId); // Implement subscription type retrieval
            String fileHash = getFileHash(wrappedRequest); // Implement file hash generation

            // Get file size from the request
            long fileSize = wrappedRequest.getContentLengthLong();

            if (!rateLimitService.isAllowed(userId, subscriptionType, fileSize, fileHash)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.getWriter().write("Rate limit exceeded");
                return;
            }
        }

        filterChain.doFilter(wrappedRequest, response);
    }
}