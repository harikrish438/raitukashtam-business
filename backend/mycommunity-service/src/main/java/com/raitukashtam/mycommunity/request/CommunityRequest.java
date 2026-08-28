package com.raitukashtam.mycommunity.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommunityRequest {
    @NotBlank(message = "Community name is required")
    private String name;
    private String description;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be a positive number")
    private Double price;

    @NotNull(message = "User id is required")
    @Positive(message = "User ID must be a positive number")
    private Long userId;
}
