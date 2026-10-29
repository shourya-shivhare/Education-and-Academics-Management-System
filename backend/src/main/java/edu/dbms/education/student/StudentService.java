package edu.dbms.education.student;

import edu.dbms.education.common.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Thin orchestration layer.  All SQL lives in the repository.
 *
 * @Transactional: student creation spans two INSERTs (person + student).
 * If the second INSERT fails (duplicate roll_no etc.) the person INSERT is
 * rolled back by the transaction manager – atomicity.
 */
@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository repo;

    @Transactional
    public StudentDto create(CreateStudentRequest req) {
        int id = repo.create(req);
        return repo.findById(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public StudentDto getById(int id) {
        return repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Student not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<StudentDto> search(String name, Integer programId, Integer departmentId,
                                   String status, PageRequest page) {
        return repo.search(name, programId, departmentId, status, page);
    }

    @Transactional
    public StudentDto update(int id, UpdateStudentRequest req) {
        getById(id);   // 404 if missing
        repo.update(id, req);
        return repo.findById(id).orElseThrow();
    }

    @Transactional
    public void delete(int id) {
        getById(id);   // 404 if missing
        repo.delete(id);
    }
}
