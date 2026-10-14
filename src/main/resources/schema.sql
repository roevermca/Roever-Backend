-- =============================================================================
-- Attendance Management System (AMS) - High Speed MySQL Database Schema
-- Optimized for high concurrent throughput, fast reads, and batch inserts
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `ams_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `ams_db`;

-- 1. Departments Table
CREATE TABLE IF NOT EXISTS `departments` (
    `id` VARCHAR(64) NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `code` VARCHAR(64) NOT NULL,
    `active` BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dept_code` (`code`),
    UNIQUE KEY `uk_dept_name` (`name`),
    KEY `idx_dept_active` (`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Courses Table
CREATE TABLE IF NOT EXISTS `courses` (
    `id` VARCHAR(64) NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `code` VARCHAR(64) NOT NULL,
    `department_id` VARCHAR(64) NOT NULL,
    `program_type` VARCHAR(10) NOT NULL,
    `duration_years` INT NOT NULL,
    `active` BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_code` (`code`),
    UNIQUE KEY `uk_course_dept_name` (`department_id`, `name`),
    KEY `idx_course_dept_active` (`department_id`, `active`),
    KEY `idx_course_active` (`active`),
    CONSTRAINT `fk_course_dept` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Users Table
CREATE TABLE IF NOT EXISTS `users` (
    `id` VARCHAR(64) NOT NULL,
    `firebase_uid` VARCHAR(128) NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `email` VARCHAR(128) NOT NULL,
    `role` VARCHAR(20) NOT NULL,
    `department_id` VARCHAR(64) DEFAULT NULL,
    `course_id` VARCHAR(64) DEFAULT NULL,
    `phone` VARCHAR(32) DEFAULT NULL,
    `active` BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_firebase_uid` (`firebase_uid`),
    UNIQUE KEY `uk_user_email` (`email`),
    KEY `idx_user_role_active` (`role`, `active`),
    KEY `idx_user_dept_active` (`department_id`, `active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Students Table
CREATE TABLE IF NOT EXISTS `students` (
    `id` VARCHAR(64) NOT NULL,
    `roll_no` VARCHAR(64) NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `email` VARCHAR(128) NOT NULL,
    `phone` VARCHAR(32) DEFAULT NULL,
    `department_id` VARCHAR(64) NOT NULL,
    `course_id` VARCHAR(64) NOT NULL,
    `program_type` VARCHAR(10) NOT NULL,
    `year` INT NOT NULL,
    `section` VARCHAR(16) NOT NULL,
    `active` BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_student_roll_no` (`roll_no`),
    UNIQUE KEY `uk_student_email` (`email`),
    KEY `idx_student_dept_active` (`department_id`, `active`),
    KEY `idx_student_course_active` (`course_id`, `active`),
    KEY `idx_student_class_section_active` (`department_id`, `course_id`, `year`, `section`, `active`),
    CONSTRAINT `fk_student_dept` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_student_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Attendance Table (Optimized with compound indexes for high-speed lookups and batch writes)
CREATE TABLE IF NOT EXISTS `attendance` (
    `id` VARCHAR(64) NOT NULL,
    `student_id` VARCHAR(64) NOT NULL,
    `date` DATE NOT NULL,
    `period` INT NOT NULL,
    `status` VARCHAR(20) NOT NULL,
    `marked_by` VARCHAR(128) NOT NULL,
    `marked_at` TIMESTAMP NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_student_date_period` (`student_id`, `date`, `period`),
    KEY `idx_att_date_period` (`date`, `period`),
    KEY `idx_att_student_date` (`student_id`, `date`),
    KEY `idx_att_date` (`date`),
    KEY `idx_att_student` (`student_id`),
    KEY `idx_att_date_status` (`date`, `status`),
    KEY `idx_att_student_date_status` (`student_id`, `date`, `status`),
    CONSTRAINT `fk_att_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Attendance Archives Table
CREATE TABLE IF NOT EXISTS `attendance_archives` (
    `id` VARCHAR(64) NOT NULL,
    `student_id` VARCHAR(64) NOT NULL,
    `date_range` VARCHAR(128) NOT NULL,
    `total_records` BIGINT NOT NULL,
    `present_records` BIGINT NOT NULL,
    `absent_records` BIGINT NOT NULL,
    `attendance_percentage` DOUBLE NOT NULL,
    `archived_at` TIMESTAMP NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_archive_student_range` (`student_id`, `date_range`),
    KEY `idx_arch_student` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Pre-Aggregated Daily Class Attendance Rollup Summary Table (High-Speed CQRS for 100k+ students)
CREATE TABLE IF NOT EXISTS `daily_attendance_summary` (
    `id` VARCHAR(64) NOT NULL,
    `date` DATE NOT NULL,
    `department_id` VARCHAR(64) NOT NULL,
    `course_id` VARCHAR(64) NOT NULL,
    `year` INT NOT NULL,
    `section` VARCHAR(16) NOT NULL,
    `total_students` INT NOT NULL DEFAULT 0,
    `present_count` INT NOT NULL DEFAULT 0,
    `absent_count` INT NOT NULL DEFAULT 0,
    `on_duty_count` INT NOT NULL DEFAULT 0,
    `attendance_rate` DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_daily_class_summary` (`date`, `department_id`, `course_id`, `year`, `section`),
    KEY `idx_summary_dept_date` (`department_id`, `date`),
    KEY `idx_summary_date` (`date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

