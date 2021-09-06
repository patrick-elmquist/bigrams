#!/usr/bin/env kscript

import java.io.File
import java.util.concurrent.TimeUnit

private val DEFAULT_EXTENSIONS = listOf("kt")
private val DEFAULT_EXCLUDED_FOLDERS = listOf("build")
private const val DEFAULT_N = 25
private const val ALL_EXTENSIONS = "All"
private const val PADDING = "  "
private const val N_MAX = 999

fun main(args: Array<String>) {
    val extensions = args.parseListArg("t") ?: DEFAULT_EXTENSIONS
    val excludedFolders = args.parseListArg("e") ?: DEFAULT_EXCLUDED_FOLDERS
    val nToShow = (args.parseIntArg("n") ?: DEFAULT_N).coerceAtMost(N_MAX)
    val path = args.inputFile()

    val (loggers, time) = time {
        when {
            path.isDirectory -> path.analyzeDir(extensions, excludedFolders)
            path.extension in extensions -> path.analyze()
            else -> emptyMap()
        }
    }

    loggers
        // Don't show the 'All' table if there's only one extension
        .filter { loggers.size != 2 || it.key != ALL_EXTENSIONS }
        .forEach { (extension, logger) -> logger.print(extension, nToShow) }

    println("Analyzes took $time seconds.")
}

private fun <T> time(block: () -> T): Pair<T, Long> {
    val before = System.currentTimeMillis()
    val value = block()
    val after = System.currentTimeMillis()
    return value to TimeUnit.MILLISECONDS.toSeconds(after - before)
}

private fun File.analyzeDir(extensions: List<String>, excludedFolders: List<String>): Map<String, Logger> {
    val loggers = mutableMapOf<String, Logger>()
    println("Analyzing folder: $path")
    val fileCount = walkTopDown()
        .filter { it.extension in extensions }
        .filterNot { file -> excludedFolders.any { it in file.path } }
        .onEach { it.analyze(loggers) }
        .count()
    println("Analyzed $fileCount files")
    return loggers
}

private fun File.analyze(loggers: MutableMap<String, Logger> = mutableMapOf()): Map<String, Logger> {
    val extensionLogger = loggers.getOrPut(extension) { Logger() }
    val allLogger = loggers.getOrPut(ALL_EXTENSIONS) { Logger() }
    println("Analyzing file: $path")
    useLines { lines ->
        lines.flatMap { line -> line.trim().windowed(3, 1, partialWindows = true) }
            .forEach { triple ->
                extensionLogger.add(triple)
                allLogger.add(triple)
            }
    }
    return loggers
}

private fun Array<String>.inputFile() = File(last())
private fun Array<String>.parseListArg(key: String) =
    firstOrNull { it.startsWith("$key=") }
        ?.drop(key.length + 1)
        ?.split(",")

private fun Array<String>.parseIntArg(key: String) =
    firstOrNull { it.startsWith("$key=") }
        ?.drop(key.length + 1)
        ?.toIntOrNull()

private class Logger {
    private val map = mutableMapOf<String, Int>()

    fun add(s: String) {
        val first = s.getOrNull(0)
        when {
            first == null -> return
            first.isLetter() -> return
            else -> map.increment("$first")
        }

        val second = s.getOrNull(1)
        when {
            first.isDigit() -> return
            first.isWhitespace() -> return
            second == null -> return
            second.isLetterOrDigit() -> return
            second.isWhitespace() -> Unit
            else -> map.increment("$first$second")
        }

        val third = s.getOrNull(2)
        when {
            third == null -> return
            third.isLetterOrDigit() -> return
            first.isWhitespace() || third.isWhitespace() -> return
            else -> map.increment("$first$second$third")
        }
    }

    fun print(extension: String, n: Int) {
        val singleColumn = map.makeColumn(1, n)
        val tupleColumn = map.makeColumn(2, n)
        val tripleColumn = map.makeColumn(3, n)
        val numbers = (1..n).map { " ${it.toString().padStart(4, ' ')} " }

        val columns = listOf(
            "Top${n.toString().padStart(3, ' ')}" to numbers,
            "${PADDING}Single" to singleColumn,
            "${PADDING}Tuple" to tupleColumn,
            "${PADDING}Triple" to tripleColumn,
        )
        val horizontalLine = makeHorizontalLine(columns.map { it.second })
        val headers = makeHeaderLine(columns)

        val lines = buildString {
            appendLine("Extension '$extension'")
            appendLine(horizontalLine)
            appendLine(headers)
            appendLine(horizontalLine)
            (0 until n).forEach { row ->
                columns.map { it.second }.map { it[row] }.forEach { append('|').append(it) }
                appendLine("|")
            }
            appendLine(horizontalLine)
        }
        println(lines)
        println()
    }

    private fun Map<String, Int>.makeColumn(len: Int, n: Int): List<String> {
        val list = toList()
            .filter { it.first.length == len }
            .sortedByDescending { (_, count) -> count }
            .take(n)
        val symbolLen = list.first().first.length
        val countMaxLen = list.first().second.toString().length
        val totalPadding = 3 * PADDING.length
        val rowLength = symbolLen + countMaxLen + totalPadding
        return list.map { (symbol, count) ->
            val paddedCount = count.toString().padStart(countMaxLen, ' ')
            "$PADDING$symbol$PADDING$paddedCount$PADDING"
        } + (0 until (n - list.size)).map { buildString { repeat(rowLength) { append(' ') } } }
    }

    private fun makeHeaderLine(cols: List<Pair<String, List<String>>>): String {
        return buildString {
            append('|')
            cols.forEach { (header, col) ->
                val len = col.first().length
                append(header.padEnd(len, ' '))
                append('|')
            }
        }
    }

    private fun makeHorizontalLine(cols: List<List<String>>, sep: Char = '+'): String {
        return buildString {
            cols.forEach {
                append(sep)
                repeat(it.first().length) { append('-') }
            }
            append(sep)
        }
    }

    private fun <T> MutableMap<T, Int>.increment(key: T) = put(key, getOrDefault(key, 0) + 1)
}
