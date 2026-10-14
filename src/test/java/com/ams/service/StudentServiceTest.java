package com.ams.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import com.ams.dto.PageResponse;
import com.ams.dto.StudentRequest;
import com.ams.dto.StudentResponse;
import com.ams.exception.DuplicateResourceException;
import com.ams.exception.ResourceNotFoundException;
import com.ams.model.ProgramType;
import com.ams.model.Student;
import com.ams.model.User;
import com.ams.repository.AttendanceArchiveRepository;
import com.ams.repository.AttendanceRepository;
import com.ams.repository.CourseRepository;
import com.ams.repository.DepartmentRepository;
import com.ams.repository.StudentRepository;
import com.ams.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private AttendanceArchiveRepository archiveRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private StudentService studentService;

    private StudentRequest studentRequest;
    private Student student;

    @BeforeEach
    void setUp() {
        studentRequest = new StudentRequest(
                "2024CS101",
                "Aarav Sharma",
                "aarav@amsportal.edu",
                "9876543210",
                "dept-cs-01",
                "course-cs-01",
                ProgramType.UG,
                1,
                "A",
                true
        );

        student = new Student(
                "2024CS101",
                "Aarav Sharma",
                "aarav@amsportal.edu",
                "9876543210",
                "dept-cs-01",
                "course-cs-01",
                ProgramType.UG,
                1,
                "A",
                true
        );
        student.setId("std-123");
    }

    @Test
    @DisplayName("Successfully create a new student and sync user account")
    void createStudent_success() {
        given(studentRepository.existsByRollNo("2024CS101")).willReturn(false);
        given(studentRepository.save(any(Student.class))).willReturn(student);
        given(userRepository.findByEmail("aarav@amsportal.edu")).willReturn(Optional.empty());

        StudentResponse response = studentService.createStudent(studentRequest);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("std-123");
        assertThat(response.getRollNo()).isEqualTo("2024CS101");
        assertThat(response.getDepartmentId()).isEqualTo("dept-cs-01");
        assertThat(response.getYear()).isEqualTo(1);
        verify(studentRepository).save(any(Student.class));
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Throw DuplicateResourceException when roll number already exists")
    void createStudent_duplicateRollNo_throwsException() {
        given(studentRepository.existsByRollNo("2024CS101")).willReturn(true);

        assertThrows(DuplicateResourceException.class, () -> studentService.createStudent(studentRequest));
    }

    @Test
    @DisplayName("Throw IllegalArgumentException when year exceeds PG program duration")
    void createStudent_invalidYearForProgramType_throwsException() {
        studentRequest.setProgramType(ProgramType.PG);
        studentRequest.setYear(3); // PG is 2 years only

        given(studentRepository.existsByRollNo("2024CS101")).willReturn(false);

        assertThrows(IllegalArgumentException.class, () -> studentService.createStudent(studentRequest));
    }

    @Test
    @DisplayName("Throw ResourceNotFoundException when student ID not found")
    void getStudentById_notFound_throwsException() {
        given(studentRepository.findById("invalid-id")).willReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> studentService.getStudentById("invalid-id"));
    }

    @Test
    @DisplayName("Paginated student query returns correct PageResponse metadata")
    void getStudents_pagination() {
        List<Student> mockList = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            Student s = new Student(
                    "2024CS" + (100 + i),
                    "Student " + i,
                    "student" + i + "@amsportal.edu",
                    "9876543210",
                    "dept-cs-01",
                    "course-cs-01",
                    ProgramType.UG,
                    1,
                    "A",
                    true
            );
            s.setId("id-" + i);
            mockList.add(s);
        }

        given(studentRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .willReturn(new PageImpl<>(mockList, PageRequest.of(0, 20), 45L));

        PageResponse<StudentResponse> pageResponse = studentService.getStudents(
                "dept-cs-01", null, null, null, null, null, 0, 20
        );

        assertThat(pageResponse).isNotNull();
        assertThat(pageResponse.getData()).hasSize(20);
        assertThat(pageResponse.getPage()).isEqualTo(0);
        assertThat(pageResponse.getSize()).isEqualTo(20);
        assertThat(pageResponse.getTotalElements()).isEqualTo(45L);
        assertThat(pageResponse.getTotalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("deleteStudent cascades deletion to User, Attendance, and Archive repositories")
    void deleteStudent_cascadesDeletion() {
        User user = new User("fb-123", "Aarav Sharma", "aarav@amsportal.edu", null, "dept-cs-01", true);
        given(studentRepository.findById("std-123")).willReturn(Optional.of(student));
        given(userRepository.findByEmail("aarav@amsportal.edu")).willReturn(Optional.of(user));

        studentService.deleteStudent("std-123");

        verify(userRepository).delete(user);
        verify(attendanceRepository).deleteByStudentId("std-123");
        verify(attendanceRepository).deleteByStudentId("2024CS101");
        verify(archiveRepository).deleteByStudentId("std-123");
        verify(archiveRepository).deleteByStudentId("2024CS101");
        verify(studentRepository).delete(student);
    }
}
