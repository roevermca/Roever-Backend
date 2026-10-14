package com.ams.dto;

public interface OverallAttendanceSummary {
    Long getTotalStudents();
    Long getTotalRecords();
    Long getPresentRecords();
    Long getAbsentRecords();
}
