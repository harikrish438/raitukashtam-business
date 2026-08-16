package com.raitukashtam.product.controller;

import com.raitukashtam.product.request.ProductRequest;
import com.raitukashtam.product.response.ProductResponse;
import com.raitukashtam.product.service.ProductService;
import com.raitukashtam.product.vo.ResponseTemplateVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/products")
@Slf4j
public class ProductController {
    @Autowired
    private ProductService productService;

    @PostMapping("/")
    public ProductResponse saveProduct(@RequestBody @Validated ProductRequest request) {
        log.info("Inside save method of ProductController");
        return productService.save(request);
    }

    @GetMapping("/{productId}")
    public ResponseTemplateVO getProductWithUser(@PathVariable("productId") Long productId, HttpServletRequest request) {
        log.info("Inside getProductWithUser method of ProductController");
        return productService.getProductWithUser(productId, request);
    }
}
