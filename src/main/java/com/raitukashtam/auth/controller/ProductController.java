package com.raitukashtam.auth.controller;

import com.raitukashtam.auth.entity.Product;
import com.raitukashtam.auth.exception.ResourceNotFoundException;
import com.raitukashtam.auth.repository.ProductRepository;
import com.raitukashtam.auth.response.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductRepository productRepository;
    private final ModelMapper modelMapper;

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        List<ProductResponse> products = productRepository.findAll().stream()
                .map(product -> modelMapper.map(product, ProductResponse.class))
                .toList();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{code}")
    public ResponseEntity<ProductResponse> getProductByCode(@PathVariable String code) {
        Product product = productRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with code: " + code));
        return ResponseEntity.ok(modelMapper.map(product, ProductResponse.class));
    }
}
