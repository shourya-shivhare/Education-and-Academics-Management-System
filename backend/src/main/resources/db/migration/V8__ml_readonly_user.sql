-- Internal AI service login. Replace the development password before deployment.
CREATE USER IF NOT EXISTS 'ml_readonly'@'%' IDENTIFIED BY 'ChangeMe_ml_2026!';
GRANT 'ml_readonly_role' TO 'ml_readonly'@'%';
SET DEFAULT ROLE 'ml_readonly_role' TO 'ml_readonly'@'%';
