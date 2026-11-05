package edu.dbms.education.ai;

import edu.dbms.education.auth.AuthenticatedUser;
import edu.dbms.education.trace.TracingJdbcTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class RiskRepository {

    private final TracingJdbcTemplate jdbc;

    public List<Map<String, Object>> features(int termId) {
        return jdbc.queryTraced(
                "SELECT * FROM v_ml_features WHERE term_id = ?",
                (rs, rowNum) -> {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    var meta = rs.getMetaData();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    return row;
                }, termId);
    }

    public int save(int studentId, int termId, double score, String label,
                    String modelVersion, String factorsJson) {
        return jdbc.updateTraced(
                "INSERT INTO student_risk " +
                        "(student_id, term_id, risk_score, risk_label, model_version, contributing_factors) " +
                        "VALUES (?, ?, ?, ?, ?, CAST(? AS JSON)) " +
                        "ON DUPLICATE KEY UPDATE risk_score = VALUES(risk_score), " +
                        "risk_label = VALUES(risk_label), model_version = VALUES(model_version), " +
                        "contributing_factors = VALUES(contributing_factors), computed_at = CURRENT_TIMESTAMP",
                studentId, termId, score, label, modelVersion, factorsJson);
    }

    public List<Map<String, Object>> list(Integer offeringId, String level, Integer termId) {
        AuthenticatedUser user = AuthenticatedUser.current();
        StringBuilder sql = new StringBuilder(
                "SELECT sr.risk_id, sr.student_id, sr.term_id, sr.risk_score, sr.risk_label, " +
                        "sr.model_version, sr.contributing_factors, sr.computed_at, " +
                        "p.first_name, p.last_name, co.offering_id, s.code AS subject_code " +
                        "FROM student_risk sr " +
                        "JOIN student st ON st.student_id = sr.student_id " +
                        "JOIN person p ON p.person_id = st.student_id " +
                        "LEFT JOIN enrollment e ON e.student_id = sr.student_id " +
                        "LEFT JOIN course_offering co ON co.offering_id = e.offering_id AND co.term_id = sr.term_id " +
                        "LEFT JOIN subject s ON s.subject_id = co.subject_id WHERE 1=1");
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        if (offeringId != null) { sql.append(" AND co.offering_id = ?"); params.add(offeringId); }
        if (level != null && !level.isBlank()) { sql.append(" AND sr.risk_label = ?"); params.add(level); }
        if (termId != null) { sql.append(" AND sr.term_id = ?"); params.add(termId); }
        if ("Faculty".equals(user.getRole())) {
            sql.append(" AND co.faculty_id = ?");
            params.add(user.getPersonId());
        } else if ("Student".equals(user.getRole())) {
            sql.append(" AND sr.student_id = ?");
            params.add(user.getPersonId());
        }
        sql.append(" ORDER BY sr.risk_score DESC, sr.student_id");
        return jdbc.queryTraced(sql.toString(), (rs, rowNum) -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            var meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) row.put(meta.getColumnLabel(i), rs.getObject(i));
            return row;
        }, params.toArray());
    }
}
