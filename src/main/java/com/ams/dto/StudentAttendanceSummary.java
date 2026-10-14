package com.ams.dto;

public interface StudentAttendanceSummary {
    String getStudentId();
    String getRollNo();
    String getName();
    String getDepartmentId();
    String getCourseId();
    String getProgramType();
    Integer getYear();
    String getSection();
    Long getTotalPeriods();
    Long getPresentPeriods();
    Long getAbsentPeriods();
}
