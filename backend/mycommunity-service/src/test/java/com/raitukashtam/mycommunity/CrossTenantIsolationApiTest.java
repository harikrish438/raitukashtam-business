package com.raitukashtam.mycommunity;

import com.raitukashtam.mycommunity.entity.Amenity;
import com.raitukashtam.mycommunity.entity.Bill;
import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.Staff;
import com.raitukashtam.mycommunity.support.AbstractCrossTenantTest;
import com.raitukashtam.mycommunity.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Go-live plan P0 item "cross-tenant-test-matrix": one test per resource
 * type asserting that an ADMIN of community B can neither (a) reach
 * community A's data by passing A's id in the path, nor (b) reach it by
 * passing B's own id together with a resource id that actually belongs to
 * A. Every prior audit only sampled the requireActiveAdmin/
 * requireActiveMember + findByIdAndCompound_Id guard pattern across a
 * handful of services -- this exercises it for real, over real HTTP,
 * against a real Postgres, for every resource type in the service.
 *
 * Each test also confirms the LEGITIMATE owner (adminA, on community A,
 * with A's own resource id) gets a 2xx first -- without that, a 404 from
 * the cross-tenant assertion below would be indistinguishable from "this
 * route doesn't even exist" (e.g. a typo'd path), which would let a broken
 * guard hide behind a coincidentally-matching status code.
 *
 * adminB (not residentB) is used as the attacking identity throughout:
 * they're ADMIN in their own community, so every admin-only endpoint
 * (Expense, Staff, Vendor, join-request approval) is reachable to them in
 * principle -- the only thing that should stop them is the tenant
 * boundary itself, not a role restriction, which is exactly what these
 * tests isolate.
 */
class CrossTenantIsolationApiTest extends AbstractCrossTenantTest {

    @Autowired
    private TestDataFactory data;

    @Test
    void unit_crossTenantAccess_isForbiddenAndNotFound() {
        Community communityA = data.createCommunity("Unit-A");
        Community communityB = data.createCommunity("Unit-B");
        CommunityMember adminA = data.createMember(communityA, CommunityRole.ADMIN, data.uniqueIdentityId());
        CommunityMember adminB = data.createMember(communityB, CommunityRole.ADMIN, data.uniqueIdentityId());
        var unit = data.createUnit(communityA);

        assertCrossTenantIsolation(communityA.getId(), communityB.getId(), unit.getId(),
                "/api/v1/communities/%d/units/%d", HttpMethod.GET,
                mintToken(adminA.getIdentityId()), mintToken(adminB.getIdentityId()));
    }

    @Test
    void announcement_crossTenantAccess_isForbiddenAndNotFound() {
        Community communityA = data.createCommunity("Ann-A");
        Community communityB = data.createCommunity("Ann-B");
        CommunityMember adminA = data.createMember(communityA, CommunityRole.ADMIN, data.uniqueIdentityId());
        CommunityMember adminB = data.createMember(communityB, CommunityRole.ADMIN, data.uniqueIdentityId());
        var announcement = data.createAnnouncement(communityA, adminA);

        assertCrossTenantIsolation(communityA.getId(), communityB.getId(), announcement.getId(),
                "/api/v1/communities/%d/announcements/%d", HttpMethod.GET,
                mintToken(adminA.getIdentityId()), mintToken(adminB.getIdentityId()));
    }

    @Test
    void bill_crossTenantAccess_isForbiddenAndNotFound() {
        Community communityA = data.createCommunity("Bill-A");
        Community communityB = data.createCommunity("Bill-B");
        CommunityMember adminA = data.createMember(communityA, CommunityRole.ADMIN, data.uniqueIdentityId());
        CommunityMember adminB = data.createMember(communityB, CommunityRole.ADMIN, data.uniqueIdentityId());
        Bill bill = data.createBill(communityA, adminA);

        assertCrossTenantIsolation(communityA.getId(), communityB.getId(), bill.getId(),
                "/api/v1/communities/%d/bills/%d", HttpMethod.GET,
                mintToken(adminA.getIdentityId()), mintToken(adminB.getIdentityId()));
    }

