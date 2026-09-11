package com.raitukashtam.mycommunity.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Minimal community info for the "find your community to join" search -- deliberately omits
 * address/billing details a non-member shouldn't see before their join request is approved. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommunitySearchResponse {
    private Long id;
    private String name;
    private String area;
    private String district;
}
