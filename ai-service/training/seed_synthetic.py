"""Seed deterministic synthetic academic data for ML coursework demonstrations."""

from __future__ import annotations

import os
from datetime import date, timedelta

import pymysql


DATABASE_URL = os.getenv(
    "ML_SEED_DATABASE_URL",
    "mysql+pymysql://flyway_user:ChangeMe_flyway_2026!@localhost:3306/edu_db",
)


def connection_options() -> dict[str, object]:
    prefix, rest = DATABASE_URL.split("://", 1)
    credentials, host_db = rest.split("@", 1)
    username, password = credentials.split(":", 1)
    host_port, database = host_db.split("/", 1)
    host, port = (host_port.split(":", 1) + ["3306"])[:2]
    return {
        "host": host,
        "port": int(port),
        "user": username,
        "password": password,
        "database": database,
        "autocommit": False,
    }


def main() -> None:
    connection = pymysql.connect(**connection_options())
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT COUNT(*) FROM v_ml_features")
            if cursor.fetchone()[0]:
                print("Synthetic seed skipped: v_ml_features already contains rows.")
                return

            cursor.execute(
                "SELECT term_id, start_date, end_date FROM term "
                "WHERE start_date < %s ORDER BY start_date LIMIT 5",
                (date.today(),),
            )
            terms = cursor.fetchall()
            if len(terms) < 3:
                raise RuntimeError("At least three historical terms are required.")

            cursor.execute("SELECT department_id FROM department ORDER BY department_id LIMIT 1")
            department_id = cursor.fetchone()[0]
            cursor.execute(
                "SELECT program_id FROM program WHERE department_id = %s ORDER BY program_id LIMIT 1",
                (department_id,),
            )
            program_id = cursor.fetchone()[0]
            cursor.execute(
                "SELECT room_id FROM classroom ORDER BY room_id LIMIT 1"
            )
            room_id = cursor.fetchone()[0]

            cursor.execute(
                "INSERT INTO subject (code, title, credits, department_id, program_id, semester) "
                "VALUES (%s, %s, 3, %s, %s, 1)",
                ("SYN101", "Synthetic Data Foundations", department_id, program_id),
            )
            subject_one = cursor.lastrowid
            cursor.execute(
                "INSERT INTO subject (code, title, credits, department_id, program_id, semester) "
                "VALUES (%s, %s, 3, %s, %s, 1)",
                ("SYN102", "Synthetic Academic Analytics", department_id, program_id),
            )
            subject_two = cursor.lastrowid

            faculty_ids: list[int] = []
            for index in range(2):
                cursor.execute(
                    "INSERT INTO person "
                    "(first_name, last_name, email, date_of_birth, gender, city, state, pin_code) "
                    "VALUES (%s, %s, %s, %s, 'Other', 'Synthetic City', 'Synthetic State', '000000')",
                    (
                        "SyntheticFaculty",
                        str(index + 1),
                        f"synthetic.faculty{index + 1}@example.invalid",
                        date(1980, 1, 1),
                    ),
                )
                person_id = cursor.lastrowid
                cursor.execute(
                    "INSERT INTO faculty "
                    "(faculty_id, employee_no, department_id, designation, hire_date, max_load) "
                    "VALUES (%s, %s, %s, 'Lecturer', %s, 6)",
                    (person_id, f"SYN-F-{index + 1}", department_id, date(2020, 1, 1)),
                )
                faculty_ids.append(person_id)

            student_ids: list[int] = []
            for index in range(30):
                cursor.execute(
                    "INSERT INTO person "
                    "(first_name, last_name, email, date_of_birth, gender, city, state, pin_code) "
                    "VALUES (%s, %s, %s, %s, 'Other', 'Synthetic City', 'Synthetic State', '000000')",
                    (
                        "SyntheticStudent",
                        str(index + 1),
                        f"synthetic.student{index + 1}@example.invalid",
                        date(2004, 1, 1),
                    ),
                )
                person_id = cursor.lastrowid
                cursor.execute(
                    "INSERT INTO student "
                    "(student_id, roll_no, program_id, admission_date, status, current_semester) "
                    "VALUES (%s, %s, %s, %s, 'Active', 5)",
                    (person_id, f"SYN-{index + 1:03d}", program_id, terms[0][1]),
                )
                student_ids.append(person_id)

            for term_index, (term_id, start_date, end_date) in enumerate(terms):
                offering_ids: list[int] = []
                for subject_index, subject_id in enumerate((subject_one, subject_two)):
                    cursor.execute(
                        "INSERT INTO course_offering "
                        "(subject_id, faculty_id, term_id, section, capacity, status) "
                        "VALUES (%s, %s, %s, %s, 40, 'Completed')",
                        (
                            subject_id,
                            faculty_ids[subject_index],
                            term_id,
                            f"S{term_index + 1}",
                        ),
                    )
                    offering_id = cursor.lastrowid
                    offering_ids.append(offering_id)
                    cursor.execute(
                        "INSERT INTO assessment "
                        "(offering_id, assessment_no, title, max_marks, weightage, assessment_date) "
                        "VALUES (%s, 1, 'Internal 1', 100, 40, %s), "
                        "(%s, 2, 'Internal 2', 100, 40, %s)",
                        (
                            offering_id,
                            start_date + timedelta(days=35),
                            offering_id,
                            start_date + timedelta(days=55),
                        ),
                    )

                for student_index, student_id in enumerate(student_ids):
                    high_risk = student_index < 10
                    for offering_id in offering_ids:
                        cursor.execute(
                            "INSERT INTO enrollment "
                            "(student_id, offering_id, enroll_date, status, final_marks, grade) "
                            "VALUES (%s, %s, %s, 'Completed', NULL, NULL)",
                            (student_id, offering_id, start_date),
                        )
                        enrollment_id = cursor.lastrowid
                        attendance_days = 10
                        present_days = 6 if high_risk else 9
                        for day in range(attendance_days):
                            cursor.execute(
                                "INSERT INTO attendance (enrollment_id, class_date, status) "
                                "VALUES (%s, %s, %s)",
                                (
                                    enrollment_id,
                                    start_date + timedelta(days=day * 7),
                                    "Present" if day < present_days else "Absent",
                                ),
                            )
                        if high_risk:
                            internal_one, internal_two, final_marks, grade = 35, 38, 35, "F"
                        else:
                            internal_one, internal_two, final_marks, grade = 78, 82, 80, "A+"
                        cursor.execute(
                            "INSERT INTO marks "
                            "(enrollment_id, offering_id, assessment_no, marks_obtained) "
                            "VALUES (%s, %s, 1, %s), (%s, %s, 2, %s)",
                            (
                                enrollment_id,
                                offering_id,
                                internal_one,
                                enrollment_id,
                                offering_id,
                                internal_two,
                            ),
                        )
                        cursor.execute(
                            "UPDATE enrollment SET final_marks = %s, grade = %s "
                            "WHERE enrollment_id = %s",
                            (final_marks, grade, enrollment_id),
                        )
                    cursor.execute(
                        "INSERT INTO fee_payment "
                        "(student_id, term_id, amount, payment_date, payment_mode, status) "
                        "VALUES (%s, %s, 1000, %s, 'Online', %s)",
                        (
                            student_id,
                            term_id,
                            start_date + timedelta(days=60),
                            "Pending" if high_risk else "Paid",
                        ),
                    )

            connection.commit()
            print("Synthetic seed complete: 30 students, 5 terms, 300 enrollments.")
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


if __name__ == "__main__":
    main()
