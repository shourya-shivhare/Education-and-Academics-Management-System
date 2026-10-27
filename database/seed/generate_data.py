#!/usr/bin/env python3
"""
=============================================================================
database/seed/generate_data.py
=============================================================================
Seed Data Generator for Education and Academics Management System (DBMS Coursework).

DBMS Concepts Illustrated:
  - Referential Integrity & Topological Ordering: Parent tables (person, department)
    are seeded before dependent tables (faculty, student, offering, enrollment, etc.).
  - Business Rule Enforcement (Pre-Trigger):
      * Maximum 26 credits per student per term.
      * Offering capacity limits strictly observed.
      * Prerequisite validation (must have passed with grade != 'F' in earlier term).
      * Timetable clash prevention: no faculty or room double-booking on day/time.
      * Assessment weightage totals exactly 100% per course offering.
      * Marks obtained bounded by [0, max_marks].
  - Realistic Data Correlations:
      * Latent student profile: High attendance/timely fee payment -> Higher marks.
      * Low attendance/late fee payment -> Lower marks (at-risk candidates).
      * Controlled noise and surprise cases:
          - Talented slackers: Low attendance (<70%) but passing marks (>65%).
          - Struggling strivers: High attendance (>85%) but failing marks (<40%).
  - Re-runnability & Idempotence:
      * Safely disables FOREIGN_KEY_CHECKS during truncation.
      * Truncates dynamic operational tables and reloads deterministically.
      * Fixed random seeds (Faker + random) produce identical, reproducible datasets.
  - Security (RBAC & Authentication):
      * BCrypt-hashed passwords for Admin, Registrar, Faculty, and Students.
=============================================================================
"""

import sys
import os
import argparse
import random
from datetime import datetime, date, timedelta
from typing import List, Dict, Tuple, Any, Optional

try:
    from faker import Faker
except ImportError:
    print("Error: 'faker' package not found. Install it with: pip install faker")
    sys.exit(1)

try:
    import bcrypt
except ImportError:
    print("Error: 'bcrypt' package not found. Install it with: pip install bcrypt")
    sys.exit(1)

# Support either mysql.connector or pymysql
MYSQL_CONNECTOR_AVAILABLE = False
PYMYSQL_AVAILABLE = False

try:
    import mysql.connector
    MYSQL_CONNECTOR_AVAILABLE = True
except ImportError:
    pass

if not MYSQL_CONNECTOR_AVAILABLE:
    try:
        import pymysql
        PYMYSQL_AVAILABLE = True
    except ImportError:
        pass


# =============================================================================
# Configuration & Constants
# =============================================================================
RANDOM_SEED = 42
DEFAULT_PASSWORD = "Password123!"

# Batch insert sizes
BATCH_SIZE = 1000

# Fixed reference Term IDs from V5__reference_data.sql
TERMS = [
    {"term_id": 1, "name": "2024-25 Odd",  "start": date(2024, 8, 1), "end": date(2024, 12, 15), "is_current": False},
    {"term_id": 2, "name": "2024-25 Even", "start": date(2025, 1, 6), "end": date(2025, 5, 15),  "is_current": False},
    {"term_id": 3, "name": "2025-26 Odd",  "start": date(2025, 8, 1), "end": date(2025, 12, 15), "is_current": False},
    {"term_id": 4, "name": "2025-26 Even", "start": date(2026, 1, 6), "end": date(2026, 5, 15),  "is_current": False},
    {"term_id": 5, "name": "2026-27 Odd",  "start": date(2026, 8, 1), "end": date(2026, 12, 15), "is_current": True},
    {"term_id": 6, "name": "2026-27 Even", "start": date(2027, 1, 6), "end": date(2027, 5, 15),  "is_current": False},
]

# Midterm snapshot date for Term 5 (used for ML features without label leakage)
CURRENT_TERM_CUTOFF_DATE = date(2026, 10, 20)

# Grade Scale reference mapping (aligned with V5__reference_data.sql)
def calculate_grade(marks: float) -> Tuple[str, float]:
    """Return (grade_letter, grade_point) based on percentage."""
    if marks >= 90.0:
        return 'O', 10.0
    elif marks >= 80.0:
        return 'A+', 9.0
    elif marks >= 70.0:
        return 'A', 8.0
    elif marks >= 60.0:
        return 'B+', 7.0
    elif marks >= 50.0:
        return 'B', 6.0
    elif marks >= 45.0:
        return 'C', 5.0
    elif marks >= 40.0:
        return 'P', 4.0
    else:
        return 'F', 0.0


