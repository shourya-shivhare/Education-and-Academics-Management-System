# 10. Security Demonstration: SQL Injection Vulnerability vs. Parameterized Queries

> [!WARNING]
> **EDUCATIONAL DOCUMENTATION ONLY**  
> Per Project Rule 3: *"Use parameterized queries only in application code. No string-concatenated SQL anywhere except one clearly marked demo file for the SQL injection demonstration."*  
> This document serves as the **sole, isolated architectural demonstration** of SQL Injection risks and why parameterized queries (PreparedStatements) are strictly enforced across the entire Education and Academics Management System codebase.

---

## 1. The Anatomy of SQL Injection (SQLi)

SQL Injection occurs when untrusted user input is directly concatenated into a dynamic SQL statement string before being passed to the relational database engine.

In a relational database (such as MySQL 8), the query processor performs:
1. **Lexical analysis and parsing** into an Abstract Syntax Tree (AST).
2. **Semantic analysis** and schema binding.
3. **Query optimization** and execution plan generation.

When SQL statements are constructed via naive string concatenation, the database parser **cannot distinguish between SQL commands written by the developer and malicious SQL tokens supplied by an attacker**. Consequently, attacker input is parsed as executable code, fundamentally hijacking the query logic.

---

## 2. Attack Scenario 1: Authentication Bypass via `' OR '1'='1`

### A. The Vulnerable Implementation (Anti-Pattern)
Suppose a developer implements user login by concatenating HTTP form parameters into raw SQL:

```java
// =========================================================================
// ANTI-PATTERN: DO NOT USE IN PRODUCTION
// Vulnerable string-concatenated query
// =========================================================================
public User authenticateVulnerable(String inputUsername, String inputPassword) {
    String sql = "SELECT user_id, username, password_hash, role " +
                 "FROM users " +
                 "WHERE username = '" + inputUsername + "' " +
                 "  AND is_active = TRUE";

    // Executed via JdbcTemplate or Statement:
    return jdbcTemplate.queryForObject(sql, userRowMapper);
}
```

### B. The Malicious Payload
An attacker enters the following payload into the `username` field:
```text
admin' OR '1'='1' -- 
```

### C. The Resulting Executed SQL
When string concatenation occurs, the SQL statement sent to MySQL becomes:

```sql
SELECT user_id, username, password_hash, role 
FROM users 
WHERE username = 'admin' OR '1'='1' -- ' AND is_active = TRUE
```

### D. Step-by-Step Exploitation Analysis
1. The attacker's leading single quote (`'`) prematurely terminates the `username` string literal.
2. The boolean operator `OR '1'='1'` evaluates to `TRUE` for **every single row** in the `users` table.
3. The SQL comment sequence `-- ` (double dash followed by space) instructs the MySQL parser to ignore all remaining characters in the query line (effectively discarding `' AND is_active = TRUE`).
4. The database returns the first matching record in the table (typically the `Admin` account with `user_id = 1`).
5. **Impact:** The attacker gains complete Administrative control over the institution's academic portal without knowing the password or passing cryptographic hashing checks.

---

## 3. Attack Scenario 2: Data Exfiltration via `UNION SELECT`

### A. The Vulnerable Student Search
```java
// Vulnerable search by roll number:
String sql = "SELECT student_id, roll_no, program_id, current_semester " +
             "FROM student " +
             "WHERE roll_no = '" + searchRollNo + "'";
```

### B. The Malicious Exfiltration Payload
```text
' UNION SELECT user_id, username, password_hash, role FROM users -- 
```

### C. The Resulting Executed SQL
```sql
SELECT student_id, roll_no, program_id, current_semester 
FROM student 
WHERE roll_no = '' 
UNION 
SELECT user_id, username, password_hash, role 
FROM users -- '
```

### D. Exploitation Analysis
Because both `SELECT` clauses return exactly 4 columns with compatible types, MySQL appends the complete contents of the sensitive `users` table directly into the student search results table. BCrypt password hashes and administrative usernames are displayed directly on the student directory UI.

