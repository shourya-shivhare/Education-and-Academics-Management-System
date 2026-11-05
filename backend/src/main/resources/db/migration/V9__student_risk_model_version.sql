ALTER TABLE student_risk
    ADD COLUMN model_version VARCHAR(80) NULL AFTER risk_label;

ALTER TABLE student_risk
    ADD CONSTRAINT uq_student_risk_student_term UNIQUE (student_id, term_id);
