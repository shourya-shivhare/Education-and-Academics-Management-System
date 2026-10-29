package edu.dbms.education.marks;

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
public class MarksService {

    private final MarksRepository repo;

    /**
     * INSERT or UPDATE depending on whether version is supplied.
     * FACULTY ownership check: only the instructor of the offering may enter marks.
     */
    @Transactional
    public MarksDto upsert(UpsertMarksRequest req) {
        assertFacultyOwnership(req.getOfferingId());

        if (req.getVersion() == null) {
            // First-time entry – no existing row, no version conflict possible
            repo.insert(req);
        } else {
            // Update with optimistic lock; ConflictException thrown on version mismatch
            repo.updateOptimistic(req);
        }
        return repo.findByPk(req.getEnrollmentId(), req.getOfferingId(), req.getAssessmentNo())
                .orElseThrow();
    }

    @Transactional(readOnly = true)
    public MarksDto getByPk(int enrollmentId, int offeringId, int assessmentNo) {
        return repo.findByPk(enrollmentId, offeringId, assessmentNo)
                .orElseThrow(() -> new NoSuchElementException(
                        "Marks not found for enrollment=" + enrollmentId +
                        " offering=" + offeringId + " assessment=" + assessmentNo));
    }

    @Transactional(readOnly = true)
    public List<MarksDto> search(Integer offeringId, Integer enrollmentId,
                                 Integer studentId, PageRequest page) {
        AuthenticatedUser u = AuthenticatedUser.current();

        Integer effectiveStudentId = studentId;
        Integer effectiveOfferingId = offeringId;

        // STUDENT: always restrict to own marks
        if ("Student".equals(u.getRole())) {
            effectiveStudentId = u.getPersonId();
        }
        // FACULTY: must supply offeringId; validated against their own offerings in repo
        // We don't further restrict here – the controller requires offeringId for FACULTY

        return repo.search(effectiveOfferingId, enrollmentId, effectiveStudentId, page);
    }

    // ── Ownership guard ───────────────────────────────────────────────────

    /**
     * If the caller is FACULTY, verify they are the instructor for this offering.
     * ADMIN and REGISTRAR skip this check.
     */
    private void assertFacultyOwnership(int offeringId) {
        AuthenticatedUser u = AuthenticatedUser.current();
        if (!"Faculty".equals(u.getRole())) return;

        int offeringFacultyId = repo.findOfferingFaculty(offeringId)
                .orElseThrow(() -> new NoSuchElementException("Offering not found: " + offeringId));

        if (u.getPersonId() != offeringFacultyId) {
            throw new AccessDeniedException(
                    "You can only enter marks for your own course offerings.");
        }
    }
}