---

## 4. The Engineered Defense: Parameterized Queries (Prepared Statements)

### A. The Secure Production Implementation
In our Spring Boot backend, all database interactions use **JdbcTemplate with parameterized placeholders (`?`)**:

```java
// =========================================================================
// PRODUCTION STANDARD: Strictly Enforced Parameterized Query
// =========================================================================
@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT user_id, username, password_hash, role, is_active " +
                     "FROM users " +
                     "WHERE username = ? AND is_active = TRUE";

        List<User> results = jdbcTemplate.query(sql, new UserRowMapper(), username);
        return results.stream().findFirst();
    }
}
```

### B. Why Parameterized Queries Are Immune to SQL Injection

When a parameterized query is executed:

```
Step 1: Protocol Prepare
Client  ─── COM_STMT_PREPARE ("SELECT ... WHERE username = ?") ───> MySQL Server
                                                                      │
                                                   Parses AST once    │
                                                   Validates schema   │
                                                   Pre-compiles plan  │
Client  <── STMT_PREPARE_OK (Statement ID: 1) ────────────────────────┘

Step 2: Protocol Execute
Client  ─── COM_STMT_EXECUTE (ID: 1, Param 1: "admin' OR '1'='1' -- ") ───> MySQL Server
                                                                             │
                                           Treats parameter strictly as      │
                                           raw binary data bytes (literal)   │
                                           NEVER re-evaluates AST or syntax  │
                                           Matches column literally:         │
                                           username == "admin' OR '1'='1' --"│
                                                                             │
Client  <── Empty Result Set (0 rows matched) ───────────────────────────────┘
```

1. **Separation of Code and Data:** The SQL structure (AST) is finalized during preparation. Parameter values are transmitted in separate network packets via the MySQL binary protocol.
2. **No Dynamic Parsing:** Even if the parameter string contains SQL keywords (`SELECT`, `UNION`, `DROP`), quotes (`'`), or comments (`--`), the MySQL engine treats them strictly as literal string values of the `VARCHAR` datatype.
3. **Performance Optimization:** MySQL caches the execution plan for the prepared statement handle, reducing CPU overhead for repeated invocations.

---

## 5. Architectural Comparison: Vulnerable vs. Parameterized

| Dimension | Vulnerable String Concatenation | Secure Parameterized Query (`?`) |
| :--- | :--- | :--- |
| **SQL Construction** | Dynamically assembled via string interpolation (`+`, `String.format`) | Static SQL string with positional placeholders (`?`) |
| **Parsing Behavior** | Re-parsed on every execution; user input becomes executable tokens | Parsed once during preparation; tokens remain immutable |
| **Data Boundary** | No separation; data and code share the same textual stream | Strict protocol separation between compiled query and bound parameters |
| **Payload: `' OR '1'='1`** | **EXPLOITED:** Always evaluates to TRUE, bypassing authentication | **SAFE:** Database searches for literal string `"admin' OR '1'='1 -- "` (0 rows returned) |
| **Execution Plan Reuse** | Poor (every unique input creates a new SQL string in MySQL query cache) | Excellent (MySQL reuses prepared statement plans in memory) |
| **Compliance** | Violates OWASP Top 10 (A03:2021-Injection) | Meets OWASP Top 10, CWE-89, and Project Rule 3 |

---

## 6. Verification and Project Audit

Every query in this project has been systematically audited against this standard:
- **`backend/src/main/resources/db/migration/`**: All database triggers and stored procedures enforce strict parameter types (`IN p_student_id INT`, etc.).
- **`backend/src/main/java/.../repository/`**: Every single JdbcTemplate method passes query arguments via `Object[]` or varargs (`jdbcTemplate.query(sql, mapper, param1, param2)`).
- **`database/queries/` (q01 to q22)**: Parameterized queries use standard `?` placeholders for runtime binding.
