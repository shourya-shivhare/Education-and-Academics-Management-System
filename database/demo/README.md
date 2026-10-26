# database/demo/

Demonstration scripts. This folder will contain the **single** allowed SQL-injection demonstration file (the only place in the project that uses string-concatenated SQL, clearly marked as a demo — never used in production application code).

That demo file is **not** created in this step; it will be added in a later step. The rest of the codebase uses parameterized queries exclusively.
