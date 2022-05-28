package tasklist

import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.datetime.*
import okio.*
import java.io.File

data class Task(var name: String, var priority: Priority, var deadlineDateTime: String) {
    fun getTag(): Tag {
        val numberOfDays = getNumberOfDays()
        return when {
            numberOfDays > 0 -> Tag.I
            numberOfDays == 0 -> Tag.T
            else -> Tag.O
        }
    }

    fun getNumberOfDays(): Int {
        val taskDate = getDeadlineDateTime().date
        val currentDate = Clock.System.now().toLocalDateTime(TimeZone.of("UTC+0")).date
        return currentDate.daysUntil(taskDate)
    }

    fun getDeadlineDateTime() = LocalDateTime.parse(deadlineDateTime.replaceFirst(" ", "T"))
}

enum class Priority(val description: String, val color: Int) {
    C("Critical", 101), H("High", 103), N("Normal", 102), L("Low", 104)
}

enum class Tag(val description: String, val color: Int) {
    I("In time", 102), T("Today", 103), O("Overdue", 101)
}

class TaskList() {
    val jsonAdapter: JsonAdapter<List<Task>> = createJsonAdapter()
    val jsonFile: File = File(TaskList.TASKLIST_FILE)
    val tasks: MutableList<Task> = mutableListOf()

    companion object {
        const val TASKLIST_FILE = "tasklist.json"
        const val HORIZON_LINE = "+----+------------+-------+---+---+--------------------------------------------+"
        const val HEADERS_LINE = "| N  |    Date    | Time  | P | D |                   Task                     |"
        const val SECOND_LINES = "|    |            |       |   |   |%-44s|"
    }

    fun createJsonAdapter(): JsonAdapter<List<Task>> {
        val moshi: Moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        val type = Types.newParameterizedType(List::class.java, Task::class.java)
        return moshi.adapter<List<Task>>(type)
    }

    fun loadData() {
        if (jsonFile.exists()) {
            tasks.clear()
            tasks.addAll(jsonAdapter.fromJson(jsonFile.source().buffer())!!)
        }
    }

    fun saveData() {
        val outputJson = jsonAdapter.toJson(tasks)
        jsonFile.writeText(outputJson)
    }

    fun createTask(): Task? {
        val priority: Priority = getPriority()
        val date: LocalDate = getDate()
        val dateTime: LocalDateTime = getTime(date)
        val taskName = getTaskName()
        if (taskName.isBlank()) {
            println("The task is blank")
            return null
        } else {
            val deadline = getDeadlineString(dateTime)
            return Task(taskName, priority, deadline)
        }
    }

    fun getPriority(): Priority {
        var priority: Priority? = null
        do {
            val input = getInputValue("Input the task priority (C, H, N, L):")
            try {
                priority = Priority.valueOf(input.uppercase())
            } catch (_: IllegalArgumentException) {  }
        } while (priority == null)
        return priority
    }

    fun getDate(): LocalDate {
        var date: LocalDate? = null
        do {
            val input = getInputValue("Input the date (yyyy-mm-dd):")
            try {
                val (year, month, day) = input.split("-").map { it.toInt() }
                date = LocalDate(year, month, day)
            } catch (_: IllegalArgumentException) {
                println("The input date is invalid")
            }
        } while (date == null)
        return date
    }

    fun getTime(date: LocalDate): LocalDateTime {
        var dateTime: LocalDateTime? = null
        do {
            val input = getInputValue("Input the time (hh:mm):")
            try {
                val (hours, minutes) = input.split(":").map { it.toInt() }
                dateTime = date.atTime(hours, minutes)
            } catch (_: IllegalArgumentException) {
                println("The input time is invalid")
            }
        } while (dateTime == null)
        return dateTime
    }

    fun getDeadlineString(dateTime: LocalDateTime): String {
        return dateTime.toString().replace("T", " ")
    }

    fun getDeadlineString(date: LocalDate, time: LocalDateTime): String {
        return getDeadlineString(date.atTime(time.hour, time.minute))
    }

    fun getTaskName(): String {
        println("Input a new task (enter a blank line to end):")
        val taskName = StringBuilder()
        do {
            val line = readLine()!!.trim()
            taskName.appendLine(line)
        } while (line.isNotBlank())
        return taskName.trimEnd().toString()
    }

    fun addTask() {
        val task = createTask()
        if (task != null) {
            tasks.add(task)
        }
    }

    fun editTask() {
        printTaskList()
        if (tasks.isNotEmpty()) {
            val taskIndex = getTaskIndex()
            val task = tasks.get(taskIndex)
            do {
                var inCorrect = false
                val input = getInputValue("Input a field to edit (priority, date, time, task):")
                when (input) {
                    "priority" -> task.priority = getPriority()
                    "date" -> task.deadlineDateTime = getDeadlineString(getDate(), task.getDeadlineDateTime())
                    "time" -> task.deadlineDateTime = getDeadlineString(getTime(task.getDeadlineDateTime().date))
                    "task" -> task.name = getTaskName()
                    else -> {
                        println("Invalid field")
                        inCorrect = true
                    }
                }
            } while (inCorrect)
            println("The task is changed")
        }
    }

    fun deleteTask() {
        printTaskList()
        if (tasks.isNotEmpty()) {
            val taskIndex = getTaskIndex()
            tasks.removeAt(taskIndex)
            println("The task is deleted")
        }
    }

    fun getTaskIndex(): Int {
        var taskNum = -1
        do {
            try {
                taskNum = getInputValue("Input the task number (1-${tasks.size}):").toInt()
                if (taskNum < 1 || taskNum > tasks.size) {
                    taskNum = -1
                    println("Invalid task number")
                }
            } catch (_: NumberFormatException) {
                println("Invalid task number")
            }
        } while (taskNum == -1)
        return taskNum - 1
    }

    fun getInputValue(message: String): String {
        println(message)
        return readLine()!!.trim()
    }

    fun getColoredIndicator(color: Int) = "\u001B[${color}m \u001B[0m"

    fun printTaskList() {
        if (tasks.isEmpty()) {
            println("No tasks have been input")
            return
        }

        val sb = StringBuilder()
        sb.appendLine(HORIZON_LINE)
        sb.appendLine(HEADERS_LINE)
        sb.appendLine(HORIZON_LINE)
        for (i in 0 until tasks.size) {
            val task = tasks[i]
            val dateTime = task.deadlineDateTime.replaceFirst(" ", " | ")
            val priority = getColoredIndicator(task.priority.color)
            val tag = getColoredIndicator(task.getTag().color)
            val lines = task.name.lines().flatMap { it.chunked(44) }.listIterator()

            sb.appendLine(String.format("| %-2d | %s | %s | %s |%-44s|", i + 1, dateTime, priority, tag, lines.next()))
            for (line in lines) sb.appendLine(String.format(SECOND_LINES, line))
            sb.appendLine(HORIZON_LINE)
        }
        println(sb.toString())
    }
}

fun main() {
    val taskList = TaskList()
    taskList.loadData()

    var command = ""
    while (command != "end") {
        println("Input an action (add, print, edit, delete, end):")
        command = readLine()!!.trim()
        when (command) {
            "add" -> taskList.addTask()
            "print" -> taskList.printTaskList()
            "edit" -> taskList.editTask()
            "delete" -> taskList.deleteTask()
            "end" -> println("Tasklist exiting!")
            else -> println("The input action is invalid")
        }
    }
    taskList.saveData()
}
