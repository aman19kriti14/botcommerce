package com.botcommerce.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.botcommerce.dto.store.StoreDtos.*;
import com.botcommerce.service.StoreService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/store")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    @GetMapping("/{slug}")
    public ResponseEntity<StoreResponse> getStore(@PathVariable String slug) {
        return ResponseEntity.ok(storeService.getStore(slug));
    }
}