# =============================================================================
# Subject Catalog (40 Subjects with Detailed Descriptions for TF-IDF / Search)
# =============================================================================
SUBJECTS_CATALOG = [
    # Department 1: Computer Science (14 subjects)
    {"code": "CS101", "title": "Programming in C", "credits": 4, "dept_id": 1, "prog_id": 1, "sem": 1,
     "desc": "Foundations of procedural programming in C. Covers pointers, dynamic memory allocation, arrays, strings, structures, file input/output, recursion, and algorithm problem solving."},
    {"code": "CS102", "title": "Digital Logic Design", "credits": 3, "dept_id": 1, "prog_id": 1, "sem": 1,
     "desc": "Boolean algebra, logic gates, combinational logic circuits, multiplexers, decoders, flip-flops, sequential circuits, synchronous counters, shift registers, and digital system design."},
    {"code": "CS201", "title": "Data Structures", "credits": 4, "dept_id": 1, "prog_id": 1, "sem": 2,
     "desc": "Linear and non-linear data structures. Detailed coverage of arrays, linked lists, stacks, queues, binary search trees, AVL trees, heaps, hash tables, graph representations, and asymptotic analysis."},
    {"code": "CS202", "title": "Computer Organization and Architecture", "credits": 4, "dept_id": 1, "prog_id": 1, "sem": 2,
     "desc": "Instruction set architecture, CPU datapath, pipelining, pipeline hazards, cache memory hierarchies, virtual memory, bus architectures, and peripheral I/O interfacing."},
    {"code": "CS301", "title": "Design and Analysis of Algorithms", "credits": 4, "dept_id": 1, "prog_id": 1, "sem": 3,
     "desc": "Algorithm design paradigms including divide-and-conquer, greedy algorithms, dynamic programming, backtracking, branch-and-bound. Graph algorithms, shortest paths, minimum spanning trees, and NP-completeness."},
    {"code": "CS302", "title": "Database Management Systems", "credits": 4, "dept_id": 1, "prog_id": 1, "sem": 3,
     "desc": "Relational data model, relational algebra, SQL querying, functional dependencies, normalization up to 3NF and BCNF, B-tree indexing, transaction ACID properties, concurrency control, and crash recovery."},
    {"code": "CS303", "title": "Operating Systems", "credits": 4, "dept_id": 1, "prog_id": 1, "sem": 4,
     "desc": "Operating system concepts: processes, threads, CPU scheduling, inter-process communication, semaphores, mutexes, deadlock detection and prevention, memory paging, segmentation, and file systems."},
    {"code": "CS304", "title": "Computer Networks", "credits": 3, "dept_id": 1, "prog_id": 1, "sem": 4,
     "desc": "OSI and TCP/IP protocol stacks. Physical media, data link layer framing, error correction, IP addressing, routing algorithms, transport layer TCP/UDP mechanisms, and network security protocols."},
    {"code": "CS401", "title": "Artificial Intelligence and Machine Learning", "credits": 4, "dept_id": 1, "prog_id": 1, "sem": 5,
     "desc": "Supervised and unsupervised learning, linear regression, logistic regression, decision trees, random forests, support vector machines, neural networks, feature engineering, and cross-validation."},
    {"code": "CS402", "title": "Web Application Development", "credits": 3, "dept_id": 1, "prog_id": 1, "sem": 5,
     "desc": "Modern web development architectures. HTML5, CSS3, JavaScript, React frontend framework, RESTful APIs, Spring Boot microservices, JSON Web Tokens (JWT), and responsive UI design."},
    {"code": "CS403", "title": "Compiler Design", "credits": 4, "dept_id": 1, "prog_id": 1, "sem": 6,
     "desc": "Lexical analysis, syntax analysis, LL and LR parsing techniques, syntax-directed translation, symbol tables, intermediate code generation, data flow analysis, and code optimization."},
    {"code": "CS404", "title": "Cloud Computing and Distributed Systems", "credits": 3, "dept_id": 1, "prog_id": 1, "sem": 6,
     "desc": "Distributed computing fundamentals, RPC, cloud virtualization, containerization with Docker, microservices, distributed consensus algorithms, distributed key-value storage, and high availability."},
    {"code": "CS501", "title": "Advanced Data Mining", "credits": 3, "dept_id": 1, "prog_id": 1, "sem": 7,
     "desc": "Knowledge discovery in large databases. Association rule mining, Apriori, FP-growth, cluster analysis, DBSCAN, outlier detection, text mining, graph mining, and stream analytics."},
    {"code": "CS502", "title": "Information and Network Security", "credits": 3, "dept_id": 1, "prog_id": 1, "sem": 7,
     "desc": "Cryptography principles, AES, RSA public key cryptography, cryptographic hash functions, digital signatures, key management, firewalls, intrusion detection, SSL/TLS, and penetration testing."},

    # Department 2: Electronics & Communication (8 subjects)
    {"code": "EC101", "title": "Basic Electronics", "credits": 3, "dept_id": 2, "prog_id": 4, "sem": 1,
     "desc": "Semiconductor fundamentals, PN junction diodes, Zener diodes, BJT operation, biasing techniques, JFET, MOSFET, operational amplifiers, and rectifier circuits."},
    {"code": "EC201", "title": "Signals and Systems", "credits": 4, "dept_id": 2, "prog_id": 4, "sem": 2,
     "desc": "Continuous-time and discrete-time signals, linear time-invariant (LTI) systems, convolution, Fourier series, Fourier transform, Laplace transform, and z-transform analysis."},
    {"code": "EC202", "title": "Electronic Circuit Analysis", "credits": 4, "dept_id": 2, "prog_id": 4, "sem": 3,
     "desc": "Small-signal analysis of BJT and MOSFET amplifiers, frequency response, multistage amplifiers, feedback amplifiers, Barkhausen criterion, sinusoidal oscillators, and tuned amplifiers."},
    {"code": "EC301", "title": "Digital Signal Processing", "credits": 4, "dept_id": 2, "prog_id": 4, "sem": 4,
     "desc": "Discrete Fourier Transform (DFT), Fast Fourier Transform (FFT) algorithms, design of FIR filters, design of IIR Butterworth and Chebyshev filters, and finite word length effects."},
    {"code": "EC302", "title": "Analog and Digital Communication", "credits": 4, "dept_id": 2, "prog_id": 4, "sem": 5,
     "desc": "Amplitude modulation, frequency modulation, pulse code modulation (PCM), delta modulation, digital keying techniques (ASK, FSK, PSK, QAM), and channel noise performance."},
    {"code": "EC401", "title": "VLSI System Design", "credits": 4, "dept_id": 2, "prog_id": 4, "sem": 6,
     "desc": "MOS transistor theory, CMOS inverter characteristics, combinational logic design, dynamic logic, static timing analysis, stick diagrams, layout rules, and Verilog HDL modeling."},
    {"code": "EC402", "title": "Embedded Systems and IoT", "credits": 3, "dept_id": 2, "prog_id": 4, "sem": 6,
     "desc": "ARM Cortex-M microcontroller architecture, GPIO, timers, ADC, interrupt handling, real-time operating systems (RTOS), IoT sensor interfacing, and wireless protocols (BLE, Zigbee, MQTT)."},
    {"code": "EC501", "title": "Wireless Communication Networks", "credits": 3, "dept_id": 2, "prog_id": 4, "sem": 7,
     "desc": "Cellular concepts, frequency reuse, handoff strategies, wireless channel fading, multipath propagation, CDMA, OFDM, 4G LTE architectures, and 5G New Radio technologies."},

    # Department 3: Mechanical Engineering (6 subjects)
    {"code": "ME101", "title": "Engineering Mechanics", "credits": 4, "dept_id": 3, "prog_id": 5, "sem": 1,
     "desc": "Statics of particles and rigid bodies, coplanar force systems, trusses, dry friction, center of gravity, moment of inertia, kinematics of particles, Newton laws, and work-energy methods."},
    {"code": "ME201", "title": "Thermodynamics", "credits": 4, "dept_id": 3, "prog_id": 5, "sem": 2,
     "desc": "Zeroth, First, and Second laws of thermodynamics, pure substances, ideal gas laws, Carnot cycle, entropy, exergy, Rankine cycle, Otto and Diesel cycles, and psychrometric charts."},
    {"code": "ME202", "title": "Fluid Mechanics", "credits": 4, "dept_id": 3, "prog_id": 5, "sem": 3,
     "desc": "Fluid statics, buoyancy, fluid kinematics, stream functions, continuity equation, Navier-Stokes equations, Bernoulli equation, laminar and turbulent pipe flow, and boundary layer theory."},
    {"code": "ME301", "title": "Heat and Mass Transfer", "credits": 4, "dept_id": 3, "prog_id": 5, "sem": 4,
     "desc": "Modes of heat transfer: steady and unsteady conduction, lumped capacitance method, forced and free convection, radiation laws, Stefan-Boltzmann law, heat exchanger design, and Fick law."},
    {"code": "ME302", "title": "Machine Design", "credits": 4, "dept_id": 3, "prog_id": 5, "sem": 5,
     "desc": "Design of machine elements against static and fatigue failure. Design of shafts, keys, couplings, bolted and welded joints, helical springs, spur gears, and rolling element bearings."},
    {"code": "ME401", "title": "Manufacturing Technology", "credits": 3, "dept_id": 3, "prog_id": 5, "sem": 6,
     "desc": "Metal casting processes, gating design, bulk deformation, metal forming, sheet metal working, arc welding, resistance welding, CNC machining, metrology, and additive manufacturing."},

    # Department 4: Civil Engineering (5 subjects)
    {"code": "CE101", "title": "Solid Mechanics", "credits": 4, "dept_id": 4, "prog_id": 6, "sem": 1,
     "desc": "Stress and strain relationships, Hooke law, axial deformation, torsion of circular shafts, shear force and bending moment diagrams, flexural stresses, and Mohr stress circles."},
    {"code": "CE201", "title": "Structural Analysis", "credits": 4, "dept_id": 4, "prog_id": 6, "sem": 2,
     "desc": "Analysis of determinate and indeterminate structures, slope-deflection equations, moment distribution method, strain energy methods, influence lines for continuous beams, and truss analysis."},
    {"code": "CE202", "title": "Surveying and Geomatics", "credits": 3, "dept_id": 4, "prog_id": 6, "sem": 2,
     "desc": "Principles of surveying, chain and compass survey, levelling, theodolite traverse, contouring, curves, Total Station operations, Global Positioning System (GPS), and GIS fundamentals."},
    {"code": "CE301", "title": "Geotechnical Engineering", "credits": 4, "dept_id": 4, "prog_id": 6, "sem": 3,
     "desc": "Soil formation, phase relationships, soil classification, permeability, seepage, effective stress principle, consolidation settlement, shear strength criteria, and lateral earth pressures."},
    {"code": "CE302", "title": "Reinforced Concrete Design", "credits": 4, "dept_id": 4, "prog_id": 6, "sem": 4,
     "desc": "Limit state design philosophy, design of singly and doubly reinforced beams, flanged beams, one-way and two-way slabs, short and slender columns, and isolated footings per IS 456."},

    # Department 5: Mathematics (4 subjects)
    {"code": "MA101", "title": "Calculus and Linear Algebra", "credits": 4, "dept_id": 5, "prog_id": 7, "sem": 1,
     "desc": "Single variable calculus, Mean Value Theorems, Taylor series, vector spaces, linear transformations, matrix operations, rank, systems of linear equations, eigenvalues, and eigenvectors."},
    {"code": "MA201", "title": "Multivariable Calculus and Differential Equations", "credits": 4, "dept_id": 5, "prog_id": 7, "sem": 2,
     "desc": "Partial derivatives, gradient, directional derivatives, multiple integrals, line and surface integrals, Green, Stokes, and Gauss divergence theorems, and ordinary differential equations."},
    {"code": "MA301", "title": "Probability and Statistics", "credits": 4, "dept_id": 5, "prog_id": 7, "sem": 3,
     "desc": "Probability axioms, conditional probability, Bayes theorem, discrete and continuous random variables, joint distributions, Central Limit Theorem, hypothesis testing, and regression analysis."},
    {"code": "MA401", "title": "Numerical Methods and Optimization", "credits": 3, "dept_id": 5, "prog_id": 7, "sem": 4,
     "desc": "Numerical solutions of algebraic equations, Newton-Raphson, polynomial interpolation, numerical differentiation and integration, Runge-Kutta methods, and linear programming simplex method."},

    # Department 6: Physics (3 subjects)
    {"code": "PH101", "title": "Engineering Physics", "credits": 3, "dept_id": 6, "prog_id": 8, "sem": 1,
     "desc": "Wave optics, interference in thin films, diffraction gratings, polarization of light, lasers and fiber optics, electromagnetism, Maxwell equations, and electromagnetic wave propagation."},
    {"code": "PH201", "title": "Quantum Mechanics and Modern Physics", "credits": 4, "dept_id": 6, "prog_id": 8, "sem": 2,
     "desc": "Inadequacy of classical mechanics, wave-particle duality, de Broglie hypothesis, Heisenberg uncertainty principle, Schrodinger wave equation, particle in a box, and quantum tunneling."},
    {"code": "PH301", "title": "Solid State Physics", "credits": 3, "dept_id": 6, "prog_id": 8, "sem": 3,
     "desc": "Crystalline states, Bravais lattices, Miller indices, X-ray diffraction, Bragg law, free electron theory of metals, band theory of solids, semiconductors, superconductivity, and magnetism."}
]

