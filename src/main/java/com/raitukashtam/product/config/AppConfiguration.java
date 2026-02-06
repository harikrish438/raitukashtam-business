package com.raitukashtam.product.config;

import com.raitukashtam.product.entity.Product;
import com.raitukashtam.product.request.ProductRequest;
import com.raitukashtam.product.response.ProductResponse;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeMap;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfiguration {
    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();
        TypeMap<ProductRequest, Product> productRequestProductTypeMap = modelMapper.createTypeMap(ProductRequest.class, Product.class);
        TypeMap<Product, ProductResponse> productToProductResponse = modelMapper.createTypeMap(Product.class, ProductResponse.class);
        return modelMapper;
    }
    
    /*@Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }*/
}