    @Test
    void payment_crossTenantAccess_isForbiddenAndNotFound() {
        Community communityA = data.createCommunity("Pay-A");
        Community communityB = data.createCommunity("Pay-B");
        CommunityMember adminA = data.createMember(communityA, CommunityRole.ADMIN, data.uniqueIdentityId());
        CommunityMember adminB = data.createMember(communityB, CommunityRole.ADMIN, data.uniqueIdentityId());
        Bill bill = data.createBill(communityA, adminA);
        data.createPayment(communityA, bill, adminA);

        // Payment is looked up by its Bill's id (getPaymentForBill), not its own id --
        // the bill itself is the compound-lookup boundary here.
        assertCrossTenantIsolation(communityA.getId(), communityB.getId(), bill.getId(),
                "/api/v1/communities/%d/bills/%d/payment", HttpMethod.GET,
                mintToken(adminA.getIdentityId()), mintToken(adminB.getIdentityId()));
    }

    @Test
    void expense_crossTenantAccess_isForbiddenAndNotFound() {
        Community communityA = data.createCommunity("Exp-A");
        Community communityB = data.createCommunity("Exp-B");
        CommunityMember adminA = data.createMember(communityA, CommunityRole.ADMIN, data.uniqueIdentityId());
        CommunityMember adminB = data.createMember(communityB, CommunityRole.ADMIN, data.uniqueIdentityId());
        var expense = data.createExpense(communityA, adminA);

        assertCrossTenantIsolation(communityA.getId(), communityB.getId(), expense.getId(),
                "/api/v1/communities/%d/expenses/%d", HttpMethod.GET,
                mintToken(adminA.getIdentityId()), mintToken(adminB.getIdentityId()));
    }

    @Test
    void visitor_crossTenantAccess_isForbiddenAndNotFound() {
        Community communityA = data.createCommunity("Vis-A");
        Community communityB = data.createCommunity("Vis-B");
        CommunityMember adminA = data.createMember(communityA, CommunityRole.ADMIN, data.uniqueIdentityId());
        CommunityMember adminB = data.createMember(communityB, CommunityRole.ADMIN, data.uniqueIdentityId());
        var visitor = data.createVisitor(communityA, adminA);

        assertCrossTenantIsolation(communityA.getId(), communityB.getId(), visitor.getId(),
                "/api/v1/communities/%d/visitors/%d", HttpMethod.GET,
                mintToken(adminA.getIdentityId()), mintToken(adminB.getIdentityId()));
    }

    @Test
    void amenity_crossTenantAccess_isForbiddenAndNotFound() {
        Community communityA = data.createCommunity("Amn-A");
        Community communityB = data.createCommunity("Amn-B");
        CommunityMember adminA = data.createMember(communityA, CommunityRole.ADMIN, data.uniqueIdentityId());
        CommunityMember adminB = data.createMember(communityB, CommunityRole.ADMIN, data.uniqueIdentityId());
        Amenity amenity = data.createAmenity(communityA);

        assertCrossTenantIsolation(communityA.getId(), communityB.getId(), amenity.getId(),
                "/api/v1/communities/%d/amenities/%d", HttpMethod.GET,
                mintToken(adminA.getIdentityId()), mintToken(adminB.getIdentityId()));
    }

    @Test
    void amenityBooking_crossTenantAccess_isForbiddenAndNotFound() {
        Community communityA = data.createCommunity("Bkg-A");
        Community communityB = data.createCommunity("Bkg-B");
        CommunityMember adminA = data.createMember(communityA, CommunityRole.ADMIN, data.uniqueIdentityId());
        CommunityMember adminB = data.createMember(communityB, CommunityRole.ADMIN, data.uniqueIdentityId());
        Amenity amenity = data.createAmenity(communityA);
        var booking = data.createAmenityBooking(communityA, amenity, adminA);

        assertCrossTenantIsolation(communityA.getId(), communityB.getId(), booking.getId(),
                "/api/v1/communities/%d/amenity-bookings/%d", HttpMethod.GET,
                mintToken(adminA.getIdentityId()), mintToken(adminB.getIdentityId()));
    }

