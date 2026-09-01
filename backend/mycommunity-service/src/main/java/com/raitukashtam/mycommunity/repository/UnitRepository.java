package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnitRepository extends JpaRepository<Unit, Long> {

    List<Unit> findByCommunity_IdOrderByUnitNumberAsc(Long communityId);

    Optional<Unit> findByIdAndCommunity_Id(Long id, Long communityId);

    boolean existsByCommunity_IdAndUnitNumberIgnoreCase(Long communityId, String unitNumber);
}