# Subject Prerequisites: (subject_code, prereq_code, min_grade)
PREREQUISITES_CONFIG = [
    ("CS201", "CS101", "P"),
    ("CS202", "CS102", "P"),
    ("CS301", "CS201", "P"),
    ("CS302", "CS201", "P"),
    ("CS303", "CS202", "P"),
    ("CS304", "CS201", "P"),
    ("CS401", "CS301", "P"),
    ("CS402", "CS302", "P"),
    ("CS403", "CS301", "P"),
    ("CS404", "CS303", "P"),
    ("CS501", "CS401", "P"),
    ("CS502", "CS304", "P"),
    ("EC201", "EC101", "P"),
    ("EC202", "EC101", "P"),
    ("EC301", "EC201", "P"),
    ("EC302", "EC201", "P"),
    ("EC401", "EC202", "P"),
    ("EC402", "EC101", "P"),
    ("EC501", "EC302", "P"),
    ("ME201", "ME101", "P"),
    ("ME202", "ME101", "P"),
    ("ME301", "ME201", "P"),
    ("ME302", "ME101", "P"),
    ("ME401", "ME101", "P"),
    ("CE201", "CE101", "P"),
    ("CE301", "CE101", "P"),
    ("CE302", "CE201", "P"),
    ("MA201", "MA101", "P"),
    ("MA301", "MA101", "P"),
    ("MA401", "MA201", "P"),
    ("PH201", "PH101", "P"),
    ("PH301", "PH201", "P"),
]