    @Test
    void complaint_crossTenantAccess_isForbiddenAndNotFound() {
        Community communityA = data.createCommunity("Cmp-A");
        Community communityB = data.createCommunity("Cmp-B");
        CommunityMember adminA = data.createMember(communityA, CommunityRole.ADMIN, data.uniqueIdentityId());
        CommunityMember adminB = data.createMember(communityB, CommunityRole.ADMIN, data.uniqueIdentityId());
        var complaint = data.createComplaint(communityA, adminA);

        assertCrossTenantIsolation(communityA.getId(), communityB.getId(), complaint.getId(),
                "/api/v1/communities/%d/complaints/%d", HttpMethod.GET,
                mintToken(adminA.getIdentityId()), mintToken(adminB.getIdentityId()));
    }

    @Test
    void complaintComments_crossTenantAccess_isForbiddenAndNotFound() {
        Community communityA = data.createCommunity("CmpCm-A");
        Community communityB = data.createCommunity("CmpCm-B");
        CommunityMember adminA = data.createMember(communityA, CommunityRole.ADMIN, data.uniqueIdentityId());
        CommunityMember adminB = data.createMember(communityB, CommunityRole.ADMIN, data.uniqueIdentityId());
        var complaint = data.createComplaint(communityA, adminA);

        // Comments are scoped only through their parent Complaint (no direct
        // community FK on ComplaintComment itself) -- this also verifies that
        // indirection doesn't leak the boundary.
        assertCrossTenantIsolation(communityA.getId(), communityB.getId(), complaint.getId(),
                "/api/v1/communities/%d/complaints/%d/comments", HttpMethod.GET,
                mintToken(adminA.getIdentityId()), mintToken(adminB.getIdentityId()));
    }

    @Test
    void staff_crossTenantAccess_isForbiddenAndNotFound() {
        Community communityA = data.createCommunity("Stf-A");
        Community communityB = data.createCommunity("Stf-B");
        CommunityMember adminA = data.createMember(communityA, CommunityRole.ADMIN, data.uniqueIdentityId());
        CommunityMember adminB = data.createMember(communityB, CommunityRole.ADMIN, data.uniqueIdentityId());
        Staff staff = data.createStaff(communityA);

        assertCrossTenantIsolation(communityA.getId(), communityB.getId(), staff.getId(),
                "/api/v1/communities/%d/staff/%d", HttpMethod.GET,
                mintToken(adminA.getIdentityId()), mintToken(adminB.getIdentityId()));
    }

    @Test
    void staffAttendance_crossTenantAccess_isForbiddenAndNotFound() {
        Community communityA = data.createCommunity("Att-A");
        Community communityB = data.createCommunity("Att-B");
        CommunityMember adminA = data.createMember(communityA, CommunityRole.ADMIN, data.uniqueIdentityId());
        CommunityMember adminB = data.createMember(communityB, CommunityRole.ADMIN, data.uniqueIdentityId());
        Staff staff = data.createStaff(communityA);
        data.createStaffAttendance(communityA, staff, adminA);

        // Attendance is reached only via an already community-scoped staffId
        // (StaffService.requireStaff) -- the staff id is the boundary here.
        assertCrossTenantIsolation(communityA.getId(), communityB.getId(), staff.getId(),
                "/api/v1/communities/%d/staff/%d/attendance", HttpMethod.GET,
                mintToken(adminA.getIdentityId()), mintToken(adminB.getIdentityId()));
    }

    @Test
    void vendor_crossTenantAccess_isForbiddenAndNotFound() {
        Community communityA = data.createCommunity("Vnd-A");
        Community communityB = data.createCommunity("Vnd-B");
        CommunityMember adminA = data.createMember(communityA, CommunityRole.ADMIN, data.uniqueIdentityId());
        CommunityMember adminB = data.createMember(communityB, CommunityRole.ADMIN, data.uniqueIdentityId());
        var vendor = data.createVendor(communityA);

        assertCrossTenantIsolation(communityA.getId(), communityB.getId(), vendor.getId(),
                "/api/v1/communities/%d/vendors/%d", HttpMethod.GET,
                mintToken(adminA.getIdentityId()), mintToken(adminB.getIdentityId()));
    }

