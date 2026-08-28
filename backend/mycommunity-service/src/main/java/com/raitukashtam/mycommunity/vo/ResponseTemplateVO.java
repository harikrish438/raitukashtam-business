package com.raitukashtam.mycommunity.vo;

import com.raitukashtam.mycommunity.entity.Community;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResponseTemplateVO {
    private User user;
    private Community community;
}
