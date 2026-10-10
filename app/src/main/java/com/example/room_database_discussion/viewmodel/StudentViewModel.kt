package com.example.room_database_discussion.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.room_database_discussion.data.Student
import com.example.room_database_discussion.data.StudentDatabase
import com.example.room_database_discussion.data.StudentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
const val ALL_COURSES = "All Courses"
val COURSE_OPTIONS = listOf("BSCpE", "BSCS", "BSEMC")
private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
data class StudentFormState(
    val name: String = "",
    val email: String = "",
    val course: String = "",
    val age: String = "",
    val nameError: String? = null,
    val emailError: String? = null,
    val courseError: String? = null,
    val ageError: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class StudentViewModel(application: Application) : AndroidViewModel(application) {

    private val database = StudentDatabase.getData(application)
    private val repository = StudentRepository(database.studentDao())
    private val _searchQuery = MutableStateFlow("")
    private val _selectedCourse = MutableStateFlow(ALL_COURSES)
    private val _formState = MutableStateFlow(StudentFormState())
    private val _editingStudent = MutableStateFlow<Student?>(null)
    private val _isLoading = MutableStateFlow(true)
    private val _message = MutableStateFlow<String?>(null)
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val selectedCourse: StateFlow<String> = _selectedCourse.asStateFlow()
    val formState: StateFlow<StudentFormState> = _formState.asStateFlow()
    val editingStudent: StateFlow<Student?> = _editingStudent.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val message: StateFlow<String?> = _message.asStateFlow()
    val students: StateFlow<List<Student>> =
        combine(_searchQuery, _selectedCourse) { query, course ->
            query.trim() to course
        }
            .flatMapLatest { (query, course) ->
                repository.filterStudents(
                    search = query,
                    course = if (course == ALL_COURSES) null else course
                )
            }
            .onEach { _isLoading.value = false }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val totalStudents: StateFlow<Int> = repository.studentCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun onSearchChange(query: String) {
        _searchQuery.value = query
    }

    fun onCourseFilterSelected(course: String) {
        _selectedCourse.value = course
    }
    fun onNameChange(value: String) =
        _formState.update { it.copy(name = value, nameError = null) }

    fun onEmailChange(value: String) =
        _formState.update { it.copy(email = value, emailError = null) }

    fun onCourseChange(value: String) =
        _formState.update { it.copy(course = value, courseError = null) }

    fun onAgeChange(value: String) =
        _formState.update {
            it.copy(age = value.filter { c -> c.isDigit() }.take(3), ageError = null)
        }
    fun selectStudentForEdit(student: Student) {
        _editingStudent.value = student
        _formState.value = StudentFormState(
            name = student.name,
            email = student.email,
            course = student.course,
            age = if (student.age > 0) student.age.toString() else ""
        )
    }

    fun cancelEdit() = clearForm()

    private fun clearForm() {
        _editingStudent.value = null
        _formState.value = StudentFormState()
    }
    fun submitStudent() {
        val form = _formState.value
        val name = form.name.trim()
        val email = form.email.trim()
        val courseInput = form.course.trim()
        val ageInput = form.age.trim()
        val ageValue = ageInput.toIntOrNull()

        val nameError = if (name.isEmpty()) "Name is required" else null
        val emailError = when {
            email.isEmpty() -> "Email is required"
            !EMAIL_REGEX.matches(email) -> "Enter a valid email (e.g. juan@email.com)"
            else -> null
        }
        val courseError = if (courseInput.isEmpty()) "Course is required" else null
        val ageError =
            if (ageInput.isNotEmpty() && (ageValue == null || ageValue !in 1..120))
                "Age must be between 1 and 120"
            else null

        if (nameError != null || emailError != null || courseError != null || ageError != null) {
            _formState.update {
                it.copy(
                    nameError = nameError,
                    emailError = emailError,
                    courseError = courseError,
                    ageError = ageError
                )
            }
            return
        }

        val course = COURSE_OPTIONS.firstOrNull { it.equals(courseInput, ignoreCase = true) }
            ?: courseInput
        val age = ageValue ?: 0

        val editing = _editingStudent.value
        if (editing == null) {
            addStudent(name, email, course, age)
        } else {
            updateStudent(editing.copy(name = name, email = email, course = course, age = age))
        }
        clearForm()
    }
    fun addStudent(name: String, email: String, course: String, age: Int = 0) {
        viewModelScope.launch {
            repository.insertStudents(
                Student(name = name, email = email, course = course, age = age)
            )
            _message.value = "Student added"
        }
    }

    fun updateStudent(student: Student) {
        viewModelScope.launch {
            repository.updateStudent(student)
            _message.value = "Changes saved"
        }
    }

    fun deleteStudent(student: Student) {
        viewModelScope.launch {
            repository.deleteStudent(student)
            if (_editingStudent.value?.id == student.id) clearForm()
            _message.value = "Student deleted"
        }
    }

    fun messageShown() {
        _message.value = null
    }
}