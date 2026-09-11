package com.example.urlshortener.service;

import com.example.urlshortener.entity.UrlMapping;
import com.example.urlshortener.repository.UrlMappingRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class UrlShortenerService {
    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int SHORT_CODE_LENGTH = 8;
    private static final int MAX_COLLISION_RETRIES = 5;

    private final UrlMappingRepository repository;
    private final SecureRandom random = new SecureRandom();

    public UrlShortenerService(UrlMappingRepository repository) {
        this.repository = repository;
    }

    public String createShortUrl(String originalUrl) {        
        validateUrl(originalUrl);

        for (int i = 0; i < MAX_COLLISION_RETRIES; i++) {
            String code = generateShortCode();
            if (!repository.existsByShortCode(code)) {
                UrlMapping mapping = new UrlMapping(code, originalUrl, Instant.now());
                repository.save(mapping);
                return code;
            }
        }
        throw new RuntimeException("Failed to generate a unique short code after maximum retries");
    }

    public String findOriginalUrl(String shortCode) {
        Optional<UrlMapping> mapping = repository.findById(shortCode);
        if (mapping.isEmpty()) {
            throw new NoSuchElementException("Short code not found");
        }
        return mapping.get().getOriginalUrl();
    }

    private void validateUrl(String url) {
        try {
            URI uri = new URI(url);
            if (uri.getScheme() == null || !(uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("URL must have http or https scheme");
            }
            // More strict validations can be added here if needed
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL format");
        }
    }

    private String generateShortCode() {
        char[] codeChars = new char[SHORT_CODE_LENGTH];
        for (int i = 0; i < SHORT_CODE_LENGTH; i++) {
            int idx = random.nextInt(BASE62.length());
            codeChars[i] = BASE62.charAt(idx);
        }
        return new String(codeChars);
    }
}
