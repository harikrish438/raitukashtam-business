package com.raitukashtam.mycommunity.controller;

import com.raitukashtam.mycommunity.request.AmenityBookingRequest;
import com.raitukashtam.mycommunity.request.AmenityRequest;
import com.raitukashtam.mycommunity.request.AnnouncementRequest;
import com.raitukashtam.mycommunity.request.AssignComplaintRequest;
import com.raitukashtam.mycommunity.request.CommunityMemberRequest;
import com.raitukashtam.mycommunity.request.CommunityRequest;
import com.raitukashtam.mycommunity.request.ComplaintCommentRequest;
import com.raitukashtam.mycommunity.request.ComplaintRequest;
import com.raitukashtam.mycommunity.request.ComplaintStatusRequest;
import com.raitukashtam.mycommunity.request.CreateVisitorRequest;
import com.raitukashtam.mycommunity.request.ExpenseRequest;
import com.raitukashtam.mycommunity.request.GenerateBillsRequest;
import com.raitukashtam.mycommunity.request.JoinRequestRequest;
import com.raitukashtam.mycommunity.request.MarkAttendanceRequest;
import com.raitukashtam.mycommunity.request.MemberProfileUpdateRequest;
import com.raitukashtam.mycommunity.request.RecordPaymentRequest;
import com.raitukashtam.mycommunity.request.StaffRequest;
import com.raitukashtam.mycommunity.request.VendorRequest;
import com.raitukashtam.mycommunity.response.AmenityBookingResponse;
import com.raitukashtam.mycommunity.response.AmenityResponse;
import com.raitukashtam.mycommunity.response.AnnouncementResponse;
import com.raitukashtam.mycommunity.response.BillResponse;
import com.raitukashtam.mycommunity.response.CommunityMemberResponse;
import com.raitukashtam.mycommunity.response.CommunityResponse;
import com.raitukashtam.mycommunity.response.ComplaintCommentResponse;
import com.raitukashtam.mycommunity.response.ComplaintResponse;
import com.raitukashtam.mycommunity.response.DashboardResponse;
import com.raitukashtam.mycommunity.response.ExpenseResponse;
import com.raitukashtam.mycommunity.response.JoinRequestResponse;
import com.raitukashtam.mycommunity.response.MyCommunityResponse;
import com.raitukashtam.mycommunity.response.PaymentResponse;
import com.raitukashtam.mycommunity.response.StaffAttendanceResponse;
import com.raitukashtam.mycommunity.response.StaffResponse;
import com.raitukashtam.mycommunity.response.VendorResponse;
import com.raitukashtam.mycommunity.response.VisitorResponse;
import com.raitukashtam.mycommunity.service.AmenityBookingService;
import com.raitukashtam.mycommunity.service.AmenityService;
import com.raitukashtam.mycommunity.service.AnnouncementService;
import com.raitukashtam.mycommunity.service.BillService;
import com.raitukashtam.mycommunity.service.CommunityJoinRequestService;
import com.raitukashtam.mycommunity.service.CommunityService;
import com.raitukashtam.mycommunity.service.ComplaintCommentService;
import com.raitukashtam.mycommunity.service.ComplaintService;
import com.raitukashtam.mycommunity.service.DashboardService;
import com.raitukashtam.mycommunity.service.ExpenseService;
import com.raitukashtam.mycommunity.service.PaymentService;
import com.raitukashtam.mycommunity.service.StaffAttendanceService;
import com.raitukashtam.mycommunity.service.StaffService;
import com.raitukashtam.mycommunity.service.VendorService;
import com.raitukashtam.mycommunity.service.VisitorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/communities")
@Slf4j
public class CommunityController {
    @Autowired
    private CommunityService communityService;

    @Autowired
    private CommunityJoinRequestService joinRequestService;

    @Autowired
    private AnnouncementService announcementService;

    @Autowired
    private BillService billService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private VisitorService visitorService;

    @Autowired
    private AmenityService amenityService;

    @Autowired
    private AmenityBookingService amenityBookingService;

    @Autowired
    private ComplaintService complaintService;

    @Autowired
    private ComplaintCommentService complaintCommentService;

    @Autowired
    private StaffService staffService;

    @Autowired
    private StaffAttendanceService staffAttendanceService;

    @Autowired
    private VendorService vendorService;

    @PostMapping
    public ResponseEntity<CommunityResponse> createCommunity(@RequestBody @Validated CommunityRequest request,
                                                               @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside createCommunity method of CommunityController");
        return new ResponseEntity<>(
                communityService.createCommunity(request, jwt.getSubject(), jwt.getTokenValue()), HttpStatus.CREATED);
    }

