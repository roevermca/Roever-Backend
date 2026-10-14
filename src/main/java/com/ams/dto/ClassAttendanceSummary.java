package com.ams.dto;

public interface ClassAttendanceSummary {
    String getCourseId();
    Integer getYear();
    String getSection();
    Long getTotalStudents();
    Long getPresentCount();
    Long getAbsentCount();
    Long getTotalCount();
}
