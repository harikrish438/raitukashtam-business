package com.raitukashtam.mycommunity.support;

import com.raitukashtam.mycommunity.entity.Amenity;
import com.raitukashtam.mycommunity.entity.AmenityBooking;
import com.raitukashtam.mycommunity.entity.AmenityBookingStatus;
import com.raitukashtam.mycommunity.entity.Announcement;
import com.raitukashtam.mycommunity.entity.AttendanceStatus;
import com.raitukashtam.mycommunity.entity.Bill;
import com.raitukashtam.mycommunity.entity.BillStatus;
import com.raitukashtam.mycommunity.entity.CommitteeMember;
import com.raitukashtam.mycommunity.entity.CommitteePosition;
import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityDocument;
import com.raitukashtam.mycommunity.entity.CommunityJoinRequest;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.Complaint;
import com.raitukashtam.mycommunity.entity.ComplaintPriority;
import com.raitukashtam.mycommunity.entity.ComplaintStatus;
import com.raitukashtam.mycommunity.entity.DocumentVisibility;
import com.raitukashtam.mycommunity.entity.Expense;
import com.raitukashtam.mycommunity.entity.JoinRequestStatus;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.entity.PaymentMethod;
import com.raitukashtam.mycommunity.entity.Payment;
import com.raitukashtam.mycommunity.entity.Staff;
import com.raitukashtam.mycommunity.entity.StaffAttendance;
import com.raitukashtam.mycommunity.entity.StaffRole;
import com.raitukashtam.mycommunity.entity.Unit;
import com.raitukashtam.mycommunity.entity.Vendor;
import com.raitukashtam.mycommunity.entity.Visitor;
import com.raitukashtam.mycommunity.entity.VisitorStatus;
import com.raitukashtam.mycommunity.entity.VisitorType;
import com.raitukashtam.mycommunity.repository.AmenityBookingRepository;
import com.raitukashtam.mycommunity.repository.AmenityRepository;
import com.raitukashtam.mycommunity.repository.AnnouncementRepository;
import com.raitukashtam.mycommunity.repository.BillRepository;
import com.raitukashtam.mycommunity.repository.CommitteeMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityDocumentRepository;
import com.raitukashtam.mycommunity.repository.CommunityJoinRequestRepository;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.repository.ComplaintRepository;
import com.raitukashtam.mycommunity.repository.ExpenseRepository;
import com.raitukashtam.mycommunity.repository.PaymentRepository;
import com.raitukashtam.mycommunity.repository.StaffAttendanceRepository;
import com.raitukashtam.mycommunity.repository.StaffRepository;
import com.raitukashtam.mycommunity.repository.UnitRepository;
import com.raitukashtam.mycommunity.repository.VendorRepository;
import com.raitukashtam.mycommunity.repository.VisitorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test-only support bean (lives under src/test but in the same base
 * package as the application, so component-scan in @SpringBootTest picks
 * it up -- same convention as auth-service's own TestDataFactory).
 * Everything here is seeded directly via repositories rather than through
 * the real create-endpoints/services: this suite's purpose is to verify
 * the cross-tenant AUTHORIZATION boundary (requireActiveAdmin/
 * requireActiveMember + findByIdAndCommunity_Id), not to re-exercise each
 * resource's own creation business logic, and going through the real
 * createCommunity/createJoinRequest flows would need a working
 * AuthServiceClient call to auth-service for no benefit to what's being
 * tested here.
 */
@Component
public class TestDataFactory {

    private static final AtomicLong SEQ = new AtomicLong(1);

    @Autowired private CommunityRepository communityRepository;
    @Autowired private CommunityMemberRepository communityMemberRepository;
    @Autowired private UnitRepository unitRepository;
    @Autowired private AnnouncementRepository announcementRepository;
    @Autowired private BillRepository billRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private VisitorRepository visitorRepository;
    @Autowired private AmenityRepository amenityRepository;
    @Autowired private AmenityBookingRepository amenityBookingRepository;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private StaffAttendanceRepository staffAttendanceRepository;
    @Autowired private VendorRepository vendorRepository;
    @Autowired private CommunityJoinRequestRepository joinRequestRepository;
    @Autowired private CommitteeMemberRepository committeeMemberRepository;
    @Autowired private CommunityDocumentRepository documentRepository;

    public String uniqueIdentityId() {
        return UUID.randomUUID().toString();
    }

    private String uniqueMobile() {
        return "9" + String.format("%09d", SEQ.incrementAndGet() % 1_000_000_000L);
    }

    public Community createCommunity(String namePrefix) {
        Community community = new Community();
        community.setName(namePrefix + "-" + SEQ.incrementAndGet());
        community.setTotalUnits(10);
        community.setStreet("Test Street");
        community.setArea("Test Area");
        community.setDistrict("Test District");
        community.setState("Test State");
        community.setPincode("500001");
        return communityRepository.save(community);
    }

    public CommunityMember createMember(Community community, CommunityRole role, String identityId) {
        CommunityMember member = new CommunityMember();
        member.setCommunity(community);
        member.setName("Test " + role + " " + SEQ.incrementAndGet());
        member.setUnitNumber("A-1");
        member.setMobileNumber(uniqueMobile());
        member.setRole(role);
        member.setStatus(MemberStatus.ACTIVE);
        member.setIdentityId(identityId);
        return communityMemberRepository.save(member);
    }

    public Unit createUnit(Community community) {
        Unit unit = new Unit();
        unit.setCommunity(community);
        unit.setUnitNumber("U-" + SEQ.incrementAndGet());
        unit.setActive(true);
        return unitRepository.save(unit);
    }

    public Announcement createAnnouncement(Community community, CommunityMember postedBy) {
        Announcement announcement = new Announcement();
        announcement.setCommunity(community);
        announcement.setTitle("Test announcement");
        announcement.setBody("Test body");
        announcement.setPostedBy(postedBy);
        return announcementRepository.save(announcement);
    }

    public Bill createBill(Community community, CommunityMember member) {
        Bill bill = new Bill();
        bill.setCommunity(community);
        bill.setMember(member);
        bill.setPeriod("2026-0" + (1 + SEQ.incrementAndGet() % 9));
        bill.setAmount(new BigDecimal("1000.00"));
        bill.setStatus(BillStatus.PENDING);
        bill.setDueDate(LocalDate.now().plusDays(15));
        return billRepository.save(bill);
    }

    public Payment createPayment(Community community, Bill bill, CommunityMember recordedBy) {
        Payment payment = new Payment();
        payment.setCommunity(community);
        payment.setBill(bill);
        payment.setAmount(bill.getAmount());
        payment.setMethod(PaymentMethod.CASH);
        payment.setPaidAt(LocalDateTime.now());
        payment.setRecordedBy(recordedBy);
        return paymentRepository.save(payment);
    }

    public Expense createExpense(Community community, CommunityMember createdBy) {
        Expense expense = new Expense();
        expense.setCommunity(community);
        expense.setCategory("Maintenance");
        expense.setDescription("Test expense");
        expense.setAmount(new BigDecimal("500.00"));
        expense.setExpenseDate(LocalDate.now());
        expense.setCreatedByMember(createdBy);
        return expenseRepository.save(expense);
    }

    public Visitor createVisitor(Community community, CommunityMember host) {
        Visitor visitor = new Visitor();
        visitor.setCommunity(community);
        visitor.setHost(host);
        visitor.setGuestName("Test Guest");
        visitor.setType(VisitorType.GUEST);
        visitor.setStatus(VisitorStatus.EXPECTED);
        return visitorRepository.save(visitor);
    }

    public Amenity createAmenity(Community community) {
        Amenity amenity = new Amenity();
        amenity.setCommunity(community);
        amenity.setName("Clubhouse " + SEQ.incrementAndGet());
        amenity.setPaid(false);
        amenity.setActive(true);
        return amenityRepository.save(amenity);
    }

    public AmenityBooking createAmenityBooking(Community community, Amenity amenity, CommunityMember member) {
        AmenityBooking booking = new AmenityBooking();
        booking.setCommunity(community);
        booking.setAmenity(amenity);
        booking.setMember(member);
        booking.setBookingDate(LocalDate.now().plusDays(1));
        booking.setSlot("10:00-11:00");
        booking.setStatus(AmenityBookingStatus.PENDING);
        return amenityBookingRepository.save(booking);
    }

    public Complaint createComplaint(Community community, CommunityMember raisedBy) {
        Complaint complaint = new Complaint();
        complaint.setCommunity(community);
        complaint.setRaisedBy(raisedBy);
        complaint.setCategory("Plumbing");
        complaint.setTitle("Test complaint");
        complaint.setDescription("Test description");
        complaint.setPriority(ComplaintPriority.MEDIUM);
        complaint.setStatus(ComplaintStatus.OPEN);
        return complaintRepository.save(complaint);
    }

    public Staff createStaff(Community community) {
        Staff staff = new Staff();
        staff.setCommunity(community);
        staff.setName("Test Staff " + SEQ.incrementAndGet());
        staff.setRole(StaffRole.SECURITY);
        staff.setActive(true);
        return staffRepository.save(staff);
    }

    public StaffAttendance createStaffAttendance(Community community, Staff staff, CommunityMember markedBy) {
        StaffAttendance attendance = new StaffAttendance();
        attendance.setCommunity(community);
        attendance.setStaff(staff);
        attendance.setAttendanceDate(LocalDate.now());
        attendance.setStatus(AttendanceStatus.PRESENT);
        attendance.setMarkedBy(markedBy);
        return staffAttendanceRepository.save(attendance);
    }

    public Vendor createVendor(Community community) {
        Vendor vendor = new Vendor();
        vendor.setCommunity(community);
        vendor.setName("Test Vendor " + SEQ.incrementAndGet());
        vendor.setServiceType("Plumbing");
        vendor.setActive(true);
        return vendorRepository.save(vendor);
    }

    public CommunityJoinRequest createJoinRequest(Community community) {
        CommunityJoinRequest request = new CommunityJoinRequest();
        request.setCommunity(community);
        request.setRequesterIdentityId(uniqueIdentityId());
        request.setRequesterMobileNumber(uniqueMobile());
        request.setRequesterName("Test Requester");
        request.setStatus(JoinRequestStatus.PENDING);
        return joinRequestRepository.save(request);
    }

    public CommitteeMember createCommitteeMember(Community community, CommunityMember member) {
        CommitteeMember committeeMember = new CommitteeMember();
        committeeMember.setCommunity(community);
        committeeMember.setMember(member);
        committeeMember.setPosition(CommitteePosition.PRESIDENT);
        committeeMember.setTermStart(LocalDate.now().minusMonths(1));
        return committeeMemberRepository.save(committeeMember);
    }

    public CommunityDocument createDocument(Community community, CommunityMember uploadedBy) {
        CommunityDocument document = new CommunityDocument();
        document.setCommunity(community);
        document.setTitle("Test document");
        document.setCategory("General");
        document.setVisibility(DocumentVisibility.ALL_MEMBERS);
        document.setS3Key("test/key-" + SEQ.incrementAndGet());
        document.setContentType("application/pdf");
        document.setFileSizeBytes(1024L);
        document.setUploadedBy(uploadedBy);
        return documentRepository.save(document);
    }
}
