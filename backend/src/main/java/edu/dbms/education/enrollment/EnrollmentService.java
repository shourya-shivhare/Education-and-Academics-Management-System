package edu.dbms.education.enrollment;

import edu.dbms.education.common.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRepository repo;

    /**
     * Enroll goes through the stored procedure which owns its own transaction.
     * We mark @Transactional here so any outer transaction participates,
     * but the procedure's internal COMMIT is the definitive commit.
     */
    @Transactional
    public void enroll(int studentId, int offeringId) {
        repo.enroll(studentId, offeringId);
    }

    @Transactional
    public void drop(int studentId, int offeringId) {
        repo.drop(studentId, offeringId);
    }

    @Transactional(readOnly = true)
    public EnrollmentDto getById(int id) {
        return repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Enrollment not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<EnrollmentDto> search(Integer studentId, Integer offeringId,
                                      Integer termId, String status, PageRequest page) {
        return repo.search(studentId, offeringId, termId, status, page);
    }
}
