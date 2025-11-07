import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.system.exitProcess

// ------------------------- Data Layer -------------------------

data class Task(
    val id: Int,
    var title: String,
    var description: String,
    var priority: String,
    var dueDate: String,     // dd.MM.yyyy
    var isCompleted: Boolean,
    var category: String,
    val createdAt: String    // dd.MM.yyyy
)

// Global collections (mutable, single source of truth)
val tasks = mutableListOf<Task>()
val categories = mutableSetOf("Работа", "Личное", "Учеба", "Здоровье", "Финансы")
val priorities = listOf("Низкий", "Средний", "Высокий", "Срочный")
val priorityEmojis = mapOf("Низкий" to "🔵", "Средний" to "🟡", "Высокий" to "🟠", "Срочный" to "🔴")
val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

// ID generator (simple incremental)
var lastId = 0
fun nextId(): Int {
    lastId += 1
    return lastId
}

// ------------------------- Business Logic Layer -------------------------

// Validation helpers
fun parseDateStrict(s: String): LocalDate? {
    return try {
        LocalDate.parse(s, dateFormatter)
    } catch (e: DateTimeParseException) {
        null
    }
}

fun validateTitle(title: String): Boolean = title.trim().isNotEmpty()
fun validatePriority(p: String): Boolean = priorities.contains(p)
fun validateCategory(cat: String): Boolean = cat.trim().isNotEmpty()
fun validateDueDate(d: String): Boolean = parseDateStrict(d) != null

// CRUD
fun createTask(
    title: String,
    description: String,
    priority: String,
    dueDate: String,
    category: String
): Task {
    val id = nextId()
    val createdAt = LocalDate.now().format(dateFormatter)
    categories.add(category)
    val t = Task(id, title.trim(), description.trim(), priority, dueDate, false, category, createdAt)
    tasks.add(t)
    return t
}

fun findTaskById(id: Int): Task? = tasks.find { it.id == id }

fun updateTask(id: Int, updater: (Task) -> Unit): Boolean {
    val t = findTaskById(id) ?: return false
    if (t.isCompleted) return false // cannot edit completed tasks
    updater(t)
    return true
}

fun deleteTask(id: Int): Boolean {
    val t = findTaskById(id) ?: return false
    return tasks.remove(t)
}

fun markCompleted(id: Int): Boolean {
    val t = findTaskById(id) ?: return false
    if (t.isCompleted) return false
    t.isCompleted = true
    return true
}

// Search & Filter
fun searchByText(q: String): List<Task> {
    val s = q.trim().lowercase()
    return tasks.filter {
        it.title.lowercase().contains(s) || it.description.lowercase().contains(s)
    }
}

fun filterByStatus(status: String): List<Task> = when (status) {
    "active" -> tasks.filter { !it.isCompleted }
    "completed" -> tasks.filter { it.isCompleted }
    else -> tasks.toList()
}

fun filterByCategory(cat: String): List<Task> = tasks.filter { it.category == cat }
fun filterByPriority(pr: String): List<Task> = tasks.filter { it.priority == pr }
fun overdueTasks(): List<Task> {
    val today = LocalDate.now()
    return tasks.filter {
        !it.isCompleted && (parseDateStrict(it.dueDate)?.isBefore(today) ?: false)
    }
}

// Analytics
fun stats(): Map<String, Any> {
    val total = tasks.size
    val completed = tasks.count { it.isCompleted }
    val active = total - completed
    val percent = if (total == 0) 0.0 else (completed.toDouble() / total.toDouble()) * 100.0
    val byPriority = priorities.associateWith { pr -> tasks.count { it.priority == pr } }
    val byCategory = categories.associateWith { c -> tasks.count { it.category == c } }
    val overdue = overdueTasks().size
    return mapOf(
        "total" to total,
        "completed" to completed,
        "active" to active,
        "percent" to percent,
        "byPriority" to byPriority,
        "byCategory" to byCategory,
        "overdue" to overdue
    )
}

// ------------------------- Presentation Layer -------------------------

fun clearScreen() {
    // Best-effort clear for many terminals
    print("\u001b[H\u001b[2J")
    System.out.flush()
}

fun line() = println("--------------------------------------------------")

fun showTask(t: Task) {
    val status = if (t.isCompleted) "✅ Выполнено" else "🕗 В работе"
    val priorityMark = priorityEmojis[t.priority] ?: ""
    val title = if (t.isCompleted) "${t.title} (выполнено)" else t.title
    println("ID: ${t.id} | $status | Приоритет: $priorityMark ${t.priority}")
    println("Название : $title")
    if (t.description.isNotEmpty()) println("Описание : ${t.description}")
    println("Категория: ${t.category} | Выполнить до: ${t.dueDate} | Создано: ${t.createdAt}")
    line()
}

