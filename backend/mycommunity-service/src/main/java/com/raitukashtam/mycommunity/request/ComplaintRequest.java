package com.raitukashtam.mycommunity.request;

import com.raitukashtam.mycommunity.entity.ComplaintPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ComplaintRequest {
    @NotBlank(message = "Category is required")
    @Size(max = 100, message = "Category must be at most 100 characters")
    private String category;

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be at most 255 characters")
    private String title;

    @NotBlank(message = "Description is required")
    private String description;

    /** Optional -- defaults to MEDIUM. */
    private ComplaintPriority priority;
}
