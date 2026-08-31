package com.raitukashtam.auth.service;

import com.raitukashtam.auth.entity.Product;
import com.raitukashtam.auth.entity.ProductStatus;
import com.raitukashtam.auth.exception.ResourceAlreadyExistsException;
import com.raitukashtam.auth.exception.ResourceNotFoundException;
import com.raitukashtam.auth.repository.ProductRepository;
import com.raitukashtam.auth.request.ProductRequest;
import com.raitukashtam.auth.response.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;
    private final ModelMapper modelMapper;

    @Transactional
    public ProductResponse createProduct(ProductRequest request, String createdBy) {
        if (productRepository.existsByCode(request.getCode())) {
            throw new ResourceAlreadyExistsException("Product with code " + request.getCode() + " already exists");
        }

        Product product = new Product();
        product.setCode(request.getCode());
        product.setName(request.getName());
        product.setStatus(ProductStatus.ACTIVE);
        product.setCreatedBy(createdBy);

        Product saved = productRepository.save(product);
        return modelMapper.map(saved, ProductResponse.class);
    }

    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(product -> modelMapper.map(product, ProductResponse.class))
                .toList();
    }

    public ProductResponse getProductByCode(String code) {
        Product product = productRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with code: " + code));
        return modelMapper.map(product, ProductResponse.class);
    }
}