    @GetMapping("/mine")
    public List<MyCommunityResponse> listMyCommunities(@AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listMyCommunities method of CommunityController");
        return communityService.listMyCommunities(jwt.getSubject());
    }

    @PostMapping("/members/activate-invitations")
    public List<MyCommunityResponse> activateInvitations(@AuthenticationPrincipal Jwt jwt) {
        log.info("Inside activateInvitations method of CommunityController");
        return communityService.activateInvitations(jwt.getSubject(), jwt.getTokenValue());
    }

    @GetMapping("/{communityId}")
    public CommunityResponse getCommunity(@PathVariable("communityId") Long communityId,
                                           @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside getCommunity method of CommunityController");
        return communityService.getCommunity(communityId, jwt.getSubject());
    }

    @PostMapping("/{communityId}/members")
    public ResponseEntity<CommunityMemberResponse> addMember(@PathVariable("communityId") Long communityId,
                                                              @RequestBody @Validated CommunityMemberRequest request,
                                                              @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside addMember method of CommunityController");
        return new ResponseEntity<>(communityService.addMember(communityId, request, jwt.getSubject()), HttpStatus.CREATED);
    }

    @GetMapping("/{communityId}/members")
    public List<CommunityMemberResponse> listMembers(@PathVariable("communityId") Long communityId,
                                                       @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listMembers method of CommunityController");
        return communityService.listMembers(communityId, jwt.getSubject());
    }

    @PatchMapping("/{communityId}/members/me")
    public CommunityMemberResponse updateMyProfile(@PathVariable("communityId") Long communityId,
                                                     @RequestBody @Validated MemberProfileUpdateRequest request,
                                                     @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside updateMyProfile method of CommunityController");
        return communityService.updateMyProfile(communityId, request, jwt.getSubject());
    }

