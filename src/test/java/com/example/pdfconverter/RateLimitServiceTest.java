//package com.example.pdfconverter;
//
//import com.example.pdfconverter.service.AttemptInfo;
//import com.example.pdfconverter.service.RateLimitService;
//import com.example.pdfconverter.service.RedisRateLimitRepository;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.Mockito.*;
//
//class RateLimitServiceTest {
//
//    private RedisRateLimitRepository redisRepo;
//    private RateLimitService rateLimitService;
//
//    private final String userId = "user123";
//    private final String fileHash = "abc123";
//
//    @BeforeEach
//    void setUp() {
//        redisRepo = mock(RedisRateLimitRepository.class);
//        rateLimitService = new RateLimitService(redisRepo);
//    }
//
//    @Test
//    void shouldAllowRequest_whenWithinLimits() {
//        when(redisRepo.getAttempts(userId)).thenReturn(null);
//        when(redisRepo.getFileGenerations(fileHash)).thenReturn(0);
//
//        boolean allowed = rateLimitService.isAllowed(userId, "free", 1024, fileHash);
//
//        assertTrue(allowed);
//        verify(redisRepo).incrementFileGenerations(fileHash);
//        verify(redisRepo).deleteAttempts(userId);
//    }
//
//    @Test
//    void shouldRejectRequest_whenFileSizeTooBig() {
//        boolean allowed = rateLimitService.isAllowed(userId, "free", 10 * 1024 * 1024, fileHash);
//
//        assertFalse(allowed);
//        verifyNoInteractions(redisRepo);
//    }
//
//    @Test
//    void shouldRejectRequest_whenFileGenerationLimitReached() {
//        when(redisRepo.getFileGenerations(fileHash)).thenReturn(RateLimitService.FILE_GENERATION_LIMIT);
//
//        boolean allowed = rateLimitService.isAllowed(userId, "free", 1024, fileHash);
//
//        assertFalse(allowed);
//    }
//
//    @Test
//    void shouldRejectRequest_whenUserIsFrozen() {
//        AttemptInfo frozenInfo = mock(AttemptInfo.class);
//        when(frozenInfo.isFrozen()).thenReturn(true);
//        when(redisRepo.getAttempts(userId)).thenReturn(frozenInfo);
//
//        boolean allowed = rateLimitService.isAllowed(userId, "free", 1024, fileHash);
//
//        assertFalse(allowed);
//    }
//
//    @Test
//    void shouldIncrementAttempts_whenRateLimitExceeded() {
//        when(redisRepo.getFileGenerations(fileHash)).thenReturn(0);
//        when(redisRepo.getAttempts(userId)).thenReturn(null);
//
//        // "free" лимит — 1 запрос в сутки → второй будет отклонён
//        rateLimitService.isAllowed(userId, "free", 1024, fileHash); // разрешено
//        boolean second = rateLimitService.isAllowed(userId, "free", 1024, fileHash); // запрещено
//
//        assertFalse(second);
//        verify(redisRepo, atLeastOnce()).saveAttempts(eq(userId), any(), any());
//    }
//}