package com.raitukashtam.product.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {
    @NotBlank(message = "Product name is required")
    private String name;
    private String description;
    
    @NotNull(message = "Product price is required")
    @Positive(message = "Price must be a positive number")
    private Double price;
    
    @NotNull(message = "Seller user id is required")
    @Positive(message = "User ID must be a positive number")
    private Long userId;
}
