package com.noor.library.data

/** Static book metadata (titles only — no scripture text lives in code). */
data class BookInfo(val id: String, val title: String, val subtitle: String, val sectionNoun: String, val passageNoun: String)

object BookCatalog {
    val all = listOf(
        BookInfo("quran", "قرآن کریم", "۱۱۴ سوره", "سوره", "آیه"),
        BookInfo("mafatih", "مفاتیح الجنان", "دعاها و زیارات", "بخش", "فراز"),
        BookInfo("sahifa", "صحیفه سجادیه", "۵۴ دعا", "دعا", "فراز"),
        BookInfo("nahj", "نهج‌البلاغه", "خطبه‌ها، نامه‌ها و حکمت‌ها", "بخش", "فراز"),
    )

    fun byId(id: String): BookInfo = all.firstOrNull { it.id == id } ?: all.first()

    fun sectionTypeLabel(type: String): String = when (type) {
        "surah" -> "سوره"
        "dua" -> "دعا"
        "ziyarat" -> "زیارت"
        "amal" -> "اعمال"
        "khutba" -> "خطبه"
        "letter" -> "نامه"
        "hikma" -> "حکمت"
        else -> "بخش"
    }
}