# =============================================================================
# Helper Class: Data Collector & Formatter
# =============================================================================
class SeedDataGenerator:
    def __init__(self, seed: int = RANDOM_SEED):
        self.seed = seed
        random.seed(seed)
        self.faker = Faker('en_IN')
        Faker.seed(seed)

        # Store generated records
        self.persons: List[Dict[str, Any]] = []
        self.person_phones: List[Dict[str, Any]] = []
        self.faculty: List[Dict[str, Any]] = []
        self.students: List[Dict[str, Any]] = []
        self.subjects: List[Dict[str, Any]] = []
        self.subject_prereqs: List[Dict[str, Any]] = []
        self.offerings: List[Dict[str, Any]] = []
        self.timetable_slots: List[Dict[str, Any]] = []
        self.enrollments: List[Dict[str, Any]] = []
        self.assessments: List[Dict[str, Any]] = []
        self.marks: List[Dict[str, Any]] = []
        self.attendance: List[Dict[str, Any]] = []
        self.fee_payments: List[Dict[str, Any]] = []
        self.users: List[Dict[str, Any]] = []
        self.hod_assignments: Dict[int, int] = {}  # dept_id -> faculty_id

        # Internal lookup caches
        self.subject_code_to_id: Dict[str, int] = {}
        self.prereqs_by_subject_id: Dict[int, List[int]] = {}
        self.student_profiles: Dict[int, Dict[str, Any]] = {}

        # Precompute standard bcrypt hash for default password
        print("[1/9] Pre-generating BCrypt hash for seed credentials...")
        raw_hash = bcrypt.hashpw(DEFAULT_PASSWORD.encode('utf-8'), bcrypt.gensalt(10)).decode('utf-8')
        # Ensure Spring Security compatibility ($2a$ format)
        if raw_hash.startswith("$2b$"):
            raw_hash = "$2a$" + raw_hash[4:]
        self.bcrypt_hash = raw_hash

    # -------------------------------------------------------------------------
    # 1. Faculty Generation (~30 Faculty across 6 Departments)
    # -------------------------------------------------------------------------
    def generate_faculty(self):
        print("[2/9] Generating 30 Faculty members across 6 Departments...")
        # Distribution: Dept 1: 6, Dept 2: 5, Dept 3: 5, Dept 4: 5, Dept 5: 5, Dept 6: 4 -> Total: 30
        dept_faculty_counts = {1: 6, 2: 5, 3: 5, 4: 5, 5: 5, 6: 4}
        person_id = 1
        emp_counter = 101

        designations = ["Professor", "Associate Professor", "Assistant Professor", "Assistant Professor"]

        for dept_id, count in dept_faculty_counts.items():
            for i in range(count):
                first_name = self.faker.first_name()
                last_name = self.faker.last_name()
                email = f"{first_name.lower()}.{last_name.lower()}.fac{emp_counter}@university.edu"
                dob = self.faker.date_of_birth(minimum_age=32, maximum_age=62)
                gender = random.choice(['M', 'F'])

                person_row = {
                    "person_id": person_id,
                    "first_name": first_name,
                    "last_name": last_name,
                    "email": email,
                    "date_of_birth": dob,
                    "gender": gender,
                    "address_line": self.faker.street_address().replace("'", "").replace("\n", ", ").replace("\r", ""),
                    "city": self.faker.city().replace("'", ""),
                    "state": self.faker.state().replace("'", ""),
                    "pin_code": str(random.randint(110001, 700099))
                }
                self.persons.append(person_row)

                # Phone number
                phone_row = {
                    "person_id": person_id,
                    "phone_no": f"98{random.randint(10000000, 99999999)}",
                    "phone_type": "Work"
                }
                self.person_phones.append(phone_row)

                # Designation & HOD assignment (first faculty in dept is Professor & HOD)
                if i == 0:
                    desig = "Professor"
                    self.hod_assignments[dept_id] = person_id
                else:
                    desig = random.choice(designations)

                hire_year = random.randint(2010, 2021)
                hire_date = date(hire_year, random.randint(1, 12), random.randint(1, 28))

                faculty_row = {
                    "faculty_id": person_id,
                    "employee_no": f"FAC{emp_counter}",
                    "department_id": dept_id,
                    "designation": desig,
                    "hire_date": hire_date,
                    "max_load": 4
                }
                self.faculty.append(faculty_row)

                # Faculty User Account
                username = f"fac_{first_name.lower()}{emp_counter}"
                self.users.append({
                    "person_id": person_id,
                    "username": username,
                    "password_hash": self.bcrypt_hash,
                    "role": "Faculty",
                    "is_active": True,
                    "last_login": datetime.now() - timedelta(days=random.randint(1, 30))
                })

                person_id += 1
                emp_counter += 1

    # -------------------------------------------------------------------------
    # 2. Subjects & Prerequisites (40 Subjects with rich descriptions)
    # -------------------------------------------------------------------------
    def generate_subjects_and_prerequisites(self):
        print("[3/9] Seeding 40 Subjects with descriptions & prerequisite DAG...")
        subject_id = 1
        for sub in SUBJECTS_CATALOG:
            sub_row = {
                "subject_id": subject_id,
                "code": sub["code"],
                "title": sub["title"],
                "credits": sub["credits"],
                "department_id": sub["dept_id"],
                "program_id": sub["prog_id"],
                "semester": sub["sem"],
                "description": sub["desc"]
            }
            self.subjects.append(sub_row)
            self.subject_code_to_id[sub["code"]] = subject_id
            self.prereqs_by_subject_id[subject_id] = []
            subject_id += 1

        for sub_code, prereq_code, min_grade in PREREQUISITES_CONFIG:
            s_id = self.subject_code_to_id[sub_code]
            p_id = self.subject_code_to_id[prereq_code]
            self.subject_prereqs.append({
                "subject_id": s_id,
                "prereq_subject_id": p_id,
                "min_grade": min_grade
            })
            self.prereqs_by_subject_id[s_id].append(p_id)

    # -------------------------------------------------------------------------
    # 3. Students Generation (~300 Students across 12 Programs)
    # -------------------------------------------------------------------------
    def generate_students(self):
        print("[4/9] Generating 310 Students across 12 Programs with academic profiles...")
        # Start person_id after faculty (31 .. 340)
        person_id = 31
        student_target = 310

        # Programs 1-12 mapping (Program ID -> (Name, Degree, Duration, Dept))
        # Distribute students: ~26-30 per program
        students_per_program = 26

        roll_counter = 1

        for prog_id in range(1, 13):
            # Target count for this program
            count = 26 if prog_id <= 10 else 25

            for _ in range(count):
                first_name = self.faker.first_name()
                last_name = self.faker.last_name()
                email = f"{first_name.lower()}.{last_name.lower()}.s{person_id}@university.edu"
                dob = self.faker.date_of_birth(minimum_age=18, maximum_age=26)
                gender = random.choice(['M', 'F', 'Other']) if random.random() > 0.05 else 'Other'

                person_row = {
                    "person_id": person_id,
                    "first_name": first_name,
                    "last_name": last_name,
                    "email": email,
                    "date_of_birth": dob,
                    "gender": gender,
                    "address_line": self.faker.street_address().replace("'", "").replace("\n", ", ").replace("\r", ""),
                    "city": self.faker.city().replace("'", ""),
                    "state": self.faker.state().replace("'", ""),
                    "pin_code": str(random.randint(110001, 700099))
                }
                self.persons.append(person_row)

                # Phone number
                phone_row = {
                    "person_id": person_id,
                    "phone_no": f"9{random.randint(100000000, 999999999)}",
                    "phone_type": "Mobile"
                }
                self.person_phones.append(phone_row)

                # Determine admission year & semester
                # Cohorts admitted in 2021, 2022, 2023, 2024, 2025
                admission_year = random.choices([2021, 2022, 2023, 2024, 2025], weights=[0.15, 0.20, 0.25, 0.25, 0.15])[0]
                admission_date = date(admission_year, 8, 1)

                # Semester calculation relative to current term 2026-27 Odd
                years_enrolled = 2026 - admission_year
                current_sem = min(8, max(1, years_enrolled * 2 + 1))

                # Status
                if current_sem >= 8 and admission_year <= 2021:
                    status = random.choice(['Active', 'Graduated'])
                elif random.random() < 0.02:
                    status = 'Suspended'
                elif random.random() < 0.02:
                    status = 'Dropped'
                else:
                    status = 'Active'

                roll_no = f"{admission_year}P{prog_id:02d}{roll_counter:04d}"

                student_row = {
                    "student_id": person_id,
                    "roll_no": roll_no,
                    "program_id": prog_id,
                    "admission_date": admission_date,
                    "status": status,
                    "current_semester": current_sem
                }
                self.students.append(student_row)

                # Latent academic tier (Correlations with noise)
                # 65% Strong/High, 20% Moderate, 10% Low/At-risk, 5% Surprise Cases
                rand_val = random.random()
                if rand_val < 0.65:
                    tier = "HIGH"
                    att_mean, att_sd = 0.90, 0.05
                    score_mean, score_sd = 80.0, 8.0
                    fee_late_prob = 0.05
                elif rand_val < 0.85:
                    tier = "MODERATE"
                    att_mean, att_sd = 0.78, 0.06
                    score_mean, score_sd = 65.0, 10.0
                    fee_late_prob = 0.20
                elif rand_val < 0.95:
                    tier = "AT_RISK"
                    att_mean, att_sd = 0.58, 0.10
                    score_mean, score_sd = 36.0, 12.0
                    fee_late_prob = 0.60
                elif rand_val < 0.975:
                    # Surprise Case 1: High Attendance but failing (Struggling Striver)
                    tier = "SURPRISE_STRIVER"
                    att_mean, att_sd = 0.92, 0.04
                    score_mean, score_sd = 34.0, 6.0
                    fee_late_prob = 0.10
                else:
                    # Surprise Case 2: Low Attendance but high marks (Talented Slacker)
                    tier = "SURPRISE_SLACKER"
                    att_mean, att_sd = 0.62, 0.06
                    score_mean, score_sd = 82.0, 7.0
                    fee_late_prob = 0.30

                self.student_profiles[person_id] = {
                    "tier": tier,
                    "att_mean": att_mean,
                    "att_sd": att_sd,
                    "score_mean": score_mean,
                    "score_sd": score_sd,
                    "fee_late_prob": fee_late_prob,
                    "admission_year": admission_year,
                    "program_id": prog_id,
                    "passed_subjects": set()  # Track passed subjects for prerequisite check
                }

                # Student User Account (sample of students get login accounts)
                if person_id % 3 == 0:  # ~100 student accounts
                    username = f"stu_{roll_no.lower()}"
                    self.users.append({
                        "person_id": person_id,
                        "username": username,
                        "password_hash": self.bcrypt_hash,
                        "role": "Student",
                        "is_active": True,
                        "last_login": datetime.now() - timedelta(days=random.randint(1, 14))
                    })

                person_id += 1
                roll_counter += 1

    # -------------------------------------------------------------------------
    # 4. Course Offerings & Timetable Slots across 6 Terms
    # -------------------------------------------------------------------------
    def generate_course_offerings_and_timetable(self):
        print("[5/9] Generating Course Offerings across 6 Terms and conflict-free Timetable slots...")
        offering_id = 1
        slot_id = 1

        # Classrooms (1 to 8 from V5)
        room_ids = list(range(1, 9))
        days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday']
        time_slots = [
            ("09:00:00", "10:00:00"),
            ("10:00:00", "11:00:00"),
            ("11:15:00", "12:15:00"),
            ("13:00:00", "14:00:00"),
            ("14:00:00", "15:00:00"),
            ("15:15:00", "16:15:00")
        ]

        # Program to Department mapping
        PROG_TO_DEPT = {1: 1, 2: 1, 3: 1, 4: 2, 5: 3, 6: 4, 7: 5, 8: 6, 9: 1, 10: 2, 11: 3, 12: 6}

        # Department faculty mapping: dept_id -> list of faculty_ids
        dept_faculty: Dict[int, List[int]] = {}
        for fac in self.faculty:
            dept_faculty.setdefault(fac["department_id"], []).append(fac["faculty_id"])

        for term in TERMS:
            t_id = term["term_id"]
            is_odd = (t_id % 2 != 0)
            status = "Completed" if t_id <= 4 else "Open"

            # Conflict tracking per term: (faculty_id, day, start_time) and (room_id, day, start_time)
            faculty_schedule: set = set()
            room_schedule: set = set()

            # Select subjects to offer based on term semester parity
            # Odd terms offer odd-semester subjects; Even terms offer even-semester subjects
            term_subjects = [s for s in self.subjects if (s["semester"] % 2 != 0) == is_odd]

            for sub in term_subjects:
                s_id = sub["subject_id"]
                dept_id = sub["department_id"]
                available_faculty = dept_faculty.get(dept_id, [1])

                # Determine sections: high-enrollment core departments (CS, ECE, Mech, Math) get sections A and B
                sections_to_create = ["A"]
                if dept_id in (1, 2, 3, 5) and len(available_faculty) >= 2:
                    sections_to_create = ["A", "B"]

                for sec_idx, sec_name in enumerate(sections_to_create):
                    # Pick different faculty for different sections if possible
                    faculty_id = available_faculty[sec_idx % len(available_faculty)]
                    capacity = 60

                    offering_row = {
                        "offering_id": offering_id,
                        "subject_id": s_id,
                        "faculty_id": faculty_id,
                        "term_id": t_id,
                        "section": sec_name,
                        "capacity": capacity,
                        "status": status
                    }
                    self.offerings.append(offering_row)

                    # Assessments for this offering (weightage sum = 100)
                    self.assessments.append({"offering_id": offering_id, "assessment_no": 1, "title": "Internal Assessment 1", "max_marks": 50,  "weightage": 20, "assessment_date": term["start"] + timedelta(days=35)})
                    self.assessments.append({"offering_id": offering_id, "assessment_no": 2, "title": "Internal Assessment 2", "max_marks": 50,  "weightage": 20, "assessment_date": term["start"] + timedelta(days=70)})
                    self.assessments.append({"offering_id": offering_id, "assessment_no": 3, "title": "Assignment & Lab Work",  "max_marks": 25,  "weightage": 10, "assessment_date": term["start"] + timedelta(days=85)})
                    self.assessments.append({"offering_id": offering_id, "assessment_no": 4, "title": "End Term Examination",  "max_marks": 100, "weightage": 50, "assessment_date": term["end"]   - timedelta(days=7)})

                    # Assign 2 timetable slots per week without clashes
                    slots_assigned = 0
                    attempts = 0
                    while slots_assigned < 2 and attempts < 150:
                        attempts += 1
                        day = random.choice(days)
                        st_time, end_time = random.choice(time_slots)
                        room = random.choice(room_ids)

                        fac_key = (faculty_id, day, st_time)
                        room_key = (room, day, st_time)

                        if fac_key not in faculty_schedule and room_key not in room_schedule:
                            faculty_schedule.add(fac_key)
                            room_schedule.add(room_key)

                            self.timetable_slots.append({
                                "slot_id": slot_id,
                                "offering_id": offering_id,
                                "room_id": room,
                                "day_of_week": day,
                                "start_time": st_time,
                                "end_time": end_time
                            })
                            slot_id += 1
                            slots_assigned += 1

                    offering_id += 1

    # -------------------------------------------------------------------------
    # 5. Enrollments (~1,800 enrollments respecting credit limits & prerequisites)
    # -------------------------------------------------------------------------
    def generate_enrollments_and_academics(self):
        print("[6/9] Generating ~1,800 Enrollments respecting 26-credit limit & prerequisites...")
        enrollment_id = 1

        PROG_TO_DEPT = {1: 1, 2: 1, 3: 1, 4: 2, 5: 3, 6: 4, 7: 5, 8: 6, 9: 1, 10: 2, 11: 3, 12: 6}

        # Offering lookup by term: term_id -> list of offerings
        offerings_by_term: Dict[int, List[Dict[str, Any]]] = {}
        for off in self.offerings:
            offerings_by_term.setdefault(off["term_id"], []).append(off)

        # Subject lookup by ID
        subject_by_id = {s["subject_id"]: s for s in self.subjects}

        # Track offering enrollment counts: offering_id -> count
        offering_enrollment_count: Dict[int, int] = {off["offering_id"]: 0 for off in self.offerings}

        # Track subjects taken by each student across terms to prevent duplicate passes
        student_completed_subjects: Dict[int, set] = {s["student_id"]: set() for s in self.students}

        for term in TERMS:
            t_id = term["term_id"]
            term_offs = offerings_by_term.get(t_id, [])

            # For each active student, determine if they enroll in this term
            for student in self.students:
                s_id = student["student_id"]
                profile = self.student_profiles[s_id]

                # Check if student was admitted on or before this term start
                if profile["admission_year"] > term["start"].year:
                    continue
                if student["status"] in ('Dropped', 'Suspended') and random.random() > 0.2:
                    continue

                # Credits enrolled this term
                term_credits = 0
                term_enrolled_subjects = set()
                dept_id = PROG_TO_DEPT.get(student["program_id"], 1)

                # Prioritize department offerings, then common science/math electives, then others
                dept_offerings = [o for o in term_offs if subject_by_id[o["subject_id"]]["department_id"] == dept_id]
                elective_offerings = [o for o in term_offs if subject_by_id[o["subject_id"]]["department_id"] != dept_id]
                random.shuffle(dept_offerings)
                random.shuffle(elective_offerings)

                candidate_offerings = dept_offerings + elective_offerings

                for off in candidate_offerings:
                    off_id = off["offering_id"]
                    sub = subject_by_id[off["subject_id"]]
                    s_code = sub["subject_id"]

                    # Do not enroll in same subject twice in the same term
                    if s_code in term_enrolled_subjects:
                        continue

                    # Do not re-enroll in already passed subjects
                    if s_code in student_completed_subjects[s_id]:
                        continue

                    # Rule 1: Capacity check
                    if offering_enrollment_count[off_id] >= off["capacity"]:
                        continue

                    # Rule 2: 26-Credit Limit check
                    if term_credits + sub["credits"] > 26:
                        continue

                    # Rule 3: Prerequisite check (Must have passed all prerequisites)
                    prereqs = self.prereqs_by_subject_id.get(s_code, [])
                    if prereqs and not all(p in profile["passed_subjects"] for p in prereqs):
                        continue

                    # Proceed with enrollment
                    offering_enrollment_count[off_id] += 1
                    term_credits += sub["credits"]
                    term_enrolled_subjects.add(s_code)

                    enroll_status = "Completed" if t_id <= 4 else "Enrolled"
                    enroll_date = term["start"] - timedelta(days=random.randint(1, 10))

                    enrollment_row = {
                        "enrollment_id": enrollment_id,
                        "student_id": s_id,
                        "offering_id": off_id,
                        "enroll_date": enroll_date,
                        "status": enroll_status,
                        "final_marks": None,
                        "grade": None,
                        # Internal helpers
                        "_term_id": t_id,
                        "_subject_id": s_code,
                        "_profile": profile
                    }
                    self.enrollments.append(enrollment_row)
                    enrollment_id += 1

                    # Standard course load is 18 to 22 credits per semester
                    if term_credits >= 18:
                        break

    # -------------------------------------------------------------------------
    # 6. Marks & Attendance (~9,000 marks, ~70,000 attendance rows)
    # -------------------------------------------------------------------------
    def generate_marks_and_attendance(self):
        print("[7/9] Simulating ~70,000 Attendance records & ~9,000 Marks with academic correlations...")

        # Term lookup
        term_by_id = {t["term_id"]: t for t in TERMS}

        # Timetable slots by offering: offering_id -> list of (day, start_time)
        slots_by_offering: Dict[int, List[Dict[str, Any]]] = {}
        for slot in self.timetable_slots:
            slots_by_offering.setdefault(slot["offering_id"], []).append(slot)

        # Assessments by offering: offering_id -> list of assessments
        assessments_by_offering: Dict[int, List[Dict[str, Any]]] = {}
        for ass in self.assessments:
            assessments_by_offering.setdefault(ass["offering_id"], []).append(ass)

        day_name_to_int = {
            'Monday': 0, 'Tuesday': 1, 'Wednesday': 2, 'Thursday': 3,
            'Friday': 4, 'Saturday': 5, 'Sunday': 6
        }

        for enr in self.enrollments:
            enr_id = enr["enrollment_id"]
            off_id = enr["offering_id"]
            t_id = enr["_term_id"]
            term = term_by_id[t_id]
            profile = enr["_profile"]
            sub_id = enr["_subject_id"]

            # -----------------------------------------------------------------
            # Attendance Simulation
            # -----------------------------------------------------------------
            # Determine class meeting dates for this offering
            off_slots = slots_by_offering.get(off_id, [])
            meeting_days = {day_name_to_int[s["day_of_week"]] for s in off_slots}

            # Generate weekly session dates
            current_date = term["start"]
            cutoff_date = CURRENT_TERM_CUTOFF_DATE if t_id == 5 else term["end"]

            # Student attendance probability
            att_prob = min(0.99, max(0.35, random.gauss(profile["att_mean"], profile["att_sd"])))

            classes_held = 0
            classes_attended = 0

            while current_date <= cutoff_date:
                if current_date.weekday() in meeting_days:
                    classes_held += 1
                    # Roll status
                    roll = random.random()
                    if roll < att_prob:
                        status = 'Present'
                        classes_attended += 1
                    elif roll < att_prob + 0.04:
                        status = 'Late'
                        classes_attended += 0.5  # Counted partially
                    else:
                        status = 'Absent'

                    self.attendance.append({
                        "enrollment_id": enr_id,
                        "class_date": current_date,
                        "status": status
                    })
                current_date += timedelta(days=1)

            # -----------------------------------------------------------------
            # Marks Simulation (Correlated with Attendance & Latent Ability)
            # -----------------------------------------------------------------
            actual_att_pct = (classes_attended / max(1, classes_held)) * 100.0

            # Base score derived from profile, with correlation to actual attendance
            base_score = random.gauss(profile["score_mean"], profile["score_sd"])
            # Attendance synergy factor
            att_impact = (actual_att_pct - 75.0) * 0.25
            total_score_pct = min(98.0, max(15.0, base_score + att_impact))

            # Retrieve assessments for this offering
            asses = assessments_by_offering.get(off_id, [])

            weighted_sum = 0.0

            for ass in asses:
                ass_no = ass["assessment_no"]
                max_m = ass["max_marks"]
                wt = ass["weightage"]

                # For Term 5 (Current), only assessments 1, 2, and 3 are conducted so far
                if t_id == 5 and ass_no == 4:
                    continue
                # For Term 6 (Upcoming), no marks yet
                if t_id == 6:
                    continue

                # Add assessment-specific variance
                score_pct = min(100.0, max(5.0, random.gauss(total_score_pct, 6.0)))
                marks_obtained = round((score_pct / 100.0) * max_m, 1)
                marks_obtained = min(float(max_m), max(0.0, marks_obtained))

                self.marks.append({
                    "enrollment_id": enr_id,
                    "offering_id": off_id,
                    "assessment_no": ass_no,
                    "marks_obtained": marks_obtained,
                    "version": 1
                })

                weighted_sum += (marks_obtained / max_m) * wt

            # For completed terms (1 to 4), finalize grade & marks in enrollment
            if t_id <= 4:
                final_percentage = round(weighted_sum, 2)
                grade_letter, grade_pt = calculate_grade(final_percentage)

                enr["final_marks"] = final_percentage
                enr["grade"] = grade_letter

                # If passed, register in student's passed_subjects for prerequisite resolution
                if grade_pt > 0:
                    profile["passed_subjects"].add(sub_id)

    # -------------------------------------------------------------------------
    # 7. Fee Payments Generation
    # -------------------------------------------------------------------------
    def generate_fee_payments(self):
        print("[8/9] Generating Fee Payments across terms with correlation to risk profile...")
        payment_id = 1
        modes = ['Online', 'Card', 'Online', 'Cash', 'Cheque']

        for student in self.students:
            s_id = student["student_id"]
            profile = self.student_profiles[s_id]

            for term in TERMS:
                t_id = term["term_id"]
                if profile["admission_year"] > term["start"].year:
                    continue

                # Amount (INR 25,000 to 45,000)
                amount = random.choice([25000.00, 32000.00, 40000.00, 45000.00])

                # Determine payment timing & status
                is_late = random.random() < profile["fee_late_prob"]

                if t_id <= 4:
                    status = 'Paid'
                    if is_late:
                        p_date = term["start"] + timedelta(days=random.randint(15, 60))
                    else:
                        p_date = term["start"] - timedelta(days=random.randint(5, 20))
                elif t_id == 5:
                    # Current term
                    if is_late:
                        status = random.choice(['Pending', 'Failed', 'Paid'])
                        p_date = term["start"] + timedelta(days=random.randint(10, 45))
                    else:
                        status = 'Paid'
                        p_date = term["start"] - timedelta(days=random.randint(5, 20))
                else:
                    # Upcoming term
                    status = random.choice(['Pending', 'Pending', 'Paid'])
                    p_date = term["start"] - timedelta(days=random.randint(1, 10))

                self.fee_payments.append({
                    "payment_id": payment_id,
                    "student_id": s_id,
                    "term_id": t_id,
                    "amount": amount,
                    "payment_date": p_date,
                    "payment_mode": random.choice(modes),
                    "status": status
                })
                payment_id += 1

    # -------------------------------------------------------------------------
    # 8. Administrative Users Generation (Admin & Registrar)
    # -------------------------------------------------------------------------
    def generate_admin_and_registrar(self):
        print("[9/9] Generating Admin, Registrar, and verified user accounts...")
        # Admin account
        self.users.insert(0, {
            "person_id": None,
            "username": "admin",
            "password_hash": self.bcrypt_hash,
            "role": "Admin",
            "is_active": True,
            "last_login": datetime.now() - timedelta(hours=2)
        })

        # Registrar account
        self.users.insert(1, {
            "person_id": None,
            "username": "registrar",
            "password_hash": self.bcrypt_hash,
            "role": "Registrar",
            "is_active": True,
            "last_login": datetime.now() - timedelta(hours=5)
        })


