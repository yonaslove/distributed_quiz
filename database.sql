CREATE DATABASE IF NOT EXISTS distributed_quiz;
USE distributed_quiz;

CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role ENUM('STUDENT', 'TEACHER') DEFAULT 'STUDENT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS questions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    question_text TEXT NOT NULL,
    option_a VARCHAR(255) NOT NULL,
    option_b VARCHAR(255) NOT NULL,
    option_c VARCHAR(255) NOT NULL,
    option_d VARCHAR(255) NOT NULL,
    correct_option CHAR(1) NOT NULL, -- 'A', 'B', 'C', 'D'
    created_by INT,
    FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS results (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,
    score INT,
    total_questions INT,
    completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Insert Sample Data
INSERT INTO users (username, password, role) VALUES 
('admin', 'admin123', 'TEACHER'),
('student1', 'pass123', 'STUDENT');

INSERT INTO questions (question_text, option_a, option_b, option_c, option_d, correct_option) VALUES 
('What is the capital of France?', 'London', 'Berlin', 'Paris', 'Madrid', 'C'),
('Which protocol is used for RMI?', 'HTTP', 'JRMP', 'FTP', 'SMTP', 'B'),
('What does JDBC stand for?', 'Java Database Connectivity', 'Java Data Control', 'Just Do Basic Code', 'None', 'A');