fun listTasks(list: List<Task>) {
    if (list.isEmpty()) {
        println("Список задач пуст.")
        return
    }
    // Group by category for presentation
    val grouped = list.groupBy { it.category }
    for ((cat, items) in grouped) {
        println("📂 Категория: $cat (${items.size})")
        for (t in items) {
            val status = if (t.isCompleted) "✅" else " "
            val pr = priorityEmojis[t.priority] ?: ""
            val overdueMark = if (!t.isCompleted && parseDateStrict(t.dueDate)?.isBefore(LocalDate.now()) == true) "⚠️ Просрочено" else ""
            println("[$status] ID:${t.id} $pr ${t.title} — ${t.dueDate} $overdueMark")
        }
        line()
    }
}

// Input helpers
fun prompt(msg: String): String {
    print("$msg: ")
    return readLine()?.trim() ?: ""
}

fun promptNonEmpty(msg: String): String {
    while (true) {
        val v = prompt(msg)
        if (v.isNotEmpty()) return v
        println("❗ Поле не может быть пустым.")
    }
}

fun chooseFromList(promptMsg: String, options: List<String>, allowNew: Boolean = false): String {
    while (true) {
        println(promptMsg)
        for ((i, o) in options.withIndex()) {
            println("${i + 1}. $o")
        }
        if (allowNew) println("${options.size + 1}. Создать новую")
        val line = prompt("Выбор (номер)")
        val idx = line.toIntOrNull()
        if (idx != null) {
            if (idx in 1..options.size) return options[idx - 1]
            if (allowNew && idx == options.size + 1) {
                val name = promptNonEmpty("Введите имя новой категории")
                categories.add(name)
                return name
            }
        }
        println("Неверный выбор, повторите.")
    }
}

// Main menus and flows
fun flowAddTask() {
    println("Добавление новой задачи")
    val title = promptNonEmpty("Название (обязательное)")
    val desc = prompt("Описание (опционально)")
    val pr = chooseFromList("Выберите приоритет", priorities, allowNew = false)
    // choose or create category
    val catList = categories.toList()
    val cat = chooseFromList("Выберите категорию (или создайте новую)", catList, allowNew = true)
    // due date
    while (true) {
        val dateStr = promptNonEmpty("Дата выполнения (dd.MM.yyyy)")
        if (!validateDueDate(dateStr)) {
            println("❗ Неверный формат даты. Пример: 25.12.2025")
            continue
        }
        val t = createTask(title, desc, pr, dateStr, cat)
        println("✅ Задача создана (ID = ${t.id})")
        break
    }
}

fun flowViewTasks() {
    println("Просмотр задач")
    println("1. Все задачи\n2. Только активные\n3. Только выполненные\n4. Просроченные\n5. По категории\n6. По приоритету\n7. Поиск по тексту")
    when (prompt("Выбор (номер)")) {
        "1" -> listTasks(filterByStatus("all"))
        "2" -> listTasks(filterByStatus("active"))
        "3" -> listTasks(filterByStatus("completed"))
        "4" -> listTasks(overdueTasks())
        "5" -> {
            val cat = chooseFromList("Выберите категорию", categories.toList(), allowNew = false)
            listTasks(filterByCategory(cat))
        }
        "6" -> {
            val pr = chooseFromList("Выберите приоритет", priorities, allowNew = false)
            listTasks(filterByPriority(pr))
        }
        "7" -> {
            val q = promptNonEmpty("Введите поисковый запрос")
            val res = searchByText(q)
            println("Найдено: ${res.size}")
            listTasks(res)
        }
        else -> println("Неверный выбор.")
    }
}

fun flowEditTask() {
    println("Редактирование задачи")
    val id = prompt("Введите ID задачи").toIntOrNull()
    if (id == null) { println("Неверный ID"); return }
    val t = findTaskById(id)
    if (t == null) { println("Задача с таким ID не найдена"); return }
    if (t.isCompleted) { println("Нельзя редактировать выполненную задачу."); return }
    showTask(t)
    println("Что изменить?\n1. Название\n2. Описание\n3. Приоритет\n4. Категорию\n5. Дату выполнения\n6. Отмена")
    when (prompt("Выбор")) {
        "1" -> {
            val new = promptNonEmpty("Новое название")
            t.title = new
            println("✅ Название обновлено")
        }
        "2" -> {
            val new = prompt("Новое описание (оставьте пустым, чтобы очистить)")
            t.description = new
            println("✅ Описание обновлено")
        }
        "3" -> {
            val pr = chooseFromList("Выберите приоритет", priorities, allowNew = false)
            t.priority = pr
            println("✅ Приоритет обновлён")
        }
        "4" -> {
            val cat = chooseFromList("Выберите или создайте категорию", categories.toList(), allowNew = true)
            t.category = cat
            println("✅ Категория обновлена")
        }
        "5" -> {
            while (true) {
                val date = promptNonEmpty("Новая дата (dd.MM.yyyy)")
                if (!validateDueDate(date)) {
                    println("❗ Неверный формат")
                    continue
                }
                t.dueDate = date
                println("✅ Дата выполнения обновлена")
                break
            }
        }
        else -> println("Отмена")
    }
}

