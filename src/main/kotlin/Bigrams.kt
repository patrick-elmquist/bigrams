#!/usr/bin/env kscript

import java.io.File
import java.util.concurrent.TimeUnit

private val DEFAULT_FILETYPES = listOf("kt")
private val DEFAULT_EXCLUDED_FOLDERS = listOf("build")

private const val PRINT_TOP_COUNT = 25

fun main(args: Array<String>) {
    val extensions = args.parseArg("t") ?: DEFAULT_FILETYPES
    val excludedFolders = args.parseArg("e") ?: DEFAULT_EXCLUDED_FOLDERS
    val inputFile = args.inputFile()

    val (loggers, time) = time {
        when {
            inputFile.isDirectory -> inputFile.analyzeDir(extensions, excludedFolders)
            inputFile.extension in extensions -> inputFile.analyze()
            else -> emptyMap()
        }
    }
    loggers.forEach { (extension, logger) -> logger.print(extension) }
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
    val logger = loggers.getOrPut(extension) { Logger() }
    println("Analyzing file: ${path} ${name}")
    useLines { lines ->
        lines.map { it.trim() }
            .forEach { line ->
                line.forEachIndexed { i, c ->
                    logger.add(c, line.getOrNull(i + 1), line.getOrNull(i + 2))
                }
            }
    }
    return loggers
}

private fun Array<String>.inputFile() = File(last())
private fun Array<String>.parseArg(key: String) =
    firstOrNull { it.startsWith("$key=") }
        ?.drop(key.length + 1)
        ?.split(",")

private class Logger {
    private val singles = mutableMapOf<Char, Int>()
    private val tuples = mutableMapOf<String, Int>()
    private val triples = mutableMapOf<String, Int>()

    fun add(first: Char, second: Char?, third: Char?) {
        when {
            first.isLetter() -> return
            else -> singles.increment(first)
        }

        when {
            first.isDigit() -> return
            second == null -> return
            second.isLetterOrDigit() -> return
            second.isWhitespace() -> Unit
            else -> tuples.increment("$first$second")
        }

        when {
            third == null -> return
            third.isLetterOrDigit() -> return
            first.isWhitespace() || third.isWhitespace() -> return
            else -> triples.increment("$first$second$third")
        }
    }

    fun print(extension: String) {
        val one = singles
            .toList()
            .sortedByDescending { it.second }
            .take(PRINT_TOP_COUNT)

        val two = tuples
            .toList()
            .sortedByDescending { it.second }
            .take(PRINT_TOP_COUNT)

        val three = triples
            .toList()
            .sortedByDescending { it.second }
            .take(PRINT_TOP_COUNT)

        println("Extension '$extension'")
        val n = PRINT_TOP_COUNT
        val header =
            "|Top $n|   Single    |    Tuple     |    Triple   |"
        val horizontalLine =
            "+------+-------------+--------------+-------------+"
        println(horizontalLine)
        println(header.format(PRINT_TOP_COUNT))
        println(horizontalLine)
        (0 until PRINT_TOP_COUNT).forEach {
            val number = (it + 1).toString().padStart(4, ' ').padEnd(5, ' ')
            println("|$number | ${one[it].show()}  |  ${two[it].show()}  |  ${three[it].show()} |")
        }
        println(horizontalLine)
        println()
    }


    private fun <T> Pair<T, Int>.show() = "${first.toString().padStart(2, ' ').padEnd(3, ' ')}  ${second.toString().padEnd(5, ' ')}"
    private fun <T> MutableMap<T, Int>.increment(key: T) = put(key, getOrDefault(key, 0) + 1)
}
