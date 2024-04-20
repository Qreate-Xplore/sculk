package tech.jamalam

import biz.source_code.utils.RawConsoleInput
import com.github.ajalt.mordant.terminal.ConversionResult
import com.github.ajalt.mordant.terminal.Prompt
import com.github.ajalt.mordant.terminal.Terminal
import io.ktor.http.*
import java.io.File
import kotlin.system.exitProcess

class PrettyListPrompt(prompt: String, choices: Collection<String>, terminal: Terminal) :
    Prompt<String>(prompt, terminal, choices = choices) {
    override fun ask(): String {
        var choiceIdx = 0
        terminal.cursor.hide(true)
        terminal.println(buildString {
            append(terminal.theme.muted("?"))
            append(" ")
            append(prompt)
            append(" ")
            append(
                terminal.theme.muted(
                    "(arrow keys to move, enter to select)"
                )
            )
        })

        var printedLines: Int

        while (true) {
            printedLines = printChoices(choices, choiceIdx)
            val char = RawConsoleInput.read(true)

            when (char) {
                3 -> exitProcess(0) // CTRL-C
                10 -> break // Enter
                27 -> {
                    if (RawConsoleInput.read(true) == 91) {
                        when (RawConsoleInput.read(true)) {
                            // Up arrow
                            65 -> choiceIdx = if (choiceIdx == 0) {
                                choices.size - 1
                            } else {
                                choiceIdx - 1
                            }
                            // Down arrow
                            66 -> choiceIdx = (choiceIdx + 1) % choices.size
                        }
                    }
                }
            }

            terminal.clearLines(printedLines)
        }

        terminal.clearLines(printedLines + 1)
        terminal.println(buildString {
            append(terminal.theme.muted("?"))
            append(" ")
            append(prompt)
            append(" ")
            append(
                terminal.theme.info(
                    choices.elementAt(choiceIdx)
                )
            )
        })

        terminal.cursor.show()
        RawConsoleInput.resetConsoleMode()
        return choices.elementAt(choiceIdx)
    }

    override fun convert(input: String): ConversionResult<String> = ConversionResult.Valid(input)

    private fun printChoices(choices: Collection<String>, choiceIdx: Int): Int {
        if (choices.size > 5) {
            val startIdx = choiceIdx - 2
            val endIdx = choiceIdx + 2

            for (idx in startIdx..endIdx) {
                var idx = idx

                if (idx < 0) {
                    idx += choices.size
                }

                if (idx >= choices.size) {
                    idx -= choices.size
                }

                printChoice(choices.elementAt(idx), idx == choiceIdx)
            }

            return 5
        } else {
            for ((idx, choice) in choices.withIndex()) {
                printChoice(choice, idx == choiceIdx)
            }

            return choices.size
        }
    }

    private fun printChoice(choice: String, isSelected: Boolean) {
        terminal.println(buildString {
            if (isSelected) {
                append(terminal.theme.muted("> "))
                append(terminal.theme.info(choice))
            } else {
                append("  ")
                append(choice)
            }
        })
    }
}

abstract class PrettyPrompt<T>(
    prompt: String, terminal: Terminal, default: T? = null
) : Prompt<T>(prompt, terminal, default = default) {
    override fun ask(): T {
        var input = ""
        var error = false

        while (true) {
            terminal.print(buildString {
                if (error) {
                    append(terminal.theme.danger("?"))
                } else {
                    append(terminal.theme.muted("?"))
                }

                append(" ")
                append(prompt)

                if (default != null) {
                    append(terminal.theme.info(" ($default)"))
                }

                append(": ")
                append(input)
            })

            when (val char = RawConsoleInput.read(true)) {
                3 -> exitProcess(0) // CTRL-C
                10 -> { // Enter
                    return if (input.isNotEmpty()) {
                        when (val conversion = convert(input)) {
                            is ConversionResult.Valid -> {
                                terminal.println()
                                RawConsoleInput.resetConsoleMode()
                                conversion.value
                            }

                            is ConversionResult.Invalid -> {
                                error = true
                                input = ""
                                terminal.clearLine()
                                continue
                            }
                        }
                    } else if (default != null) {
                        RawConsoleInput.resetConsoleMode()
                        default!!
                    } else {
                        error = true
                        terminal.clearLine()
                        continue
                    }
                }

                127 -> { // Backspace
                    input = input.dropLast(1)
                    terminal.clearLine()
                }

                27 -> {} // Control code
                else -> {
                    terminal.clearLine()
                    input += char.toChar()
                }
            }
        }
    }
}

class StringPrettyPrompt(
    prompt: String, terminal: Terminal, default: String? = null
) : PrettyPrompt<String>(prompt, terminal, default = default) {
    override fun convert(input: String): ConversionResult<String> = ConversionResult.Valid(input)
}

class FilePrettyPrompt(
    prompt: String, terminal: Terminal, default: File? = null
) : PrettyPrompt<File>(prompt, terminal, default = default) {
    override fun convert(input: String): ConversionResult<File> =
        ConversionResult.Valid(File(input))
}

class UrlPrettyPrompt(
    prompt: String, terminal: Terminal, default: Url? = null
) : PrettyPrompt<Url>(prompt, terminal, default = default) {
    override fun convert(input: String): ConversionResult<Url> {
        return try {
            ConversionResult.Valid(URLBuilder().takeFrom(input).build())
        } catch (e: URLParserException) {
            ConversionResult.Invalid("Invalid URL")
        }
    }
}
