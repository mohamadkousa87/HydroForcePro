package com.hydroforce.pro.data

/** App appearance preference. */
enum class ThemeMode(val label: String, val labelEn: String) {
    SYSTEM("حسب النظام", "System"),
    LIGHT("فاتح", "Light"),
    DARK("داكن", "Dark");

    fun label(lang: Language): String = if (lang == Language.EN) labelEn else label
}
