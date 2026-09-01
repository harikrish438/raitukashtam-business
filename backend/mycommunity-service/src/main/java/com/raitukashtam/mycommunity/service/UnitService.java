package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.Unit;
import com.raitukashtam.mycommunity.exception.ResourceAlreadyExistsException;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.repository.UnitRepository;
import com.raitukashtam.mycommunity.request.AssignUnitRequest;
import com.raitukashtam.mycommunity.request.UnitRequest;
import com.raitukashtam.mycommunity.request.UnitUpdateRequest;
import com.raitukashtam.mycommunity.response.CommunityMemberResponse;
import com.raitukashtam.mycommunity.response.UnitResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Unit master data (block/floor/area/type) -- ADMIN manages, any ACTIVE
 * member browses, same shape as AmenityService. A community's members keep
 * their free-text unitNumber (Phase 1) regardless of whether Units exist;
 * linking a member to a structured Unit (assignUnitToMember) is a separate,
 * explicit admin action -- purely additive, doesn't require every community
 * to adopt structured units.
 */
@Service
@Slf4j
public class UnitService {
    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityMemberRepository communityMemberRepository;

    @Autowired
    private CommunityService communityService;

    @Transactional
    public UnitResponse createUnit(Long communityId, UnitRequest request, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);

        String unitNumber = request.getUnitNumber().trim();
        if (unitRepository.existsByCommunity_IdAndUnitNumberIgnoreCase(communityId, unitNumber)) {
            throw new ResourceAlreadyExistsException("Unit '" + unitNumber + "' already exists in this community");
        }

        Community community = communityRepository.getReferenceById(communityId);
        Unit unit = new Unit();
        unit.setCommunity(community);
        unit.setUnitNumber(unitNumber);
        unit.setBlock(request.getBlock());
        unit.setFloor(request.getFloor());
        unit.setAreaSqft(request.getAreaSqft());
        unit.setUnitType(request.getUnitType());
        unit.setActive(true);

        return toResponse(unitRepository.save(unit));
    }

    @Transactional(readOnly = true)
    public List<UnitResponse> listUnits(Long communityId, String callerIdentityId) {
        communityService.requireActiveMember(communityId, callerIdentityId);
        return unitRepository.findByCommunity_IdOrderByUnitNumberAsc(communityId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UnitResponse getUnit(Long communityId, Long unitId, String callerIdentityId) {
        communityService.requireActiveMember(communityId, callerIdentityId);
        return toResponse(requireUnit(communityId, unitId));
    }

    @Transactional
    public UnitResponse updateUnit(Long communityId, Long unitId, UnitUpdateRequest request, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        Unit unit = requireUnit(communityId, unitId);

        if (request.getUnitNumber() != null) {
            if (request.getUnitNumber().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unit number cannot be blank");
            }
            String newUnitNumber = request.getUnitNumber().trim();
            if (!newUnitNumber.equalsIgnoreCase(unit.getUnitNumber())
                    && unitRepository.existsByCommunity_IdAndUnitNumberIgnoreCase(communityId, newUnitNumber)) {
                throw new ResourceAlreadyExistsException("Unit '" + newUnitNumber + "' already exists in this community");
            }
            unit.setUnitNumber(newUnitNumber);
        }
        if (request.getBlock() != null) {
            unit.setBlock(request.getBlock());
        }
        if (request.getFloor() != null) {
            unit.setFloor(request.getFloor());
        }
        if (request.getAreaSqft() != null) {
            unit.setAreaSqft(request.getAreaSqft());
        }
        if (request.getUnitType() != null) {
            unit.setUnitType(request.getUnitType());
        }

        return toResponse(unitRepository.save(unit));
    }

    @Transactional
    public UnitResponse deactivateUnit(Long communityId, Long unitId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        Unit unit = requireUnit(communityId, unitId);
        if (!unit.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unit is already inactive");
        }
        unit.setActive(false);
        return toResponse(unitRepository.save(unit));
    }

    @Transactional
    public CommunityMemberResponse assignUnitToMember(Long communityId, Long memberId, AssignUnitRequest request, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);

        CommunityMember member = communityMemberRepository.findByIdAndCommunity_Id(memberId, communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + memberId));
        Unit unit = requireUnit(communityId, request.getUnitId());
        if (!unit.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot assign an inactive unit");
        }

        member.setUnit(unit);
        member.setUnitNumber(unit.getUnitNumber());

        return communityService.toResponse(communityMemberRepository.save(member));
    }

    Unit requireUnit(Long communityId, Long unitId) {
        return unitRepository.findByIdAndCommunity_Id(unitId, communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found with id: " + unitId));
    }

    UnitResponse toResponse(Unit unit) {
        return new UnitResponse(
                unit.getId(),
                unit.getCommunity().getId(),
                unit.getUnitNumber(),
                unit.getBlock(),
                unit.getFloor(),
                unit.getAreaSqft(),
                unit.getUnitType(),
                unit.isActive(),
                unit.getCreatedAt());
    }
}
