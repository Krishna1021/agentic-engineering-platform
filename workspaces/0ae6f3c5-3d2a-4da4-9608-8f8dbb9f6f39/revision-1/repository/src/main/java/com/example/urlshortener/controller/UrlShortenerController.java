package com.example.urlshortener.controller;

import com.example.urlshortener.service.UrlShortenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.NoSuchElementException;

@RestController
@Validated
@Tag(name = "URL Shortener API", description = "APIs for URL shortening and redirect")
public class UrlShortenerController {

    private final UrlShortenerService urlShortenerService;

    public UrlShortenerController(UrlShortenerService urlShortenerService) {
        this.urlShortenerService = urlShortenerService;
    }

    public static record ShortenRequest(@NotBlank String url) {}

    public static record ShortenResponse(String shortCode) {}

    @Operation(summary = "Create a shortened URL")
    @ApiResponse(responseCode = "200", description = "Short URL created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid URL supplied")
    @ApiResponse(responseCode = "409", description = "Could not generate unique short code after retries")
    @PostMapping("/api/shorten")
    public ResponseEntity<ShortenResponse> shortenUrl(@Valid @RequestBody ShortenRequest request) {
        try {
            String shortCode = urlShortenerService.createShortUrl(request.url());
            return ResponseEntity.ok(new ShortenResponse(shortCode));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @Operation(summary = "Redirect to original URL")
    @ApiResponse(responseCode = "302", description = "Redirection successful")
    @ApiResponse(responseCode = "404", description = "Short code not found")
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirectToOriginal(@PathVariable @Size(min=8, max=8) String shortCode) {
        try {
            String originalUrl = urlShortenerService.findOriginalUrl(shortCode);
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(originalUrl));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
