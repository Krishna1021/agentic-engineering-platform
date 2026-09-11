package com.example.urlshortener.service;

import com.example.urlshortener.entity.UrlMapping;
import com.example.urlshortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UrlShortenerServiceTest {

    private UrlMappingRepository repository;
    private UrlShortenerService service;

    @BeforeEach
    void setup() {
        repository = mock(UrlMappingRepository.class);
        service = new UrlShortenerService(repository);
    }

    @Test
    void testCreateShortUrl_validUrl_savesAndReturnsCode() {
        when(repository.existsByShortCode(anyString())).thenReturn(false);

        String originalUrl = "https://example.com/foo";
        String shortCode = service.createShortUrl(originalUrl);

        assertNotNull(shortCode);
        assertEquals(8, shortCode.length());

        ArgumentCaptor<UrlMapping> captor = ArgumentCaptor.forClass(UrlMapping.class);
        verify(repository).save(captor.capture());
        UrlMapping saved = captor.getValue();
        assertEquals(shortCode, saved.getShortCode());
        assertEquals(originalUrl, saved.getOriginalUrl());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void testCreateShortUrl_invalidUrl_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> service.createShortUrl("htp://bad-url"));
        assertThrows(IllegalArgumentException.class, () -> service.createShortUrl("ftp://example.com"));
        assertThrows(IllegalArgumentException.class, () -> service.createShortUrl(""));
    }

    @Test
    void testCreateShortUrl_exceedsCollisionRetries_throwsRuntimeException() {
        when(repository.existsByShortCode(anyString())).thenReturn(true);
        String url = "https://valid.com";

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.createShortUrl(url));
        assertTrue(ex.getMessage().contains("Failed to generate a unique short code"));

        verify(repository, times(5)).existsByShortCode(anyString());
        verify(repository, never()).save(any());
    }

    @Test
    void testFindOriginalUrl_existingShortCode_returnsUrl() {
        UrlMapping mockMapping = new UrlMapping("AbCd1234", "https://site.com", Instant.now());
        when(repository.findById("AbCd1234")).thenReturn(Optional.of(mockMapping));

        String result = service.findOriginalUrl("AbCd1234");
        assertEquals("https://site.com", result);
    }

    @Test
    void testFindOriginalUrl_nonexistentShortCode_throwsNoSuchElementException() {
        when(repository.findById("missing1")).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.findOriginalUrl("missing1"));
    }
}