    @DeleteMapping("/{communityId}/members/{memberId}")
    public ResponseEntity<Void> removeMember(@PathVariable("communityId") Long communityId,
                                              @PathVariable("memberId") Long memberId,
                                              @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside removeMember method of CommunityController");
        communityService.removeMember(communityId, memberId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{communityId}/join-requests")
    public ResponseEntity<JoinRequestResponse> createJoinRequest(@PathVariable("communityId") Long communityId,
                                                                   @RequestBody @Validated JoinRequestRequest request,
                                                                   @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside createJoinRequest method of CommunityController");
        return new ResponseEntity<>(
                joinRequestService.createJoinRequest(communityId, request, jwt.getSubject(), jwt.getTokenValue()), HttpStatus.CREATED);
    }

    @GetMapping("/{communityId}/join-requests")
    public List<JoinRequestResponse> listJoinRequests(@PathVariable("communityId") Long communityId,
                                                        @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listJoinRequests method of CommunityController");
        return joinRequestService.listPendingJoinRequests(communityId, jwt.getSubject());
    }

    @PostMapping("/{communityId}/join-requests/{requestId}/approve")
    public CommunityMemberResponse approveJoinRequest(@PathVariable("communityId") Long communityId,
                                                        @PathVariable("requestId") Long requestId,
                                                        @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside approveJoinRequest method of CommunityController");
        return joinRequestService.approveJoinRequest(communityId, requestId, jwt.getSubject());
    }

    @PostMapping("/{communityId}/join-requests/{requestId}/reject")
    public ResponseEntity<Void> rejectJoinRequest(@PathVariable("communityId") Long communityId,
                                                    @PathVariable("requestId") Long requestId,
                                                    @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside rejectJoinRequest method of CommunityController");
        joinRequestService.rejectJoinRequest(communityId, requestId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{communityId}/announcements")
    public ResponseEntity<AnnouncementResponse> createAnnouncement(@PathVariable("communityId") Long communityId,
                                                                      @RequestBody @Validated AnnouncementRequest request,
                                                                      @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside createAnnouncement method of CommunityController");
        return new ResponseEntity<>(
                announcementService.createAnnouncement(communityId, request, jwt.getSubject()), HttpStatus.CREATED);
    }

    @GetMapping("/{communityId}/announcements")
    public List<AnnouncementResponse> listAnnouncements(@PathVariable("communityId") Long communityId,
                                                          @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listAnnouncements method of CommunityController");
        return announcementService.listAnnouncements(communityId, jwt.getSubject());
    }

    @GetMapping("/{communityId}/announcements/{announcementId}")
    public AnnouncementResponse getAnnouncement(@PathVariable("communityId") Long communityId,
                                                 @PathVariable("announcementId") Long announcementId,
                                                 @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside getAnnouncement method of CommunityController");
        return announcementService.getAnnouncement(communityId, announcementId, jwt.getSubject());
    }

    @DeleteMapping("/{communityId}/announcements/{announcementId}")
    public ResponseEntity<Void> deleteAnnouncement(@PathVariable("communityId") Long communityId,
                                                    @PathVariable("announcementId") Long announcementId,
                                                    @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside deleteAnnouncement method of CommunityController");
        announcementService.deleteAnnouncement(communityId, announcementId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{communityId}/bills/generate")
    public ResponseEntity<List<BillResponse>> generateBills(@PathVariable("communityId") Long communityId,
                                                              @RequestBody @Validated GenerateBillsRequest request,
                                                              @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside generateBills method of CommunityController");
        return new ResponseEntity<>(billService.generateBills(communityId, request, jwt.getSubject()), HttpStatus.CREATED);
    }

    @GetMapping("/{communityId}/bills")
    public List<BillResponse> listBills(@PathVariable("communityId") Long communityId,
                                         @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listBills method of CommunityController");
        return billService.listBills(communityId, jwt.getSubject());
    }

    @GetMapping("/{communityId}/bills/mine")
    public List<BillResponse> listMyBills(@PathVariable("communityId") Long communityId,
                                           @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listMyBills method of CommunityController");
        return billService.listMyBills(communityId, jwt.getSubject());
    }

    @GetMapping("/{communityId}/bills/{billId}")
    public BillResponse getBill(@PathVariable("communityId") Long communityId,
                                 @PathVariable("billId") Long billId,
                                 @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside getBill method of CommunityController");
        return billService.getBill(communityId, billId, jwt.getSubject());
    }

    @PostMapping("/{communityId}/bills/{billId}/payments")
    public ResponseEntity<PaymentResponse> recordPayment(@PathVariable("communityId") Long communityId,
                                                           @PathVariable("billId") Long billId,
                                                           @RequestBody @Validated RecordPaymentRequest request,
                                                           @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside recordPayment method of CommunityController");
        return new ResponseEntity<>(
                paymentService.recordPayment(communityId, billId, request, jwt.getSubject()), HttpStatus.CREATED);
    }

    @GetMapping("/{communityId}/bills/{billId}/payment")
    public PaymentResponse getPaymentForBill(@PathVariable("communityId") Long communityId,
                                              @PathVariable("billId") Long billId,
                                              @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside getPaymentForBill method of CommunityController");
        return paymentService.getPaymentForBill(communityId, billId, jwt.getSubject());
    }

    @GetMapping("/{communityId}/payments")
    public List<PaymentResponse> listPayments(@PathVariable("communityId") Long communityId,
                                               @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listPayments method of CommunityController");
        return paymentService.listPayments(communityId, jwt.getSubject());
    }

    @GetMapping("/{communityId}/payments/mine")
    public List<PaymentResponse> listMyPayments(@PathVariable("communityId") Long communityId,
                                                 @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listMyPayments method of CommunityController");
        return paymentService.listMyPayments(communityId, jwt.getSubject());
    }

    @PostMapping("/{communityId}/expenses")
    public ResponseEntity<ExpenseResponse> createExpense(@PathVariable("communityId") Long communityId,
                                                           @RequestBody @Validated ExpenseRequest request,
                                                           @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside createExpense method of CommunityController");
        return new ResponseEntity<>(expenseService.createExpense(communityId, request, jwt.getSubject()), HttpStatus.CREATED);
    }

    @GetMapping("/{communityId}/expenses")
    public List<ExpenseResponse> listExpenses(@PathVariable("communityId") Long communityId,
                                               @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listExpenses method of CommunityController");
        return expenseService.listExpenses(communityId, jwt.getSubject());
    }

    @GetMapping("/{communityId}/expenses/{expenseId}")
    public ExpenseResponse getExpense(@PathVariable("communityId") Long communityId,
                                       @PathVariable("expenseId") Long expenseId,
                                       @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside getExpense method of CommunityController");
        return expenseService.getExpense(communityId, expenseId, jwt.getSubject());
    }

    @DeleteMapping("/{communityId}/expenses/{expenseId}")
    public ResponseEntity<Void> deleteExpense(@PathVariable("communityId") Long communityId,
                                               @PathVariable("expenseId") Long expenseId,
                                               @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside deleteExpense method of CommunityController");
        expenseService.deleteExpense(communityId, expenseId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{communityId}/dashboard")
    public DashboardResponse getDashboard(@PathVariable("communityId") Long communityId,
                                           @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside getDashboard method of CommunityController");
        return dashboardService.getDashboard(communityId, jwt.getSubject());
    }

    @PostMapping("/{communityId}/visitors")
    public ResponseEntity<VisitorResponse> createVisitor(@PathVariable("communityId") Long communityId,
                                                           @RequestBody @Validated CreateVisitorRequest request,
                                                           @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside createVisitor method of CommunityController");
        return new ResponseEntity<>(visitorService.createVisitor(communityId, request, jwt.getSubject()), HttpStatus.CREATED);
    }

    @GetMapping("/{communityId}/visitors")
    public List<VisitorResponse> listVisitors(@PathVariable("communityId") Long communityId,
                                               @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listVisitors method of CommunityController");
        return visitorService.listVisitors(communityId, jwt.getSubject());
    }

    @GetMapping("/{communityId}/visitors/mine")
    public List<VisitorResponse> listMyVisitors(@PathVariable("communityId") Long communityId,
                                                 @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listMyVisitors method of CommunityController");
        return visitorService.listMyVisitors(communityId, jwt.getSubject());
    }

    @GetMapping("/{communityId}/visitors/{visitorId}")
    public VisitorResponse getVisitor(@PathVariable("communityId") Long communityId,
                                       @PathVariable("visitorId") Long visitorId,
                                       @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside getVisitor method of CommunityController");
        return visitorService.getVisitor(communityId, visitorId, jwt.getSubject());
    }

    @PostMapping("/{communityId}/visitors/{visitorId}/check-in")
    public VisitorResponse checkIn(@PathVariable("communityId") Long communityId,
                                    @PathVariable("visitorId") Long visitorId,
                                    @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside checkIn method of CommunityController");
        return visitorService.checkIn(communityId, visitorId, jwt.getSubject());
    }

    @PostMapping("/{communityId}/visitors/{visitorId}/check-out")
    public VisitorResponse checkOut(@PathVariable("communityId") Long communityId,
                                     @PathVariable("visitorId") Long visitorId,
                                     @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside checkOut method of CommunityController");
        return visitorService.checkOut(communityId, visitorId, jwt.getSubject());
    }

    @PostMapping("/{communityId}/amenities")
    public ResponseEntity<AmenityResponse> createAmenity(@PathVariable("communityId") Long communityId,
                                                           @RequestBody @Validated AmenityRequest request,
                                                           @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside createAmenity method of CommunityController");
        return new ResponseEntity<>(amenityService.createAmenity(communityId, request, jwt.getSubject()), HttpStatus.CREATED);
    }

    @GetMapping("/{communityId}/amenities")
    public List<AmenityResponse> listAmenities(@PathVariable("communityId") Long communityId,
                                                @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listAmenities method of CommunityController");
        return amenityService.listAmenities(communityId, jwt.getSubject());
    }

    @GetMapping("/{communityId}/amenities/{amenityId}")
    public AmenityResponse getAmenity(@PathVariable("communityId") Long communityId,
                                       @PathVariable("amenityId") Long amenityId,
                                       @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside getAmenity method of CommunityController");
        return amenityService.getAmenity(communityId, amenityId, jwt.getSubject());
    }

    @PatchMapping("/{communityId}/amenities/{amenityId}/deactivate")
    public AmenityResponse deactivateAmenity(@PathVariable("communityId") Long communityId,
                                              @PathVariable("amenityId") Long amenityId,
                                              @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside deactivateAmenity method of CommunityController");
        return amenityService.deactivateAmenity(communityId, amenityId, jwt.getSubject());
    }

    @PostMapping("/{communityId}/amenities/{amenityId}/bookings")
    public ResponseEntity<AmenityBookingResponse> createBooking(@PathVariable("communityId") Long communityId,
                                                                  @PathVariable("amenityId") Long amenityId,
                                                                  @RequestBody @Validated AmenityBookingRequest request,
                                                                  @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside createBooking method of CommunityController");
        return new ResponseEntity<>(
                amenityBookingService.createBooking(communityId, amenityId, request, jwt.getSubject()), HttpStatus.CREATED);
    }

    @GetMapping("/{communityId}/amenity-bookings")
    public List<AmenityBookingResponse> listBookings(@PathVariable("communityId") Long communityId,
                                                       @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listBookings method of CommunityController");
        return amenityBookingService.listBookings(communityId, jwt.getSubject());
    }

    @GetMapping("/{communityId}/amenity-bookings/mine")
    public List<AmenityBookingResponse> listMyBookings(@PathVariable("communityId") Long communityId,
                                                         @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listMyBookings method of CommunityController");
        return amenityBookingService.listMyBookings(communityId, jwt.getSubject());
    }

    @GetMapping("/{communityId}/amenity-bookings/{bookingId}")
    public AmenityBookingResponse getBooking(@PathVariable("communityId") Long communityId,
                                              @PathVariable("bookingId") Long bookingId,
                                              @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside getBooking method of CommunityController");
        return amenityBookingService.getBooking(communityId, bookingId, jwt.getSubject());
    }

    @PostMapping("/{communityId}/amenity-bookings/{bookingId}/approve")
    public AmenityBookingResponse approveBooking(@PathVariable("communityId") Long communityId,
                                                  @PathVariable("bookingId") Long bookingId,
                                                  @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside approveBooking method of CommunityController");
        return amenityBookingService.approveBooking(communityId, bookingId, jwt.getSubject());
    }

    @PostMapping("/{communityId}/amenity-bookings/{bookingId}/reject")
    public AmenityBookingResponse rejectBooking(@PathVariable("communityId") Long communityId,
                                                 @PathVariable("bookingId") Long bookingId,
                                                 @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside rejectBooking method of CommunityController");
        return amenityBookingService.rejectBooking(communityId, bookingId, jwt.getSubject());
    }

    @PostMapping("/{communityId}/amenity-bookings/{bookingId}/cancel")
    public AmenityBookingResponse cancelBooking(@PathVariable("communityId") Long communityId,
                                                 @PathVariable("bookingId") Long bookingId,
                                                 @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside cancelBooking method of CommunityController");
        return amenityBookingService.cancelBooking(communityId, bookingId, jwt.getSubject());
    }

    @PostMapping("/{communityId}/complaints")
    public ResponseEntity<ComplaintResponse> createComplaint(@PathVariable("communityId") Long communityId,
                                                               @RequestBody @Validated ComplaintRequest request,
                                                               @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside createComplaint method of CommunityController");
        return new ResponseEntity<>(complaintService.createComplaint(communityId, request, jwt.getSubject()), HttpStatus.CREATED);
    }

    @GetMapping("/{communityId}/complaints")
    public List<ComplaintResponse> listComplaints(@PathVariable("communityId") Long communityId,
                                                   @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listComplaints method of CommunityController");
        return complaintService.listComplaints(communityId, jwt.getSubject());
    }

    @GetMapping("/{communityId}/complaints/mine")
    public List<ComplaintResponse> listMyComplaints(@PathVariable("communityId") Long communityId,
                                                     @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listMyComplaints method of CommunityController");
        return complaintService.listMyComplaints(communityId, jwt.getSubject());
    }

    @GetMapping("/{communityId}/complaints/{complaintId}")
    public ComplaintResponse getComplaint(@PathVariable("communityId") Long communityId,
                                           @PathVariable("complaintId") Long complaintId,
                                           @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside getComplaint method of CommunityController");
        return complaintService.getComplaint(communityId, complaintId, jwt.getSubject());
    }

    @PatchMapping("/{communityId}/complaints/{complaintId}/assign")
    public ComplaintResponse assignComplaint(@PathVariable("communityId") Long communityId,
                                              @PathVariable("complaintId") Long complaintId,
                                              @RequestBody @Validated AssignComplaintRequest request,
                                              @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside assignComplaint method of CommunityController");
        return complaintService.assignComplaint(communityId, complaintId, request, jwt.getSubject());
    }

    @PatchMapping("/{communityId}/complaints/{complaintId}/status")
    public ComplaintResponse updateComplaintStatus(@PathVariable("communityId") Long communityId,
                                                    @PathVariable("complaintId") Long complaintId,
                                                    @RequestBody @Validated ComplaintStatusRequest request,
                                                    @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside updateComplaintStatus method of CommunityController");
        return complaintService.updateStatus(communityId, complaintId, request, jwt.getSubject());
    }

    @PostMapping("/{communityId}/complaints/{complaintId}/comments")
    public ResponseEntity<ComplaintCommentResponse> addComplaintComment(@PathVariable("communityId") Long communityId,
                                                                          @PathVariable("complaintId") Long complaintId,
                                                                          @RequestBody @Validated ComplaintCommentRequest request,
                                                                          @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside addComplaintComment method of CommunityController");
        return new ResponseEntity<>(
                complaintCommentService.addComment(communityId, complaintId, request, jwt.getSubject()), HttpStatus.CREATED);
    }

    @GetMapping("/{communityId}/complaints/{complaintId}/comments")
    public List<ComplaintCommentResponse> listComplaintComments(@PathVariable("communityId") Long communityId,
                                                                  @PathVariable("complaintId") Long complaintId,
                                                                  @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listComplaintComments method of CommunityController");
        return complaintCommentService.listComments(communityId, complaintId, jwt.getSubject());
    }

    @PostMapping("/{communityId}/staff")
    public ResponseEntity<StaffResponse> createStaff(@PathVariable("communityId") Long communityId,
                                                       @RequestBody @Validated StaffRequest request,
                                                       @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside createStaff method of CommunityController");
        return new ResponseEntity<>(staffService.createStaff(communityId, request, jwt.getSubject()), HttpStatus.CREATED);
    }

    @GetMapping("/{communityId}/staff")
    public List<StaffResponse> listStaff(@PathVariable("communityId") Long communityId,
                                          @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listStaff method of CommunityController");
        return staffService.listStaff(communityId, jwt.getSubject());
    }

    @GetMapping("/{communityId}/staff/{staffId}")
    public StaffResponse getStaff(@PathVariable("communityId") Long communityId,
                                   @PathVariable("staffId") Long staffId,
                                   @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside getStaff method of CommunityController");
        return staffService.getStaff(communityId, staffId, jwt.getSubject());
    }

    @PatchMapping("/{communityId}/staff/{staffId}/deactivate")
    public StaffResponse deactivateStaff(@PathVariable("communityId") Long communityId,
                                          @PathVariable("staffId") Long staffId,
                                          @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside deactivateStaff method of CommunityController");
        return staffService.deactivateStaff(communityId, staffId, jwt.getSubject());
    }

    @PostMapping("/{communityId}/staff/{staffId}/attendance")
    public StaffAttendanceResponse markAttendance(@PathVariable("communityId") Long communityId,
                                                   @PathVariable("staffId") Long staffId,
                                                   @RequestBody @Validated MarkAttendanceRequest request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside markAttendance method of CommunityController");
        return staffAttendanceService.markAttendance(communityId, staffId, request, jwt.getSubject());
    }

    @GetMapping("/{communityId}/staff/{staffId}/attendance")
    public List<StaffAttendanceResponse> listAttendance(@PathVariable("communityId") Long communityId,
                                                          @PathVariable("staffId") Long staffId,
                                                          @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listAttendance method of CommunityController");
        return staffAttendanceService.listAttendance(communityId, staffId, jwt.getSubject());
    }

    @PostMapping("/{communityId}/vendors")
    public ResponseEntity<VendorResponse> createVendor(@PathVariable("communityId") Long communityId,
                                                         @RequestBody @Validated VendorRequest request,
                                                         @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside createVendor method of CommunityController");
        return new ResponseEntity<>(vendorService.createVendor(communityId, request, jwt.getSubject()), HttpStatus.CREATED);
    }

    @GetMapping("/{communityId}/vendors")
    public List<VendorResponse> listVendors(@PathVariable("communityId") Long communityId,
                                             @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listVendors method of CommunityController");
        return vendorService.listVendors(communityId, jwt.getSubject());
    }

    @GetMapping("/{communityId}/vendors/{vendorId}")
    public VendorResponse getVendor(@PathVariable("communityId") Long communityId,
                                     @PathVariable("vendorId") Long vendorId,
                                     @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside getVendor method of CommunityController");
        return vendorService.getVendor(communityId, vendorId, jwt.getSubject());
    }

    @PatchMapping("/{communityId}/vendors/{vendorId}/deactivate")
    public VendorResponse deactivateVendor(@PathVariable("communityId") Long communityId,
                                            @PathVariable("vendorId") Long vendorId,
                                            @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside deactivateVendor method of CommunityController");
        return vendorService.deactivateVendor(communityId, vendorId, jwt.getSubject());
    }

    @GetMapping("/{communityId}/vendors/{vendorId}/expenses")
    public List<ExpenseResponse> listExpensesForVendor(@PathVariable("communityId") Long communityId,
                                                         @PathVariable("vendorId") Long vendorId,
                                                         @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listExpensesForVendor method of CommunityController");
        return expenseService.listExpensesForVendor(communityId, vendorId, jwt.getSubject());
    }
}
