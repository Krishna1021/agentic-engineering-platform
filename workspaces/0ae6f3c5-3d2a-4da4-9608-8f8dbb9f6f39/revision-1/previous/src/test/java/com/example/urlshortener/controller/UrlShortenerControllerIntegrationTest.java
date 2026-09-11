package com.example.urlshortener.controller;

import com.example.urlshortener.UrlShortenerApplication;
import com.example.urlshortener.entity.UrlMapping;
import com.example.urlshortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.hasLength;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = UrlShortenerApplication.class)
@AutoConfigureMockMvc
class UrlShortenerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UrlMappingRepository repository;

    @BeforeEach
    void cleanup() {
        repository.deleteAll();
    }

    @Test
    void testShortenUrl_success() throws Exception {
        String jsonRequest = "{\"url\":\"https://spring.io\"}";

        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode", hasLength(8)))
                .andExpect(jsonPath("$.shortCode", matchesPattern("[0-9A-Za-z]{8}")));
    }

    @Test
    void testShortenUrl_invalidUrl_badRequest() throws Exception {
        String jsonRequest = "{\"url\":\"ftp://invalid.url\"}";

        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testRedirectToOriginal_found() throws Exception {
        UrlMapping mapping = new UrlMapping("AbCd1234", "https://example.com", Instant.now());
        repository.save(mapping);

        mockMvc.perform(get("/AbCd1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"));
    }

    @Test
    void testRedirectToOriginal_notFound() throws Exception {
        mockMvc.perform(get("/NoExist8"))
                .andExpect(status().isNotFound());
    }
}
