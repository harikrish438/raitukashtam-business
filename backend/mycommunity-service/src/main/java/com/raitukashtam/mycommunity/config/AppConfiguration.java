package com.raitukashtam.mycommunity.config;

import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.request.CommunityRequest;
import com.raitukashtam.mycommunity.response.CommunityResponse;
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
        TypeMap<CommunityRequest, Community> communityRequestCommunityTypeMap = modelMapper.createTypeMap(CommunityRequest.class, Community.class);
        TypeMap<Community, CommunityResponse> communityToCommunityResponse = modelMapper.createTypeMap(Community.class, CommunityResponse.class);
        return modelMapper;
    }
}
