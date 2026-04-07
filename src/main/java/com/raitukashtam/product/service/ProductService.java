package com.raitukashtam.product.service;

import com.raitukashtam.product.entity.Product;
import com.raitukashtam.product.exception.ResourceAlreadyExistsException;
import com.raitukashtam.product.exception.ResourceNotFoundException;
import com.raitukashtam.product.repository.ProductRepository;
import com.raitukashtam.product.request.ProductRequest;
import com.raitukashtam.product.response.ProductResponse;
import com.raitukashtam.product.vo.ResponseTemplateVO;
import com.raitukashtam.product.vo.User;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class ProductService {
    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Transactional
    public ProductResponse save(ProductRequest request) {
        log.info("Inside save method of ProductService");
        try {
            // Check if product with the same name already exists (case-insensitive)
            if (productRepository.existsByNameIgnoreCase(request.getName())) {
                throw new ResourceAlreadyExistsException("A product with the name '" + request.getName() + "' already exists");
            }
            Product product = new Product();
            product.setDescription(request.getDescription());
            product.setPrice(request.getPrice());
            product.setName(request.getName());
            product.setUserId(request.getUserId());
            Product productSaved = productRepository.save(product);
            return modelMapper.map(productSaved, ProductResponse.class);
        } catch (Exception e) {
            log.error("Error saving product: {}", e.getMessage(), e);
            throw e;
        }
    }

    public ResponseTemplateVO getProductWithUser(Long productId) {
        log.info("Inside getProductWithUser method of ProductService class");
        
        // Find product or throw exception if not found
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
        
        // Set up headers with Authorization
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJyYWl0dWthc2h0YW0tYXV0aC1zZXJ2aWNlIiwic3ViIjoidGVzdDNAZ21haWwuY29tIiwicm9sZSI6IkNPTlNVTUVSIiwidGVuYW50X2NvZGUiOiJURU5BTlQiLCJqdGkiOiI0MGJkM2UxYy0yODRjLTQ0MzItOGYyZi1jMjhlYzA4YmFiYzEiLCJpYXQiOjE3NzU1MzA4ODksImV4cCI6MTc3NTUzMTc4OX0.gLXXMvi4AyZTzMjLBbTf4LWqcnaGWIZ6vPXLSN7vfUI");
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        // Make the request with headers using service discovery
        ResponseEntity<User> response = restTemplate.exchange(
            "http://auth-service/users/" + product.getUserId(),
            HttpMethod.GET,
            entity,
            User.class
        );
        
        if (response.getBody() == null) {
            throw new RuntimeException("User not found for id: " + product.getUserId());
        }
        
        // Create and return response
        ResponseTemplateVO responseTemplateVO = new ResponseTemplateVO();
        responseTemplateVO.setProduct(product);
        responseTemplateVO.setUser(response.getBody());
        return responseTemplateVO;
    }
}
