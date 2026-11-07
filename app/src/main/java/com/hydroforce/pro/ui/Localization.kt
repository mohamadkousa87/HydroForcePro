package com.hydroforce.pro.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import com.hydroforce.pro.data.Language

/** Current UI language; defaults to Arabic so any untranslated text stays Arabic (safe fallback). */
val LocalLanguage = compositionLocalOf { Language.AR }

/** Picks the Arabic or English string for the current composable language. */
@Composable
@ReadOnlyComposable
fun tr(ar: String, en: String): String =
    if (LocalLanguage.current == Language.EN) en else ar

/** Non-composable variant for notifications/services where the language is known explicitly. */
fun tr(lang: Language, ar: String, en: String): String =
    if (lang == Language.EN) en else ar