fun flowDeleteTask() {
    println("Удаление задачи")
    val id = prompt("Введите ID задачи для удаления").toIntOrNull()
    if (id == null) { println("Неверный ID"); return }
    val t = findTaskById(id)
    if (t == null) { println("Задача не найдена"); return }
    showTask(t)
    val conf = prompt("Подтвердите удаление (yes/no)")
    if (conf.lowercase() == "yes" || conf.lowercase() == "y") {
        if (deleteTask(id)) println("✅ Удалено") else println("Ошибка при удалении")
    } else {
        println("Отмена удаления")
    }
}

fun flowMarkCompleted() {
    val id = prompt("Введите ID задачи для отметки выполненной").toIntOrNull()
    if (id == null) { println("Неверный ID"); return }
    val t = findTaskById(id)
    if (t == null) { println("Задача не найдена"); return }
    if (t.isCompleted) { println("Уже выполнено") ; return }
    showTask(t)
    val conf = prompt("Отметить как выполненную? (yes/no)")
    if (conf.lowercase() in listOf("yes","y")) {
        if (markCompleted(id)) println("✅ Отмечена как выполненная") else println("Ошибка")
    } else println("Отмена")
}

fun flowAnalytics() {
    val s = stats()
    println("📊 Статистика")
    println("Всего задач: ${s["total"]}")
    println("Выполнено: ${s["completed"]}")
    println("Активных: ${s["active"]}")
    println("Процент выполнения: ${"%.2f".format(s["percent"] as Double)}%")
    println()
    println("Распределение по приоритетам:")
    (s["byPriority"] as Map<*, *>).forEach { (k, v) -> println("  $k : $v") }
    println()
    println("Распределение по категориям:")
    (s["byCategory"] as Map<*, *>).forEach { (k, v) -> println("  $k : $v") }
    println()
    println("Просроченных задач: ${s["overdue"]}")
}

// ------------------------- App Entry / Main Loop -------------------------

fun seedDemoData() {
    // optional demo seed to show functionality
    createTask("Сдать отчёт", "Отчёт за месяц", "Высокий", LocalDate.now().plusDays(2).format(dateFormatter), "Работа")
    createTask("Сходить к стоматологу", "", "Средний", LocalDate.now().plusDays(10).format(dateFormatter), "Здоровье")
    createTask("Купить продукты", "Молоко, хлеб", "Низкий", LocalDate.now().minusDays(1).format(dateFormatter), "Личное")
}

fun showMainMenu() {
    line()
    println("TaskMaster — Консольный менеджер задач")
    line()
    println("1. Добавить задачу")
    println("2. Просмотреть задачи")
    println("3. Редактировать задачу")
    println("4. Удалить задачу")
    println("5. Отметить задачу как выполненную")
    println("6. Поиск")
    println("7. Аналитика")
    println("8. Показать все категории")
    println("9. Выход")
    line()
}

fun main() {
    // seed demo data to illustrate (comment out if undesired)
    seedDemoData()

    while (true) {
        showMainMenu()
        when (prompt("Выберите пункт (номер)")) {
            "1" -> flowAddTask()
            "2" -> flowViewTasks()
            "3" -> flowEditTask()
            "4" -> flowDeleteTask()
            "5" -> flowMarkCompleted()
            "6" -> {
                val q = promptNonEmpty("Введите запрос для поиска")
                val res = searchByText(q)
                println("Найдено: ${res.size}")
                listTasks(res)
            }
            "7" -> flowAnalytics()
            "8" -> {
                println("Категории:")
                categories.forEach { println(" - $it") }
            }
            "9" -> {
                println("До свидания 👋")
                exitProcess(0)
            }
            else -> println("Неверный выбор. Введите номер пункта меню.")
        }
        println()
        prompt("Нажмите Enter, чтобы продолжить")
        clearScreen()
    }
}