# =============================================================================
# Database Operations: Truncation, Batch Insert, and SQL Dump
# =============================================================================
def truncate_tables_safely(cursor):
    """Safely truncate operational tables respecting foreign keys."""
    print("\n--- Resetting operational database state ---")
    cursor.execute("SET FOREIGN_KEY_CHECKS = 0;")
    tables_to_truncate = [
        "audit_log",
        "student_risk",
        "marks",
        "assessment",
        "attendance",
        "enrollment",
        "timetable_slot",
        "course_offering",
        "fee_payment",
        "users",
        "student",
        "faculty",
        "person_phone",
        "person",
        "subject_prerequisite",
        "subject"
    ]
    for tbl in tables_to_truncate:
        cursor.execute(f"TRUNCATE TABLE `{tbl}`;")

    # Clear circular HOD reference before repopulating
    cursor.execute("UPDATE department SET hod_faculty_id = NULL;")
    cursor.execute("SET FOREIGN_KEY_CHECKS = 1;")
    print("Truncated 16 dynamic tables successfully.")


def ensure_twelve_programs(cursor):
    """Ensure all 12 programs exist in the program table."""
    programs_sql = """
    INSERT IGNORE INTO program (program_id, name, degree, duration_years, department_id, total_credits) VALUES
        (1,  'B.Sc. Computer Science',           'B.Sc.',   3, 1, 140),
        (2,  'M.Sc. Computer Science',           'M.Sc.',   2, 1,  80),
        (3,  'Ph.D. Computer Science',           'Ph.D.',   5, 1,  24),
        (4,  'B.E. Electronics & Communication', 'B.E.',    4, 2, 160),
        (5,  'B.E. Mechanical Engineering',      'B.E.',    4, 3, 160),
        (6,  'B.E. Civil Engineering',            'B.E.',    4, 4, 160),
        (7,  'M.Sc. Mathematics',                'M.Sc.',   2, 5,  80),
        (8,  'M.Sc. Physics',                     'M.Sc.',   2, 6,  80),
        (9,  'M.Tech. Computer Science',           'M.Tech.', 2, 1,  72),
        (10, 'M.Tech. Electronics & Communication', 'M.Tech.', 2, 2,  72),
        (11, 'Ph.D. Mechanical Engineering',      'Ph.D.',   5, 3,  24),
        (12, 'Ph.D. Physics',                     'Ph.D.',   5, 6,  24);
    """
    cursor.execute(programs_sql)


