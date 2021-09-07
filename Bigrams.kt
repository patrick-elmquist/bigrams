#!/usr/bin/env kscript

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.max

private val DEFAULT_EXTENSIONS = listOf("kt")
private val DEFAULT_EXCLUDED_FOLDERS = listOf("build")
private const val DEFAULT_N = 25
private const val COMBINED_EXTENSION = "Combined"
private const val PADDING = "  "
private const val N_MAX = 999

fun main(args: Array<String>) {
    val inputFile = args.inputFile().takeIf { it.exists() } ?: return output("Not a valid file path")
    val extensions = args.parseListArg("ext") ?: DEFAULT_EXTENSIONS
    val excludedFolders = args.parseListArg("ignore") ?: DEFAULT_EXCLUDED_FOLDERS
    val nToShow = (args.parseIntArg("top") ?: DEFAULT_N).coerceAtMost(N_MAX)

    val (loggers, time) = time {
        when {
            inputFile.isDirectory -> inputFile.analyzeDir(extensions, excludedFolders)
            inputFile.extension in extensions -> inputFile.analyze()
            else -> emptyMap()
        }
    }

    loggers
        // Don't show the 'All' table if there's only one extension
        .filter { loggers.size != 2 || it.key != COMBINED_EXTENSION }
        .forEach { (extension, logger) -> logger.print(extension, nToShow) }

    output("Analyzes took $time seconds.")
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

private fun File.analyzeDir(
    extensions: List<String>,
    excludedFolders: List<String>
): Map<String, Logger> {
    val loggers = mutableMapOf<String, Logger>()
    output("Analyzing folder: $path")

    val fileCount = walkTopDown()
        .filter { it.extension in extensions }
        .filterNot { file -> excludedFolders.any { it in file.path } }
        .onEach { it.analyze(loggers, carriageReturn = true) }
        .count()
    output("Analyzed $fileCount files", withCarriageReturn = true)
    output()
    return loggers
}

private fun File.analyze(
    loggers: MutableMap<String, Logger> = mutableMapOf(),
    carriageReturn: Boolean = false
): Map<String, Logger> {
    val extensionLogger = loggers.getOrPut(extension) { Logger() }
    val combinedLogger = loggers.getOrPut(COMBINED_EXTENSION) { Logger() }

    output("Analyzing file: $name", withCarriageReturn = carriageReturn)
    useLines { lines ->
        lines.flatMap { line -> line.trim().windowed(3, 1, partialWindows = true) }
            .forEach { triple ->
                extensionLogger.add(triple)
                combinedLogger.add(triple)
            }
    }
    return loggers
}

private class Logger {
    private val map = mutableMapOf<String, Int>()

    fun add(triple: String) {
        val first = triple.getOrNull(0)
        when {
            first == null -> return
            first.isLetter() -> return
            else -> map.increment("$first")
        }

        val second = triple.getOrNull(1)
        when {
            first.isDigit() -> return
            first.isWhitespace() -> return
            second == null -> return
            second.isLetterOrDigit() -> return
            second.isWhitespace() -> Unit
            else -> map.increment("$first$second")
        }

        val third = triple.getOrNull(2)
        when {
            third == null -> return
            third.isLetterOrDigit() -> return
            first.isWhitespace() || third.isWhitespace() -> return
            else -> map.increment("$first$second$third")
        }
    }

    fun print(extension: String, n: Int) {
        val numbers = makeNumberColumn(n)
        val singleColumn = map.makeColumn(1, n)
        val tupleColumn = map.makeColumn(2, n)
        val tripleColumn = map.makeColumn(3, n)

        val columns = listOf(numbers, singleColumn, tupleColumn, tripleColumn)
        val headers = listOf(
            "Top${n.toString().padStart(3, ' ')}",
            "${PADDING}Single",
            "${PADDING}Tuple",
            "${PADDING}Triple"
        )

        val horizontalLine = makeHorizontalLine(columns)
        val headerLine = makeHeaderLine(columns, headers)

        val lines = buildString {
            appendLine("Extension '$extension'")
            appendLine(horizontalLine)
            appendLine(headerLine)
            appendLine(horizontalLine)
            (0 until n).forEach { row ->
                columns.forEach { append('|').append(it[row]) }
                appendLine("|")
            }
            appendLine(horizontalLine)
        }
        output(lines)
    }

    private fun makeNumberColumn(n: Int) = (1..n).map { " ${it.toString().padStart(4, ' ')} " }

    private fun Map<String, Int>.makeColumn(symbolLength: Int, n: Int): List<String> {
        val list = toList()
            .filter { (symbol, _) -> symbol.length == symbolLength }
            .sortedByDescending { (_, count) -> count }
            .take(n)
        val (symbolLen, countLen) = list.first().let { (symbol, count) -> symbol.length to count.toString().length }
        val rowLength = symbolLen + countLen + 3 * PADDING.length
        return list.map { (symbol, count) ->
            val paddedCount = count.toString().padStart(countLen, ' ')
            "$PADDING$symbol$PADDING$paddedCount$PADDING"
        } + (0 until (n - list.size)).map { repeat(rowLength, ' ') }
    }

    private fun makeHeaderLine(columns: List<List<String>>, headers: List<String>) =
        buildString {
            val zip = headers.zip(columns.map { it.first().length })
            append('|')
            zip.forEach { (header, columnLen) ->
                append(header.padEnd(columnLen, ' ')).append('|')
            }
        }

    private fun makeHorizontalLine(cols: List<List<String>>, sep: Char = '+') =
        buildString {
            append(sep)
            cols.forEach { append(repeat(it.first().length, '-')).append(sep) }
        }

    private fun repeat(n: Int, c: Char) = buildString { repeat(n) { append(c) } }

    private fun <T> MutableMap<T, Int>.increment(key: T) = put(key, getOrDefault(key, 0) + 1)
}

private fun <T> time(block: () -> T): Pair<T, Long> {
    val before = System.currentTimeMillis()
    val value = block()
    val after = System.currentTimeMillis()
    return value to TimeUnit.MILLISECONDS.toSeconds(after - before)
}

private fun output(msg: String = "", withCarriageReturn: Boolean = false) = Printer.out(msg, withCarriageReturn)

private object Printer {
    private var longestLine = 0
    private var wasLastWithCR: Boolean = false
    fun out(msg: String, withCarriageReturn: Boolean = false) {
        if (withCarriageReturn) {
            wasLastWithCR = true
            val output = "\r$msg"
            longestLine = max(output.length, longestLine)
            print(output.padEnd(longestLine, ' '))
        } else {
            if (wasLastWithCR) {
                wasLastWithCR = false
                longestLine = 0
                println()
            }
            println(msg)
        }
    }
}

