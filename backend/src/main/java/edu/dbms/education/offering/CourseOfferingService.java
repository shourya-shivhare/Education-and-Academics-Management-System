package edu.dbms.education.offering;

import edu.dbms.education.common.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class CourseOfferingService {

    private final CourseOfferingRepository repo;

    @Transactional
    public CourseOfferingDto create(CreateOfferingRequest req) {
        return repo.findById(repo.create(req)).orElseThrow();
    }

    @Transactional(readOnly = true)
    public CourseOfferingDto getById(int id) {
        return repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Offering not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<CourseOfferingDto> search(Integer subjectId, Integer facultyId,
                                          Integer termId, String status,
                                          Integer departmentId, PageRequest page) {
        return repo.search(subjectId, facultyId, termId, status, departmentId, page);
    }

    @Transactional
    public CourseOfferingDto updateStatus(int id, String status) {
        getById(id);
        repo.updateStatus(id, status);
        return repo.findById(id).orElseThrow();
    }

    @Transactional
    public CourseOfferingDto updateCapacity(int id, int capacity) {
        getById(id);
        repo.updateCapacity(id, capacity);
        return repo.findById(id).orElseThrow();
    }

    @Transactional
    public void delete(int id) {
        getById(id);
        repo.delete(id);
    }
}
