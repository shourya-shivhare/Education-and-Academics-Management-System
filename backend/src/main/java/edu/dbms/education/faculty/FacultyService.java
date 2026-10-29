package edu.dbms.education.faculty;

import edu.dbms.education.common.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class FacultyService {

    private final FacultyRepository repo;

    @Transactional
    public FacultyDto create(CreateFacultyRequest req) {
        int id = repo.create(req);
        return repo.findById(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public FacultyDto getById(int id) {
        return repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Faculty not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<FacultyDto> search(String name, Integer departmentId, PageRequest page) {
        return repo.search(name, departmentId, page);
    }

    @Transactional
    public FacultyDto update(int id, UpdateFacultyRequest req) {
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
