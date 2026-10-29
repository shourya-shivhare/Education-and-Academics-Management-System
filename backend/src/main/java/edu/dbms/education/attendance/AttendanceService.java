package edu.dbms.education.attendance;

import edu.dbms.education.auth.AuthenticatedUser;
import edu.dbms.education.common.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository repo;

    /**
     * FACULTY ownership check: the requesting faculty must be the instructor
     * for the offering that contains the enrollment.
     *
     * DBMS concept: we query the offering's faculty_id and compare it to
     * the JWT personId.  No role-based table-level restriction in MySQL
     * can enforce this cross-row check, so it lives here in the service.
     */
    @Transactional
    public AttendanceDto create(RecordAttendanceRequest req) {
        AuthenticatedUser u = AuthenticatedUser.current();
        if ("Faculty".equals(u.getRole())) {
            int offeringFacultyId = repo
                    .findOfferingFacultyByEnrollmentId(req.getEnrollmentId())
                    .orElseThrow(() -> new NoSuchElementException("Enrollment not found"));
            if (u.getPersonId() != offeringFacultyId) {
                throw new AccessDeniedException(
                        "You can only record attendance for your own course offerings.");
            }
        }
        int id = repo.create(req);
        return repo.findById(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public AttendanceDto getById(int id) {
        return repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Attendance record not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<AttendanceDto> search(Integer enrollmentId, Integer offeringId,
                                      Integer studentId, String fromDate, String toDate,
                                      PageRequest page) {
        AuthenticatedUser u = AuthenticatedUser.current();

        // STUDENT: always restrict to own records
        Integer effectiveStudentId = studentId;
        if ("Student".equals(u.getRole())) {
            effectiveStudentId = u.getPersonId();
        }

        return repo.search(enrollmentId, offeringId, effectiveStudentId, fromDate, toDate, page);
    }

    @Transactional
    public AttendanceDto updateStatus(int id, String status) {
        AttendanceDto existing = getById(id);

        AuthenticatedUser u = AuthenticatedUser.current();
        if ("Faculty".equals(u.getRole())) {
            int facultyId = repo
                    .findOfferingFacultyByEnrollmentId(existing.getEnrollmentId())
                    .orElseThrow();
            if (u.getPersonId() != facultyId) {
                throw new AccessDeniedException(
                        "You can only update attendance for your own course offerings.");
            }
        }
        repo.updateStatus(id, status);
        return repo.findById(id).orElseThrow();
    }

    @Transactional
    public void delete(int id) {
        getById(id);
        repo.delete(id);
    }
}
