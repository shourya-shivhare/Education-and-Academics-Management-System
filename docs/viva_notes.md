# Viva Notes

1. **Why is MySQL the primary focus?**  
   Constraints, triggers, procedures, views, roles, and transactions enforce
   the business rules independently of the UI.

2. **What is a primary key?**  
   A minimal chosen key that uniquely identifies each row and cannot be NULL.

3. **What is a candidate key?**  
   Any minimal attribute set that uniquely identifies a row; one is selected as
   the primary key.

4. **What is a foreign key?**  
   A reference that enforces a valid relationship to a parent key.

5. **What is a functional dependency?**  
   `X → Y` means equal X values require equal Y values.

6. **What is 1NF?**  
   Every attribute is atomic and repeating groups are removed.

7. **What is 2NF?**  
   1NF plus no non-key attribute depends on only part of a composite key.

8. **What is 3NF?**  
   2NF plus no non-key attribute depends transitively on another non-key
   attribute.

9. **BCNF versus 3NF?**  
   BCNF requires every determinant to be a superkey. 3NF is weaker and can
   preserve dependencies that a BCNF decomposition may not.

10. **Why separate Person, Student, and Faculty?**  
    It models specialization and prevents duplicated person data.

11. **Why is Enrollment a separate table?**  
    It resolves the Student–Course Offering many-to-many relationship and
    stores relationship attributes such as status and grade.

12. **Trigger versus procedure?**  
    A trigger fires automatically for a table event; a procedure is explicitly
    called with defined inputs and outputs.

13. **Why use triggers?**  
    To enforce cross-row or cross-table rules for every client, not only the
    Spring application.

14. **Why use procedures?**  
    To encapsulate multi-step business workflows in a transaction.

15. **Why use Flyway?**  
    It versions schema changes, makes environments reproducible, and keeps
    DDL separate from runtime DML.

16. **Why not JPA/Hibernate?**  
    JdbcTemplate keeps the SQL visible for DBMS coursework and trace output.

17. **Why use JdbcTemplate?**  
    It supports hand-written parameterized SQL, explicit joins, and predictable
    SQL traces without hiding database behavior.

18. **What is SQL injection prevention here?**  
    SQL structure is fixed and values are bound separately with `?` parameters;
    only the clearly marked demo shows unsafe concatenation.

19. **What is ACID?**  
    Atomicity, Consistency, Isolation, and Durability.

20. **What is atomicity in enrollment?**  
    Capacity/prerequisite checks and the insert commit together or all roll
    back.

21. **What is a transaction isolation level?**  
    It defines which concurrent changes a transaction can observe and what
    locks/snapshots are used.

22. **What is MySQL's default isolation level?**  
    InnoDB normally uses `REPEATABLE READ`.

23. **Why use `FOR UPDATE`?**  
    To lock rows participating in a write decision until commit, such as the
    offering capacity row in a last-seat race.

24. **What happens without `FOR UPDATE` in a last-seat race?**  
    Two sessions can read the same available capacity and both attempt to
    claim it.

25. **What is a deadlock?**  
    Two transactions wait cyclically for locks held by the other.

26. **How does InnoDB handle deadlocks?**  
    It detects the cycle and rolls back one victim transaction.

27. **Why optimistic locking for marks?**  
    Browser edits should not hold database locks; a version predicate detects a
    stale update and returns 409.

28. **What does a 409 optimistic-lock response mean?**  
    Another writer changed the row, so the user must reload before editing.

29. **How does RBAC work?**  
    JWT claims identify the role/person, and Spring method security checks the
    permitted operation and scope.

30. **Does MySQL provide native row-level security here?**  
    No. MySQL has users, roles, and privileges, but not PostgreSQL-style native
    row-level security. Spring predicates enforce Faculty/Student row scope.

31. **Why use separate database users?**  
    Flyway needs migration privileges, runtime needs limited DML, and ML needs
    read-only access.

32. **Why is the AI service internal?**  
    The browser should only call Spring; Spring controls authentication,
    tracing, and the internal-key boundary.

33. **Why use a time-based split?**  
    It simulates deployment: earlier terms train and the latest term tests,
    avoiding future information leaking backward.

34. **What is label leakage?**  
    Using information unavailable at prediction time, especially a final
    outcome or a post-outcome field, as an input feature.

35. **How is leakage prevented?**  
    `v_ml_features` contains only mid-term inputs; final F/detention is kept in
    a separate label view.

36. **Why use recall for the at-risk class?**  
    A false negative misses a student who may need support; recall measures how
    many actual at-risk students are found.

37. **Why impose a precision floor?**  
    A model that flags everyone has high recall but overwhelms staff with
    false positives; the floor keeps alerts useful.

38. **Why compare class weights and SMOTE?**  
    Both address imbalance differently; SMOTE must run inside CV training folds
    to avoid validation contamination.

39. **Why calibrate probabilities and tune a threshold?**  
    Calibration improves probability interpretation and threshold tuning matches
    the recall/precision policy rather than blindly using 0.5.

40. **Can the synthetic metrics be claimed as real performance?**  
    No. The seed is synthetic and intentionally separable; the metrics only
    demonstrate repeatability and pipeline behavior.