def execute_batch_inserts(cursor, generator: SeedDataGenerator):
    """Insert generated dataset in batches using parameterized SQL queries."""
    print("\n--- Executing Parameterized Batch Inserts ---")

    # 1. Person
    print(f"Inserting {len(generator.persons)} person rows...")
    sql_person = """
        INSERT INTO person (person_id, first_name, last_name, email, date_of_birth, gender, address_line, city, state, pin_code)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
    """
    person_params = [
        (p["person_id"], p["first_name"], p["last_name"], p["email"], p["date_of_birth"],
         p["gender"], p["address_line"], p["city"], p["state"], p["pin_code"])
        for p in generator.persons
    ]
    cursor.executemany(sql_person, person_params)

    # 2. Person Phone
    print(f"Inserting {len(generator.person_phones)} person_phone rows...")
    sql_phone = "INSERT INTO person_phone (person_id, phone_no, phone_type) VALUES (%s, %s, %s)"
    phone_params = [(p["person_id"], p["phone_no"], p["phone_type"]) for p in generator.person_phones]
    cursor.executemany(sql_phone, phone_params)

    # 3. Faculty
    print(f"Inserting {len(generator.faculty)} faculty rows...")
    sql_fac = "INSERT INTO faculty (faculty_id, employee_no, department_id, designation, hire_date, max_load) VALUES (%s, %s, %s, %s, %s, %s)"
    fac_params = [(f["faculty_id"], f["employee_no"], f["department_id"], f["designation"], f["hire_date"], f["max_load"]) for f in generator.faculty]
    cursor.executemany(sql_fac, fac_params)

    # Update HOD in Department (Circular Foreign Key)
    print("Updating Department HOD foreign keys...")
    for dept_id, hod_id in generator.hod_assignments.items():
        cursor.execute("UPDATE department SET hod_faculty_id = %s WHERE department_id = %s", (hod_id, dept_id))

    # 4. Subjects
    print(f"Inserting {len(generator.subjects)} subject rows with descriptions...")
    sql_sub = "INSERT INTO subject (subject_id, code, title, credits, department_id, program_id, semester, description) VALUES (%s, %s, %s, %s, %s, %s, %s, %s)"
    sub_params = [(s["subject_id"], s["code"], s["title"], s["credits"], s["department_id"], s["program_id"], s["semester"], s["description"]) for s in generator.subjects]
    cursor.executemany(sql_sub, sub_params)

    # 5. Subject Prerequisites
    print(f"Inserting {len(generator.subject_prereqs)} subject_prerequisite rows...")
    sql_prereq = "INSERT INTO subject_prerequisite (subject_id, prereq_subject_id, min_grade) VALUES (%s, %s, %s)"
    prereq_params = [(p["subject_id"], p["prereq_subject_id"], p["min_grade"]) for p in generator.subject_prereqs]
    cursor.executemany(sql_prereq, prereq_params)

    # 6. Students
    print(f"Inserting {len(generator.students)} student rows...")
    sql_stu = "INSERT INTO student (student_id, roll_no, program_id, admission_date, status, current_semester) VALUES (%s, %s, %s, %s, %s, %s)"
    stu_params = [(s["student_id"], s["roll_no"], s["program_id"], s["admission_date"], s["status"], s["current_semester"]) for s in generator.students]
    cursor.executemany(sql_stu, stu_params)

    # 7. Users
    print(f"Inserting {len(generator.users)} users with BCrypt password hashes...")
    sql_usr = "INSERT INTO users (person_id, username, password_hash, `role`, is_active, last_login) VALUES (%s, %s, %s, %s, %s, %s)"
    usr_params = [(u["person_id"], u["username"], u["password_hash"], u["role"], u["is_active"], u["last_login"]) for u in generator.users]
    cursor.executemany(sql_usr, usr_params)

    # 8. Course Offerings
    print(f"Inserting {len(generator.offerings)} course_offering rows across 6 terms...")
    sql_off = "INSERT INTO course_offering (offering_id, subject_id, faculty_id, term_id, section, capacity, status) VALUES (%s, %s, %s, %s, %s, %s, %s)"
    off_params = [(o["offering_id"], o["subject_id"], o["faculty_id"], o["term_id"], o["section"], o["capacity"], o["status"]) for o in generator.offerings]
    cursor.executemany(sql_off, off_params)

    # 9. Timetable Slots
    print(f"Inserting {len(generator.timetable_slots)} timetable_slot rows...")
    sql_slot = "INSERT INTO timetable_slot (slot_id, offering_id, room_id, day_of_week, start_time, end_time) VALUES (%s, %s, %s, %s, %s, %s)"
    slot_params = [(t["slot_id"], t["offering_id"], t["room_id"], t["day_of_week"], t["start_time"], t["end_time"]) for t in generator.timetable_slots]
    cursor.executemany(sql_slot, slot_params)

    # 10. Assessments
    print(f"Inserting {len(generator.assessments)} assessment rows...")
    sql_ass = "INSERT INTO assessment (offering_id, assessment_no, title, max_marks, weightage, assessment_date) VALUES (%s, %s, %s, %s, %s, %s)"
    ass_params = [(a["offering_id"], a["assessment_no"], a["title"], a["max_marks"], a["weightage"], a["assessment_date"]) for a in generator.assessments]
    cursor.executemany(sql_ass, ass_params)

    # 11. Enrollments
    print(f"Inserting {len(generator.enrollments)} enrollment rows...")
    sql_enr = "INSERT INTO enrollment (enrollment_id, student_id, offering_id, enroll_date, status, final_marks, grade) VALUES (%s, %s, %s, %s, %s, %s, %s)"
    enr_params = [(e["enrollment_id"], e["student_id"], e["offering_id"], e["enroll_date"], e["status"], e["final_marks"], e["grade"]) for e in generator.enrollments]
    cursor.executemany(sql_enr, enr_params)

    # 12. Marks (in chunks)
    print(f"Inserting {len(generator.marks)} marks rows...")
    sql_marks = "INSERT INTO marks (enrollment_id, offering_id, assessment_no, marks_obtained, version) VALUES (%s, %s, %s, %s, %s)"
    marks_params = [(m["enrollment_id"], m["offering_id"], m["assessment_no"], m["marks_obtained"], m["version"]) for m in generator.marks]
    for i in range(0, len(marks_params), BATCH_SIZE):
        cursor.executemany(sql_marks, marks_params[i:i + BATCH_SIZE])

    # 13. Attendance (in chunks)
    print(f"Inserting {len(generator.attendance)} attendance rows...")
    sql_att = "INSERT INTO attendance (enrollment_id, class_date, status) VALUES (%s, %s, %s)"
    att_params = [(a["enrollment_id"], a["class_date"], a["status"]) for a in generator.attendance]
    for i in range(0, len(att_params), BATCH_SIZE):
        cursor.executemany(sql_att, att_params[i:i + BATCH_SIZE])

    # 14. Fee Payments
    print(f"Inserting {len(generator.fee_payments)} fee_payment rows...")
    sql_fee = "INSERT INTO fee_payment (payment_id, student_id, term_id, amount, payment_date, payment_mode, status) VALUES (%s, %s, %s, %s, %s, %s, %s)"
    fee_params = [(f["payment_id"], f["student_id"], f["term_id"], f["amount"], f["payment_date"], f["payment_mode"], f["status"]) for f in generator.fee_payments]
    cursor.executemany(sql_fee, fee_params)


