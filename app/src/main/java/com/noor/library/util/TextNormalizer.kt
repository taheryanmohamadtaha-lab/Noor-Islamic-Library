package com.noor.library.util

/**
 * Normalisation used ONLY for search indexing/matching. The displayed text is never changed.
 * - removes Arabic diacritics (harakat), tatweel and Quranic annotation marks
 * - unifies Persian/Arabic yeh (ی/ي/ى) and kaf (ک/ك), heh forms and alef/hamza forms
 * - converts Persian/Arabic-Indic digits to ASCII
 */
object TextNormalizer {
    private val marks = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u08D3-\\u08FF\\u0640]")

    fun normalize(input: String): String {
        var t = marks.replace(input, "")
        val sb = StringBuilder(t.length)
        for (ch in t) {
            when (ch) {
                'ي', 'ى', 'ئ' -> sb.append('ی')
                'ك' -> sb.append('ک')
                'ۀ', 'ة' -> sb.append('ه')
                'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا')
                'ؤ' -> sb.append('و')
                '‌' -> sb.append(' ')
                '‍', '‏', '‎' -> {}
                in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                else -> sb.append(ch)
            }
        }
        t = sb.toString()
        return t.trim().lowercase()
    }

    /** Eastern-Arabic digits for displaying numbers in the UI. */
    fun toPersianDigits(n: Int): String = n.toString().map { '۰' + (it - '0') }.joinToString("")
}
