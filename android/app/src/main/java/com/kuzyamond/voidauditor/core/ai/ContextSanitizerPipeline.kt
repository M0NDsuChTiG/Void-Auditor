package com.kuzyamond.voidauditor.core.ai

/**
 * ContextSanitizerPipeline — каскадный пайплайн безопасности на входящем AI-тексте/промпте.
 * Состоит из слоёв: InjectionStripper, SecretRedactor, TokenNormalizer.
 * Абсолютно необходим на каждом входе AI-потока!
 */
object ContextSanitizerPipeline {
    
    fun sanitize(input: String): String {
        var sanitized = input
        sanitized = defaultInjectionStripper(sanitized)
        sanitized = defaultSecretRedactor(sanitized)
        sanitized = defaultTokenNormalizer(sanitized)
        return sanitized
    }

    /**
     * Удаляет известные паттерны prompt-инъекций
     */
    private fun defaultInjectionStripper(text: String): String {
        val patterns = listOf(
            "(?i)sudo ",
            "(?i)rm ",
            "(?i)sh ",
            "(?i)bash ",
            "(?i)system\\(",
            "(?i)os\\.system",
            "(?i)import os",
            "(?i)eval\\(",
            "(?i)exec\\(",
        )
        var out = text
        for (p in patterns) {
            out = out.replace(Regex(p), "[BLOCKED]")
        }
        return out
    }

    /**
     * Маскирует возможные строки с секретами/токенами/паролями
     */
    private fun defaultSecretRedactor(text: String): String {
        // Обычные примитивы: access_token, api_key, password
        return text.replace(Regex("(?i)(access_token|api_key|password)[=:]\\w+"), "$1=[REDACTED]")
    }

    /**
     * Стандартизирует символы, убирает невидимые токены, экранирует служебные символы
     */
    private fun defaultTokenNormalizer(text: String): String {
        return text
            .replace(Regex("[\\u0080-\\u009F]"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }
}