def dump_sql_file(output_path: str, generator: SeedDataGenerator):
    """Write the complete seed dataset as standard executable SQL for offline loading."""
    print(f"\nWriting complete executable SQL dump to: {output_path}...")
    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)

    with open(output_path, "w", encoding="utf-8") as f:
        f.write("-- =============================================================================\n")
        f.write("-- database/seed/seed_data.sql\n")
        f.write("-- Auto-generated realistic seed dataset for Education Management System\n")
        f.write("-- =============================================================================\n\n")
        f.write("SET FOREIGN_KEY_CHECKS = 0;\n")

        tables = ["audit_log", "student_risk", "marks", "assessment", "attendance",
                  "enrollment", "timetable_slot", "course_offering", "fee_payment",
                  "users", "student", "faculty", "person_phone", "person",
                  "subject_prerequisite", "subject"]
        for tbl in tables:
            f.write(f"TRUNCATE TABLE `{tbl}`;\n")
        f.write("UPDATE department SET hod_faculty_id = NULL;\n\n")

        # Program inserts
        f.write("-- Ensure 12 Programs\n")
        f.write("INSERT IGNORE INTO program (program_id, name, degree, duration_years, department_id, total_credits) VALUES\n")
        f.write("  (1,  'B.Sc. Computer Science',           'B.Sc.',   3, 1, 140),\n")
        f.write("  (2,  'M.Sc. Computer Science',           'M.Sc.',   2, 1,  80),\n")
        f.write("  (3,  'Ph.D. Computer Science',           'Ph.D.',   5, 1,  24),\n")
        f.write("  (4,  'B.E. Electronics & Communication', 'B.E.',    4, 2, 160),\n")
        f.write("  (5,  'B.E. Mechanical Engineering',      'B.E.',    4, 3, 160),\n")
        f.write("  (6,  'B.E. Civil Engineering',            'B.E.',    4, 4, 160),\n")
        f.write("  (7,  'M.Sc. Mathematics',                'M.Sc.',   2, 5,  80),\n")
        f.write("  (8,  'M.Sc. Physics',                     'M.Sc.',   2, 6,  80),\n")
        f.write("  (9,  'M.Tech. Computer Science',           'M.Tech.', 2, 1,  72),\n")
        f.write("  (10, 'M.Tech. Electronics & Communication', 'M.Tech.', 2, 2,  72),\n")
        f.write("  (11, 'Ph.D. Mechanical Engineering',      'Ph.D.',   5, 3,  24),\n")
        f.write("  (12, 'Ph.D. Physics',                     'Ph.D.',   5, 6,  24);\n\n")

        def sql_escape(val):
            if val is None:
                return "NULL"
            if isinstance(val, (int, float)):
                return str(val)
            if isinstance(val, (date, datetime)):
                return f"'{val}'"
            if isinstance(val, bool):
                return "TRUE" if val else "FALSE"
            clean = str(val).replace("\\", "\\\\").replace("'", "''")
            return f"'{clean}'"

        # Helper to dump table in chunks
        def write_insert_block(tbl_name, columns, rows):
            if not rows:
                return
            col_list = ", ".join(f"`{c}`" for c in columns)
            chunk_size = 500
            for i in range(0, len(rows), chunk_size):
                chunk = rows[i:i + chunk_size]
                f.write(f"INSERT INTO `{tbl_name}` ({col_list}) VALUES\n")
                lines = []
                for r in chunk:
                    vals = ", ".join(sql_escape(r[c]) for c in columns)
                    lines.append(f"  ({vals})")
                f.write(",\n".join(lines) + ";\n\n")

        # Write each table
        write_insert_block("person", ["person_id", "first_name", "last_name", "email", "date_of_birth", "gender", "address_line", "city", "state", "pin_code"], generator.persons)
        write_insert_block("person_phone", ["person_id", "phone_no", "phone_type"], generator.person_phones)
        write_insert_block("faculty", ["faculty_id", "employee_no", "department_id", "designation", "hire_date", "max_load"], generator.faculty)

        for dept_id, hod_id in generator.hod_assignments.items():
            f.write(f"UPDATE department SET hod_faculty_id = {hod_id} WHERE department_id = {dept_id};\n")
        f.write("\n")

        write_insert_block("subject", ["subject_id", "code", "title", "credits", "department_id", "program_id", "semester", "description"], generator.subjects)
        write_insert_block("subject_prerequisite", ["subject_id", "prereq_subject_id", "min_grade"], generator.subject_prereqs)
        write_insert_block("student", ["student_id", "roll_no", "program_id", "admission_date", "status", "current_semester"], generator.students)
        write_insert_block("users", ["person_id", "username", "password_hash", "role", "is_active", "last_login"], generator.users)
        write_insert_block("course_offering", ["offering_id", "subject_id", "faculty_id", "term_id", "section", "capacity", "status"], generator.offerings)
        write_insert_block("timetable_slot", ["slot_id", "offering_id", "room_id", "day_of_week", "start_time", "end_time"], generator.timetable_slots)
        write_insert_block("assessment", ["offering_id", "assessment_no", "title", "max_marks", "weightage", "assessment_date"], generator.assessments)
        write_insert_block("enrollment", ["enrollment_id", "student_id", "offering_id", "enroll_date", "status", "final_marks", "grade"], generator.enrollments)
        write_insert_block("marks", ["enrollment_id", "offering_id", "assessment_no", "marks_obtained", "version"], generator.marks)
        write_insert_block("attendance", ["enrollment_id", "class_date", "status"], generator.attendance)
        write_insert_block("fee_payment", ["payment_id", "student_id", "term_id", "amount", "payment_date", "payment_mode", "status"], generator.fee_payments)

        f.write("SET FOREIGN_KEY_CHECKS = 1;\n")

    print(f"SQL file generated successfully ({os.path.getsize(output_path) / (1024*1024):.2f} MB).")