    @Test
    void joinRequestApproval_crossTenantAccess_isForbiddenAndNotFound() {
        Community communityA = data.createCommunity("Join-A");
        Community communityB = data.createCommunity("Join-B");
        CommunityMember adminA = data.createMember(communityA, CommunityRole.ADMIN, data.uniqueIdentityId());
        CommunityMember adminB = data.createMember(communityB, CommunityRole.ADMIN, data.uniqueIdentityId());
        var joinRequest = data.createJoinRequest(communityA);

        // POST with no body -- if the guard didn't hold, this would actually
        // approve someone else's community's join request. The legit-owner
        // check runs first and genuinely approves it there (fine, it's a
        // fresh request created just for this test); both assertions after
        // that throw before any mutation happens (guard runs first), so
        // there's no leftover side effect to worry about between them.
        assertCrossTenantIsolation(communityA.getId(), communityB.getId(), joinRequest.getId(),
                "/api/v1/communities/%d/join-requests/%d/approve", HttpMethod.POST,
                mintToken(adminA.getIdentityId()), mintToken(adminB.getIdentityId()));
    }

    @Test
    void committeeMember_crossTenantAccess_isForbiddenAndNotFound() {
        Community communityA = data.createCommunity("Cte-A");
        Community communityB = data.createCommunity("Cte-B");
        CommunityMember adminA = data.createMember(communityA, CommunityRole.ADMIN, data.uniqueIdentityId());
        CommunityMember adminB = data.createMember(communityB, CommunityRole.ADMIN, data.uniqueIdentityId());
        var committeeMember = data.createCommitteeMember(communityA, adminA);

        assertCrossTenantIsolation(communityA.getId(), communityB.getId(), committeeMember.getId(),
                "/api/v1/communities/%d/committee/%d", HttpMethod.GET,
                mintToken(adminA.getIdentityId()), mintToken(adminB.getIdentityId()));
    }

    @Test
    void document_crossTenantAccess_isForbiddenAndNotFound() {
        Community communityA = data.createCommunity("Doc-A");
        Community communityB = data.createCommunity("Doc-B");
        CommunityMember adminA = data.createMember(communityA, CommunityRole.ADMIN, data.uniqueIdentityId());
        CommunityMember adminB = data.createMember(communityB, CommunityRole.ADMIN, data.uniqueIdentityId());
        var document = data.createDocument(communityA, adminA);

        assertCrossTenantIsolation(communityA.getId(), communityB.getId(), document.getId(),
                "/api/v1/communities/%d/documents/%d", HttpMethod.GET,
                mintToken(adminA.getIdentityId()), mintToken(adminB.getIdentityId()));
    }

    // ---------- shared assertion ----------

    /**
     * Three checks for one resource: first that the legitimate owner (their
     * own community, their own resource) gets a 2xx -- proving the path
     * template actually routes to something real -- then that the same
     * non-member/non-owning admin gets 403 with the OTHER community's id
     * (flat membership rejection), and 404 with THEIR OWN community's id
     * paired with a resource id that belongs elsewhere (the compound
     * findByIdAndCommunity_Id lookup legitimately finds nothing).
     */
    private void assertCrossTenantIsolation(Long communityAId, Long communityBId, Long resourceId,
                                             String pathTemplate, HttpMethod method,
                                             String ownerToken, String attackerToken) {
        String pathOnOwnCommunity = pathTemplate.formatted(communityAId, resourceId);
        ResponseEntity<String> legitimate = exchange(pathOnOwnCommunity, method, ownerToken);
        assertThat(legitimate.getStatusCode().is2xxSuccessful())
                .as("owner should be able to reach their own resource at %s, got %s: %s",
                        pathOnOwnCommunity, legitimate.getStatusCode(), legitimate.getBody())
                .isTrue();

        String pathUsingWrongCommunity = pathTemplate.formatted(communityAId, resourceId);
        ResponseEntity<String> notAMember = exchange(pathUsingWrongCommunity, method, attackerToken);
        assertThat(notAMember.getStatusCode())
                .as("caller is not a member of community %d at all", communityAId)
                .isEqualTo(HttpStatus.FORBIDDEN);

        String pathUsingOwnCommunityMismatchedResource = pathTemplate.formatted(communityBId, resourceId);
        ResponseEntity<String> mismatched = exchange(pathUsingOwnCommunityMismatchedResource, method, attackerToken);
        assertThat(mismatched.getStatusCode())
                .as("resource %d does not belong to caller's own community %d", resourceId, communityBId)
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(baseUrl(path), method, new HttpEntity<>(headers), String.class);
    }
}
