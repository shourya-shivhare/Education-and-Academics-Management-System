package edu.dbms.education.subject;

import edu.dbms.education.common.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository repo;

    @Transactional
    public SubjectDto create(CreateSubjectRequest req) {
        int id = repo.create(req);
        return repo.findById(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public SubjectDto getById(int id) {
        return repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Subject not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<SubjectDto> search(String keyword, Integer departmentId,
                                   Integer programId, Integer semester, PageRequest page) {
        return repo.search(keyword, departmentId, programId, semester, page);
    }

    @Transactional
    public SubjectDto update(int id, CreateSubjectRequest req) {
        getById(id);
        repo.update(id, req);
        return repo.findById(id).orElseThrow();
    }

    @Transactional
    public void delete(int id) {
        getById(id);
        repo.delete(id);
    }
}