def print_summary(generator: SeedDataGenerator):
    """Print formatted summary of generated row counts and correlation statistics."""
    print("\n" + "=" * 70)
    print("           SEED DATA GENERATION SUMMARY (DBMS VERIFICATION)          ")
    print("=" * 70)
    print(f"  Faculty Count          : {len(generator.faculty):>6}  (Across 6 departments)")
    print(f"  Subjects Catalog       : {len(generator.subjects):>6}  (With TF-IDF descriptions)")
    print(f"  Subject Prerequisites  : {len(generator.subject_prereqs):>6}  (Prerequisite DAG rules)")
    print(f"  Student Body           : {len(generator.students):>6}  (Across 12 programs)")
    print(f"  Course Offerings       : {len(generator.offerings):>6}  (Across 6 academic terms)")
    print(f"  Timetable Slots        : {len(generator.timetable_slots):>6}  (Conflict-free room & faculty)")
    print(f"  Assessments Configured : {len(generator.assessments):>6}  (Weightages total 100%)")
    print(f"  Student Enrollments    : {len(generator.enrollments):>6}  (<=26 credits/term limit)")
    print(f"  Marks Records          : {len(generator.marks):>6}  (Bounded by [0, max_marks])")
    print(f"  Attendance Records     : {len(generator.attendance):>6}  (Present/Absent/Late)")
    print(f"  Fee Payment Records    : {len(generator.fee_payments):>6}  (Paid/Pending/Failed)")
    print(f"  System Users (BCrypt)  : {len(generator.users):>6}  (Admin, Registrar, Faculty, Students)")
    print("=" * 70)

    # Academic correlation spot check
    completed_enrs = [e for e in generator.enrollments if e["final_marks"] is not None]
    if completed_enrs:
        # Calculate correlation metrics for verification
        enr_ids_high_att = set()
        enr_ids_low_att = set()

        att_by_enr: Dict[int, List[str]] = {}
        for a in generator.attendance:
            att_by_enr.setdefault(a["enrollment_id"], []).append(a["status"])

        marks_high = []
        marks_low = []

        for e in completed_enrs:
            eid = e["enrollment_id"]
            records = att_by_enr.get(eid, [])
            if records:
                present = sum(1 for s in records if s in ('Present', 'Late'))
                pct = (present / len(records)) * 100.0
                if pct >= 85.0:
                    marks_high.append(e["final_marks"])
                elif pct < 70.0:
                    marks_low.append(e["final_marks"])

        avg_high = sum(marks_high) / max(1, len(marks_high))
        avg_low = sum(marks_low) / max(1, len(marks_low))

        print("\n  Statistical Correlation Spot-Check:")
        print(f"    - Avg Marks (Attendance >= 85%): {avg_high:.2f}%  (n={len(marks_high)})")
        print(f"    - Avg Marks (Attendance <  70%): {avg_low:.2f}%  (n={len(marks_low)})")
        print(f"    - Empirical Performance Gap    : {avg_high - avg_low:+.2f}%")
        print("    - Surprise Cases Modeled       : Striving Failures & Talented Slackers included.")
    print("=" * 70)


# =============================================================================
# Main Entry Point
# =============================================================================
def main():
    parser = argparse.ArgumentParser(description="Seed Data Generator for Education and Academics Management System.")
    parser.add_argument("--host", default=os.getenv("DB_HOST", "localhost"), help="MySQL Host")
    parser.add_argument("--port", type=int, default=int(os.getenv("DB_PORT", "3306")), help="MySQL Port")
    parser.add_argument("--user", default=os.getenv("DB_USER", "flyway_user"), help="MySQL User (flyway_user or root)")
    parser.add_argument("--password", default=os.getenv("DB_PASSWORD", "ChangeMe_flyway_2026!"), help="MySQL Password")
    parser.add_argument("--database", default=os.getenv("DB_NAME", "edu_db"), help="Database Name")
    parser.add_argument("--dump-sql", default="database/seed/seed_data.sql", help="Path to write offline .sql dump file")
    parser.add_argument("--no-db", action="store_true", help="Generate offline SQL dump without connecting to database")

    args = parser.parse_args()

    # Step 1: Generate Dataset in memory
    print(f"Generating realistic dataset (Random seed = {RANDOM_SEED})...")
    gen = SeedDataGenerator(seed=RANDOM_SEED)
    gen.generate_faculty()
    gen.generate_subjects_and_prerequisites()
    gen.generate_students()
    gen.generate_course_offerings_and_timetable()
    gen.generate_enrollments_and_academics()
    gen.generate_marks_and_attendance()
    gen.generate_fee_payments()
    gen.generate_admin_and_registrar()

    # Step 2: Always generate the SQL dump file for offline loading / review
    if args.dump_sql:
        dump_sql_file(args.dump_sql, gen)

    # Step 3: Attempt database connection if not --no-db
    if not args.no_db:
        conn = None
        try:
            print(f"\nAttempting connection to MySQL: {args.user}@{args.host}:{args.port}/{args.database}...")
            if MYSQL_CONNECTOR_AVAILABLE:
                conn = mysql.connector.connect(
                    host=args.host,
                    port=args.port,
                    user=args.user,
                    password=args.password,
                    database=args.database,
                    autocommit=False
                )
            elif PYMYSQL_AVAILABLE:
                conn = pymysql.connect(
                    host=args.host,
                    port=args.port,
                    user=args.user,
                    password=args.password,
                    database=args.database,
                    autocommit=False
                )
            else:
                print("Warning: Neither mysql-connector nor pymysql is installed. Skipping live DB load.")

            if conn:
                cursor = conn.cursor()
                truncate_tables_safely(cursor)
                ensure_twelve_programs(cursor)
                execute_batch_inserts(cursor, gen)
                conn.commit()
                cursor.close()
                conn.close()
                print("\n[SUCCESS] All seed data committed to MySQL database successfully!")
        except Exception as e:
            print(f"\n[Note on MySQL Connection]: Could not connect to live MySQL instance ({e}).")
            print(f"-> This is normal if MySQL is not currently running or uses different credentials.")
            print(f"-> All seed records have been exported to '{args.dump_sql}'.")
            print(f"-> You can load them anytime via: mysql -u {args.user} -p {args.database} < {args.dump_sql}")

    # Step 4: Print verification summary
    print_summary(gen)


if __name__ == "__main__":
    main()
