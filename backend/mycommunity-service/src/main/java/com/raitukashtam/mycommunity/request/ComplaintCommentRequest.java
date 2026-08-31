package com.raitukashtam.mycommunity.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ComplaintCommentRequest {
    @NotBlank(message = "Comment is required")
    private String comment;
}
