package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.Vendor;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.repository.VendorRepository;
import com.raitukashtam.mycommunity.request.VendorRequest;
import com.raitukashtam.mycommunity.response.VendorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * ADMIN-only end to end, same as Staff. Membership authorization is
 * delegated to CommunityService.
 */
@Service
@Slf4j
public class VendorService {
    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityService communityService;

    @Transactional
    public VendorResponse createVendor(Long communityId, VendorRequest request, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);

        Community community = communityRepository.getReferenceById(communityId);
        Vendor vendor = new Vendor();
        vendor.setCommunity(community);
        vendor.setName(request.getName().trim());
        vendor.setServiceType(request.getServiceType().trim());
        vendor.setContactPerson(request.getContactPerson());
        vendor.setPhoneNumber(request.getPhoneNumber());
        vendor.setActive(true);
        Vendor saved = vendorRepository.save(vendor);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<VendorResponse> listVendors(Long communityId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        return vendorRepository.findByCommunity_IdOrderByNameAsc(communityId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public VendorResponse getVendor(Long communityId, Long vendorId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        return toResponse(requireVendor(communityId, vendorId));
    }

    @Transactional
    public VendorResponse deactivateVendor(Long communityId, Long vendorId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        Vendor vendor = requireVendor(communityId, vendorId);
        if (!vendor.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vendor is already inactive");
        }
        vendor.setActive(false);
        return toResponse(vendorRepository.save(vendor));
    }

    /** Package-private -- reused by ExpenseService so linking an expense to a vendor doesn't duplicate the "vendor exists in this community" lookup. */
    Vendor requireVendor(Long communityId, Long vendorId) {
        return vendorRepository.findByIdAndCommunity_Id(vendorId, communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found with id: " + vendorId));
    }

    VendorResponse toResponse(Vendor vendor) {
        return new VendorResponse(
                vendor.getId(),
                vendor.getCommunity().getId(),
                vendor.getName(),
                vendor.getServiceType(),
                vendor.getContactPerson(),
                vendor.getPhoneNumber(),
                vendor.isActive());
    }
}
