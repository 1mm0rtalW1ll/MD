import java.util.*
import kotlin.math.pow

fun main() {
    println("Kotlin Console Calculator")
    println("Поддержка: + - * / ^ ( ) %  | переменная ans  | введите 'exit' для выхода")
    var lastAns = 0.0
    val scanner = Scanner(System.`in`)
    while (true) {
        print("> ")
        if (!scanner.hasNextLine()) break
        val line = scanner.nextLine().trim()
        if (line.equals("exit", ignoreCase = true) || line.equals("quit", ignoreCase = true)) {
            println("Bye!")
            break
        }
        if (line.isEmpty()) continue

        try {
            val replaced = line.replace(Regex("""\bans\b""", RegexOption.IGNORE_CASE)) { lastAns.toString() }
            val rpn = toRPN(replaced)
            val result = evalRPN(rpn)
            if (result.isNaN()) {
                println("Ошибка вычисления (NaN).")
            } else if (result.isInfinite()) {
                println("Ошибка: бесконечность (возможно деление на ноль).")
            } else {
                println(result)
                lastAns = result
            }
        } catch (e: CalcException) {
            println("Ошибка: ${e.message}")
        } catch (e: Exception) {
            println("Неожиданная ошибка: ${e.message}")
        }
    }
}

// --- Exceptions
class CalcException(message: String): Exception(message)

// --- Token types
sealed class Token {
    data class Number(val value: Double) : Token()
    data class Op(val op: String) : Token()
    object LeftParen : Token()
    object RightParen : Token()
}

// --- Tokenizer
fun tokenize(expr: String): List<Token> {
    val tokens = mutableListOf<Token>()
    val s = expr.replace("\\s+".toRegex(), "")
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c.isDigit() || c == '.' -> {
                val start = i
                var dotCount = if (c == '.') 1 else 0
                i++
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) {
                    if (s[i] == '.') {
                        dotCount++
                        if (dotCount > 1) throw CalcException("Неверный формат числа")
                    }
                    i++
                }
                val numStr = s.substring(start, i)
                val value = numStr.toDoubleOrNull() ?: throw CalcException("Неверное число: $numStr")
                tokens.add(Token.Number(value))
                continue
            }
            c == '(' -> {
                tokens.add(Token.LeftParen)
                i++
            }
            c == ')' -> {
                tokens.add(Token.RightParen)
                i++
            }
            c == '+' || c == '-' || c == '*' || c == '/' || c == '^' -> {
                // handle unary plus/minus: if at start or after left paren or after another operator
                val op = c.toString()
                // detect unary minus/plus -> convert to 'u-' / 'u+' which we'll treat specially
                if (op == "+" || op == "-") {
                    val prev = tokens.lastOrNull()
                    if (prev == null || prev is Token.Op || prev is Token.LeftParen) {
                        tokens.add(Token.Op("u$op")) // unary
                        i++
                        continue
                    }
                }
                tokens.add(Token.Op(op))
                i++
            }
            c == '%' -> {
                // treat % as postfix unary operator (divide by 100)
                tokens.add(Token.Op("%"))
                i++
            }
            else -> throw CalcException("Неожиданный символ: $c")
        }
    }
    return tokens
}

// --- Shunting-yard -> RPN
fun toRPN(expr: String): List<Token> {
    val output = mutableListOf<Token>()
    val ops = Stack<Token.Op>()
    val tokens = tokenize(expr)

    // operator precedence and associativity
    val prec = mapOf(
        "u+" to 5, "u-" to 5, // unary
        "^" to 4,
        "*" to 3, "/" to 3,
        "+" to 2, "-" to 2,
        "%" to 6 // postfix highest (we'll handle as right-assoc unary)
    )
    val rightAssoc = setOf("^", "u+", "u-", "%")

    for (t in tokens) {
        when (t) {
            is Token.Number -> output.add(t)
            is Token.Op -> {
                when (t.op) {
                    "u+", "u-", "%", "^", "*", "/", "+", "-" -> {
                        while (ops.isNotEmpty()) {
                            val top = ops.peek()
                            val tp = top.op
                            val p1 = prec[tp] ?: 0
                            val p2 = prec[t.op] ?: 0
                            if ((p1 > p2) || (p1 == p2 && !rightAssoc.contains(t.op))) {
                                output.add(ops.pop())
                            } else break
                        }
                        ops.push(t)
                    }
                    else -> throw CalcException("Неизвестный оператор: ${t.op}")
                }
            }
            is Token.LeftParen -> ops.push(Token.Op("("))
            is Token.RightParen -> {
                while (ops.isNotEmpty() && ops.peek().op != "(") {
                    output.add(ops.pop())
                }
                if (ops.isEmpty() || ops.peek().op != "(") throw CalcException("Несбалансированные скобки")
                ops.pop() // pop '('
            }
        }
    }
    while (ops.isNotEmpty()) {
        val op = ops.pop()
        if (op.op == "(" || op.op == ")") throw CalcException("Несбалансированные скобки")
        output.add(op)
    }
    return output
}

// --- Evaluate RPN
fun evalRPN(rpn: List<Token>): Double {
    val st = Stack<Double>()
    for (t in rpn) {
        when (t) {
            is Token.Number -> st.push(t.value)
            is Token.Op -> {
                when (t.op) {
                    "u+" -> {
                        if (st.isEmpty()) throw CalcException("Операнд отсутствует для унарного +")
                        val a = st.pop()
                        st.push(+a)
                    }
                    "u-" -> {
                        if (st.isEmpty()) throw CalcException("Операнд отсутствует для унарного -")
                        val a = st.pop()
                        st.push(-a)
                    }
                    "%" -> {
                        if (st.isEmpty()) throw CalcException("Операнд отсутствует для %")
                        val a = st.pop()
                        st.push(a / 100.0)
                    }
                    "+" -> {
                        if (st.size < 2) throw CalcException("Недостаточно операндов для +")
                        val b = st.pop(); val a = st.pop()
                        st.push(a + b)
                    }
                    "-" -> {
                        if (st.size < 2) throw CalcException("Недостаточно операндов для -")
                        val b = st.pop(); val a = st.pop()
                        st.push(a - b)
                    }
                    "*" -> {
                        if (st.size < 2) throw CalcException("Недостаточно операндов для *")
                        val b = st.pop(); val a = st.pop()
                        st.push(a * b)
                    }
                    "/" -> {
                        if (st.size < 2) throw CalcException("Недостаточно операндов для /")
                        val b = st.pop(); val a = st.pop()
                        if (b == 0.0) throw CalcException("Деление на ноль")
                        st.push(a / b)
                    }
                    "^" -> {
                        if (st.size < 2) throw CalcException("Недостаточно операндов для ^")
                        val b = st.pop(); val a = st.pop()
                        st.push(a.pow(b))
                    }
                    else -> throw CalcException("Неизвестный оператор в RPN: ${t.op}")
                }
            }
            else -> throw CalcException("Непредвиденный токен при вычислении")
        }
    }
    if (st.size != 1) throw CalcException("Неверное выражение")
    return st.pop()
}
