package com.example.room_database_discussion.data

import kotlinx.coroutines.flow.Flow

class StudentRepository(private val studentDao: StudentDao) {

    val students: Flow<List<Student>>
        get() = studentDao.getAllStudents()

    val studentCount: Flow<Int>
        get() = studentDao.getStudentCount()

    fun searchStudents(search: String): Flow<List<Student>> =
        studentDao.searchStudents(search)

    fun getStudentsByCourse(course: String): Flow<List<Student>> =
        studentDao.getStudentsByCourse(course)

    fun filterStudents(search: String, course: String?): Flow<List<Student>> =
        if (course == null) {
            studentDao.searchStudents(search)
        } else {
            studentDao.searchStudentsByCourse(search, course)
        }

    suspend fun insertStudents(student: Student) {
        studentDao.insertStudent(student)
    }

    suspend fun updateStudent(student: Student) {
        studentDao.updateStudent(student)
    }

    suspend fun deleteStudent(student: Student) {
        studentDao.deleteStudent(student)
    }
}
