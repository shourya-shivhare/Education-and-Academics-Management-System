package edu.dbms.education.lab;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;

/**
 * Creates and resets the isolated data rows used by every lab scenario.
 *
 * LAB DESIGN INVARIANT
 * --------------------
 * All lab demos use a fixed set of rows identified by specific IDs or
 * marker values so they never touch real student/offering data and are
 * safe to reset before each run.
 *
 * Fixtures needed:
 *   • Two lab students (lab_student_a / lab_student_b) – person + student rows
 *   • One lab offering with capacity = 1  (for last-seat race)
 *   • One lab offering with capacity = 30 (for ACID atomicity)
 *   • Two lab marks rows  (for lost-update demo)
 *   • Fee-payment rows    (cleaned up before atomicity demo)
 *
 * Because students share the IS-A relationship with person, the fixture must
 * insert into both tables.  All fixture IDs are stored as constants so every
 * lab service references exactly the same rows.
 *
 * Fixture subjects / faculty / term: reuse the first available real rows
 * (SELECT MIN(subject_id) etc.) to avoid requiring a specific seed state.
 * The fixture itself is idempotent (INSERT IGNORE).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LabFixture {

    // ── Well-known fixture IDs ─────────────────────────────────────────────
    // These are set the first time ensureFixture() runs and are stable for
    // the lifetime of the JVM.

    private volatile int studentA   = -1;
    private volatile int studentB   = -1;
    private volatile int offeringSeat1  = -1;  // capacity=1 (race demos)
    private volatile int offeringBig   = -1;  // capacity=30 (ACID demos)
    private volatile int termId     = -1;
    private volatile int subjectId  = -1;
    private volatile int facultyId  = -1;

    private final @Qualifier("appDataSource") DataSource ds;

    /** Exposed so LabController's verify endpoint can create a raw JdbcTemplate. */
    public DataSource getDs() { return ds; }

    // ── Public accessors ──────────────────────────────────────────────────

    public int studentA()       { ensure(); return studentA; }
    public int studentB()       { ensure(); return studentB; }
    public int offeringSeat1()  { ensure(); return offeringSeat1; }
    public int offeringBig()    { ensure(); return offeringBig; }
    public int termId()         { ensure(); return termId; }

    /**
     * Deletes all enrollment and fee-payment rows for the lab students and
     * resets offering capacities/statuses to their lab defaults.
     * Call at the start of every lab scenario so runs are repeatable.
     */
    public void reset(JdbcTemplate jdbc) {
        ensure();
        // Collect enrollment IDs for the lab students first (avoids MySQL's
        // "can't select from table being deleted" restriction)
        List<Integer> enrIds = jdbc.queryForList(
                "SELECT enrollment_id FROM enrollment WHERE student_id IN (?,?)",
                Integer.class, studentA, studentB);

        if (!enrIds.isEmpty()) {
            // Spring's JdbcTemplate doesn't have an "IN list" helper for arrays,
            // so we build it manually – safe because these are Integer values
            String placeholders = enrIds.stream()
                    .map(id -> "?").collect(java.util.stream.Collectors.joining(","));
            Object[] ids = enrIds.toArray();
            jdbc.update("DELETE FROM marks      WHERE enrollment_id IN (" + placeholders + ")", ids);
            jdbc.update("DELETE FROM attendance WHERE enrollment_id IN (" + placeholders + ")", ids);
        }
        jdbc.update("DELETE FROM enrollment  WHERE student_id IN (?,?)", studentA, studentB);
        jdbc.update("DELETE FROM fee_payment WHERE student_id IN (?,?)", studentA, studentB);
        // Reset the single-seat offering to Open with capacity=1
        jdbc.update("UPDATE course_offering SET capacity=1, status='Open' " +
                    "WHERE offering_id=?", offeringSeat1);
        // Reset the big offering
        jdbc.update("UPDATE course_offering SET status='Open' WHERE offering_id=?",
                    offeringBig);
        log.debug("LabFixture.reset() done: studentA={} studentB={} offeringSeat1={} " +
                  "offeringBig={}", studentA, studentB, offeringSeat1, offeringBig);
    }

    /**
     * Inserts two lab marks rows for student A's enrollment in offeringBig,
     * used by the lost-update and deadlock demos.
     * assessment_no 1 and 2 must already exist in the assessment table.
     */
    public void resetMarksForDeadlock(JdbcTemplate jdbc, int enrollmentIdA) {
        // Ensure assessment rows exist (INSERT IGNORE)
        jdbc.update("INSERT IGNORE INTO assessment " +
                    "(offering_id, assessment_no, title, max_marks, weightage) " +
                    "VALUES (?,1,'Lab-A',100,50),(?,2,'Lab-B',100,50)",
                    offeringBig, offeringBig);
        jdbc.update("DELETE FROM marks WHERE enrollment_id=? AND offering_id=?",
                    enrollmentIdA, offeringBig);
        jdbc.update("INSERT INTO marks (enrollment_id, offering_id, assessment_no," +
                    " marks_obtained, version) VALUES (?,?,1,50.0,1),(?,?,2,60.0,1)",
                    enrollmentIdA, offeringBig, enrollmentIdA, offeringBig);
    }

    // ── Internal initialisation ───────────────────────────────────────────

    private synchronized void ensure() {
        if (studentA > 0) return;   // already initialised
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        // Pick real reference rows (term, subject, faculty)
        termId    = firstId(jdbc, "SELECT MIN(term_id)    FROM term    WHERE is_current=TRUE OR 1=1");
        subjectId = firstId(jdbc, "SELECT MIN(subject_id) FROM subject");
        facultyId = firstId(jdbc, "SELECT MIN(faculty_id) FROM faculty");

        // ── Lab person A ──────────────────────────────────────────────────
        studentA = upsertLabPerson(jdbc, "lab_a@edu.lab", "Lab", "StudentA");
        upsertLabStudent(jdbc, studentA, "LAB-A-001");

        // ── Lab person B ──────────────────────────────────────────────────
        studentB = upsertLabPerson(jdbc, "lab_b@edu.lab", "Lab", "StudentB");
        upsertLabStudent(jdbc, studentB, "LAB-B-001");

        // ── Single-seat offering (for race demos) ─────────────────────────
        offeringSeat1 = upsertLabOffering(jdbc, subjectId, facultyId, termId, "LAB1", 1);

        // ── Large offering (for ACID + deadlock) ──────────────────────────
        offeringBig = upsertLabOffering(jdbc, subjectId, facultyId, termId, "LAB2", 30);

        log.info("LabFixture initialised: studentA={} studentB={} " +
                 "offeringSeat1={} offeringBig={}", studentA, studentB,
                 offeringSeat1, offeringBig);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private int firstId(JdbcTemplate jdbc, String sql) {
        try {
            Integer v = jdbc.queryForObject(sql, Integer.class);
            return v != null ? v : 1;
        } catch (EmptyResultDataAccessException e) { return 1; }
    }

    private int upsertLabPerson(JdbcTemplate jdbc, String email,
                                String first, String last) {
        // Idempotent – return existing person_id if email already exists
        Integer existing = null;
        try {
            existing = jdbc.queryForObject(
                    "SELECT person_id FROM person WHERE email=?", Integer.class, email);
        } catch (EmptyResultDataAccessException ignore) {}
        if (existing != null) return existing;

        jdbc.update("INSERT INTO person (first_name,last_name,email,date_of_birth) " +
                    "VALUES (?,?,?,'2000-01-01')", first, last, email);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
    }

    private void upsertLabStudent(JdbcTemplate jdbc, int personId, String rollNo) {
        Integer exists = null;
        try {
            exists = jdbc.queryForObject(
                    "SELECT student_id FROM student WHERE student_id=?",
                    Integer.class, personId);
        } catch (EmptyResultDataAccessException ignore) {}
        if (exists != null) return;

        // program_id: pick first available
        int progId = firstId(jdbc, "SELECT MIN(program_id) FROM program");
        jdbc.update("INSERT INTO student (student_id,roll_no,program_id," +
                    "admission_date,current_semester) VALUES (?,?,?,'2024-01-01',1)",
                    personId, rollNo, progId);
    }

    private int upsertLabOffering(JdbcTemplate jdbc, int subjectId, int facultyId,
                                  int termId, String section, int capacity) {
        // Check if already exists by natural key
        Integer existing = null;
        try {
            existing = jdbc.queryForObject(
                    "SELECT offering_id FROM course_offering " +
                    "WHERE subject_id=? AND faculty_id=? AND term_id=? AND section=?",
                    Integer.class, subjectId, facultyId, termId, section);
        } catch (EmptyResultDataAccessException ignore) {}
        if (existing != null) {
            // Reset capacity in case previous test left it altered
            jdbc.update("UPDATE course_offering SET capacity=?, status='Open' " +
                        "WHERE offering_id=?", capacity, existing);
            return existing;
        }
        jdbc.update("INSERT INTO course_offering " +
                    "(subject_id,faculty_id,term_id,section,capacity,status) " +
                    "VALUES (?,?,?,?,?,'Open')",
                    subjectId, facultyId, termId, section, capacity);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
    }
}
