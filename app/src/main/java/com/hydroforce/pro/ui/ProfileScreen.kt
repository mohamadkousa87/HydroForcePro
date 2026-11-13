package com.hydroforce.pro.ui

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hydroforce.pro.data.ActivityLevel
import com.hydroforce.pro.data.CUP_SIZES
import com.hydroforce.pro.data.Gender
import com.hydroforce.pro.data.HydrationCalculator
import com.hydroforce.pro.data.HydrationResult
import com.hydroforce.pro.data.Language
import com.hydroforce.pro.data.UserProfile
import com.hydroforce.pro.data.formatClock
import com.hydroforce.pro.data.formatDuration
import com.hydroforce.pro.data.formatMl
import com.hydroforce.pro.update.UpdateSection
import com.hydroforce.pro.update.rememberUpdateController
import java.util.Locale

/** Shared by onboarding and settings: medical profile, container, and daily schedule. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    initial: UserProfile,
    isOnboarding: Boolean,
    onSave: (UserProfile) -> Unit,
    onBack: (() -> Unit)?
) {
    var gender by rememberSaveable { mutableStateOf(initial.gender) }
    var ageText by rememberSaveable { mutableStateOf(initial.age.toString()) }
    var weightText by rememberSaveable { mutableStateOf(HydrationCalculator.formatNum(initial.weightKg)) }
    var heightText by rememberSaveable { mutableStateOf(initial.heightCm.toString()) }
    var activity by rememberSaveable { mutableStateOf(initial.activity) }
    var cup by rememberSaveable { mutableIntStateOf(initial.cupSizeMl) }
    var wake by rememberSaveable { mutableIntStateOf(initial.wakeMinutes) }
    var sleep by rememberSaveable { mutableIntStateOf(initial.sleepMinutes) }
    var workEnabled by rememberSaveable { mutableStateOf(initial.workModeEnabled) }
    var workStart by rememberSaveable { mutableIntStateOf(initial.workStartMinutes) }
    var workEnd by rememberSaveable { mutableIntStateOf(initial.workEndMinutes) }
    var ringtoneUri by rememberSaveable { mutableStateOf(initial.ringtoneUri) }
    var vibration by rememberSaveable { mutableStateOf(initial.vibrationEnabled) }
    var mealsEnabled by rememberSaveable { mutableStateOf(initial.mealsEnabled) }
    var breakfast by rememberSaveable { mutableIntStateOf(initial.breakfastMinutes) }
    var lunch by rememberSaveable { mutableIntStateOf(initial.lunchMinutes) }
    var dinner by rememberSaveable { mutableIntStateOf(initial.dinnerMinutes) }
    var breakfastOn by rememberSaveable { mutableStateOf(initial.breakfastOn) }
    var lunchOn by rememberSaveable { mutableStateOf(initial.lunchOn) }
    var dinnerOn by rememberSaveable { mutableStateOf(initial.dinnerOn) }

    val age = ageText.toIntOrNull()
    val weight = weightText.replace(',', '.').toFloatOrNull()
    val height = heightText.toIntOrNull()
    val ageError = age == null || age !in 5..120
    val weightError = weight == null || weight !in 20f..300f
    val heightError = height == null || height !in 80..250
    val sleepError = wake == sleep
    val valid = !ageError && !weightError && !heightError && !sleepError

    val draft = UserProfile(
        gender = gender,
        age = age ?: initial.age,
        weightKg = weight ?: initial.weightKg,
        heightCm = height ?: initial.heightCm,
        activity = activity,
        cupSizeMl = cup,
        wakeMinutes = wake,
        sleepMinutes = sleep,
        workModeEnabled = workEnabled,
        workStartMinutes = workStart,
        workEndMinutes = workEnd,
        ringtoneUri = ringtoneUri,
        vibrationEnabled = vibration,
        mealsEnabled = mealsEnabled,
        breakfastMinutes = breakfast,
        lunchMinutes = lunch,
        dinnerMinutes = dinner,
        breakfastOn = breakfastOn,
        lunchOn = lunchOn,
        dinnerOn = dinnerOn
    )
    val lang = LocalLanguage.current
    val result = HydrationCalculator.calculate(draft, lang)
    val ml = tr("مل", "ml")

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                TextButton(onClick = onBack) { Text(tr("→ رجوع", "→ Back")) }
            }
            Text(
                if (isOnboarding) tr("مرحباً بك في VeroSip 💧", "Welcome to VeroSip 💧") else tr("الملف الطبي والجدول", "Medical profile & schedule"),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (isOnboarding) {
            Text(
                tr(
                    "أدخل بياناتك لحساب احتياجك الطبي اليومي من الماء وتوزيع التنبيهات على ساعات يقظتك.",
                    "Enter your details to compute your daily medical water need and spread reminders across your waking hours."
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Section(tr("🧬 البيانات الحيوية", "🧬 Vital data")) {
            Text(tr("الجنس", "Gender"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Gender.entries.forEach {
                    FilterChip(
                        selected = gender == it,
                        onClick = { gender = it },
                        label = { Text(if (it == Gender.MALE) "👨 ${it.label(lang)}" else "👩 ${it.label(lang)}") }
                    )
                }
            }
            NumberField(tr("العمر (سنة)", "Age (years)"), ageText, ageError) { ageText = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(tr("الوزن (كغ)", "Weight (kg)"), weightText, weightError, Modifier.weight(1f), decimal = true) { weightText = it }
                NumberField(tr("الطول (سم)", "Height (cm)"), heightText, heightError, Modifier.weight(1f)) { heightText = it }
            }
            if (!weightError && !heightError) {
                val bmi = HydrationCalculator.bmi(draft)
                Text(
                    tr(
                        "مؤشر كتلة الجسم (BMI): ${String.format(Locale.US, "%.1f", bmi)} — ${bmiLabel(bmi, lang)}",
                        "BMI: ${String.format(Locale.US, "%.1f", bmi)} — ${bmiLabel(bmi, lang)}"
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Section(tr("🏃 مستوى النشاط البدني", "🏃 Physical activity level")) {
            ActivityLevel.entries.forEach { level ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (activity == level) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { activity = level }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = activity == level, onClick = { activity = level })
                    Column(Modifier.weight(1f)) {
                        Text(level.label(lang), fontWeight = FontWeight.SemiBold)
                        Text(level.description(lang), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("+${level.bonusMl} $ml", color = HydroBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Section(tr("🥤 حجم الكأس / العبوة المعتادة", "🥤 Usual cup / bottle size")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CUP_SIZES.forEach {
                    FilterChip(selected = cup == it, onClick = { cup = it }, label = { Text("$it $ml") })
                }
            }
        }

        Section(tr("🕒 الجدول اليومي", "🕒 Daily schedule")) {
            TimeRow(tr("☀️ وقت الاستيقاظ", "☀️ Wake-up time"), wake) { wake = it }
            TimeRow(tr("🌙 وقت النوم", "🌙 Bedtime"), sleep) { sleep = it }
            if (sleepError) {
                Text(tr("وقت النوم يجب أن يختلف عن وقت الاستيقاظ", "Bedtime must differ from wake-up time"), color = MaterialTheme.colorScheme.error)
            } else {
                Text(
                    tr(
                        "ساعات اليقظة: ${formatDuration(result.awakeMinutes, lang)} — لا تنبيهات إطلاقاً أثناء النوم 😴",
                        "Waking hours: ${formatDuration(result.awakeMinutes, lang)} — no reminders at all during sleep 😴"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("💼 وضع العمل الهادئ", "💼 Quiet work mode"), fontWeight = FontWeight.SemiBold)
                    Text(
                        tr(
                            "اهتزاز ونغمة خفيفة مع زر كتم خلال ساعات العمل — والتوثيق بالكاميرا يبقى إلزامياً",
                            "Vibration and a soft chime with a mute button during work hours — camera verification stays mandatory"
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = workEnabled, onCheckedChange = { workEnabled = it })
            }
            if (workEnabled) {
                TimeRow(tr("بداية العمل", "Work start"), workStart) { workStart = it }
                TimeRow(tr("نهاية العمل", "Work end"), workEnd) { workEnd = it }
            }
        }

        AlarmSoundSection(
            ringtoneUri = ringtoneUri,
            vibration = vibration,
            onRingtoneChange = { ringtoneUri = it },
            onVibrationChange = { vibration = it }
        )

        Section(tr("🍽️ التوقف حول الوجبات", "🍽️ Pause around meals")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("إيقاف التنبيهات وقت الأكل", "Pause reminders at meal times"), fontWeight = FontWeight.SemiBold)
                    Text(
                        tr("يتوقف التنبيه 30 دقيقة قبل الوجبة و45 دقيقة بعدها.", "Reminders stop 30 min before and 45 min after each meal."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = mealsEnabled, onCheckedChange = { mealsEnabled = it })
            }
            if (mealsEnabled) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                MealRow(tr("🍳 الفطور", "🍳 Breakfast"), breakfastOn, breakfast, { breakfastOn = it }) { breakfast = it }
                MealRow(tr("🍚 الغداء", "🍚 Lunch"), lunchOn, lunch, { lunchOn = it }) { lunch = it }
                MealRow(tr("🍲 العشاء", "🍲 Dinner"), dinnerOn, dinner, { dinnerOn = it }) { dinner = it }
            }
        }

        ResultCard(result, cup, title = tr("📋 هدفك الطبي اليومي", "📋 Your daily medical goal"))

        if (!isOnboarding) {
            ThemeModeSection()
            val updateController = rememberUpdateController()
            UpdateSection(updateController)
        }

        Button(
            onClick = { onSave(draft) },
            enabled = valid,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(if (isOnboarding) tr("ابدأ الالتزام بالترطيب 🚀", "Start staying hydrated 🚀") else tr("حفظ الإعدادات ✅", "Save settings ✅"))
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun bmiLabel(bmi: Float, lang: Language) = when {
    bmi < 18.5f -> if (lang == Language.EN) "Underweight" else "نقص وزن"
    bmi < 25f -> if (lang == Language.EN) "Normal" else "طبيعي"
    bmi < 30f -> if (lang == Language.EN) "Overweight" else "زيادة وزن"
    else -> if (lang == Language.EN) "Obese" else "سمنة"
}

@Composable
private fun MealRow(
    label: String,
    enabled: Boolean,
    minutes: Int,
    onEnabledChange: (Boolean) -> Unit,
    onPick: (Int) -> Unit
) {
    val context = LocalContext.current
    val lang = LocalLanguage.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
        Spacer(Modifier.width(8.dp))
        Text(label, Modifier.weight(1f), color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.material3.OutlinedButton(
            onClick = {
                TimePickerDialog(context, { _, h, m -> onPick(h * 60 + m) }, minutes / 60, minutes % 60, false).show()
            },
            enabled = enabled,
            border = BorderStroke(1.dp, HydroBlue),
            shape = RoundedCornerShape(12.dp)
        ) { Text(formatClock(minutes, lang)) }
    }
}

@Composable
private fun AlarmSoundSection(
    ringtoneUri: String,
    vibration: Boolean,
    onRingtoneChange: (String) -> Unit,
    onVibrationChange: (Boolean) -> Unit
) {
    val context = LocalContext.current

    val defaultLabel = tr("الافتراضي (نغمة منبه النظام)", "Default (system alarm tone)")
    val customLabel = tr("نغمة مخصصة محدّدة", "Custom tone selected")
    val ringtoneTitle = remember(ringtoneUri, defaultLabel, customLabel) {
        when {
            ringtoneUri.isBlank() -> defaultLabel
            else -> runCatching {
                RingtoneManager.getRingtone(context, Uri.parse(ringtoneUri))?.getTitle(context)
            }.getOrNull() ?: customLabel
        }
    }

    val pickRingtone = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            onRingtoneChange(uri?.toString() ?: "")
        }
    }

    val pickAudioFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onRingtoneChange(uri.toString())
        }
    }

    val pickerTitle = tr("اختر نغمة التنبيه", "Choose alarm tone")

    Section(tr("🔔 نغمة التنبيه والاهتزاز", "🔔 Alarm tone & vibration")) {
        Text(tr("النغمة الحالية:", "Current tone:"), style = MaterialTheme.typography.labelLarge)
        Text(ringtoneTitle, fontWeight = FontWeight.SemiBold, color = HydroBlue)
        Text(
            tr("تُستخدم نفس النغمة في الوضع العادي والوضع الهادئ.", "The same tone is used in both normal and quiet modes."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedButton(
                onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(
                            RingtoneManager.EXTRA_RINGTONE_TYPE,
                            RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_RINGTONE
                        )
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, pickerTitle)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        putExtra(
                            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                            ringtoneUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                        )
                    }
                    pickRingtone.launch(intent)
                },
                border = BorderStroke(1.dp, HydroBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) { Text(tr("نغمات الجهاز", "Device tones")) }
            androidx.compose.material3.OutlinedButton(
                onClick = { pickAudioFile.launch(arrayOf("audio/*")) },
                border = BorderStroke(1.dp, HydroBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) { Text(tr("ملف صوتي", "Audio file")) }
        }
        if (ringtoneUri.isNotBlank()) {
            TextButton(onClick = { onRingtoneChange("") }) {
                Text(tr("إعادة للنغمة الافتراضية", "Reset to default tone"))
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("📳 الاهتزاز", "📳 Vibration"), fontWeight = FontWeight.SemiBold)
                Text(
                    tr("اهتزاز الجهاز مع التنبيه", "Vibrate the device with the alarm"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = vibration, onCheckedChange = onVibrationChange)
        }
    }
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            content()
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    isError: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    decimal: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(5)) },
        label = { Text(label) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    )
}

@Composable
private fun TimeRow(label: String, minutes: Int, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    val lang = LocalLanguage.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f))
        androidx.compose.material3.OutlinedButton(
            onClick = {
                TimePickerDialog(context, { _, h, m -> onPick(h * 60 + m) }, minutes / 60, minutes % 60, false).show()
            },
            border = BorderStroke(1.dp, HydroBlue),
            shape = RoundedCornerShape(12.dp)
        ) { Text(formatClock(minutes, lang)) }
    }
}

@Composable
fun ResultCard(result: HydrationResult, cup: Int, title: String = "📋 هدفك الطبي اليومي") {
    val lang = LocalLanguage.current
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            val ml = tr("مل", "ml")
            Text(
                tr("${formatMl(result.targetMl)} مل يومياً", "${formatMl(result.targetMl)} ml per day"),
                style = MaterialTheme.typography.headlineMedium,
                color = HydroBlue
            )
            Text(
                tr(
                    "= ${result.cupsNeeded} كأس × $cup مل • تنبيه كل ${formatDuration(result.intervalMinutes, lang)}",
                    "= ${result.cupsNeeded} cups × $cup ml • reminder every ${formatDuration(result.intervalMinutes, lang)}"
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(tr("لماذا هذا الهدف لجسمك؟", "Why this goal for your body?"), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            result.items.forEach { item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.Medium)
                        Text(item.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (item.ml > 0) "+${formatMl(item.ml)} $ml" else "0 $ml",
                        fontWeight = FontWeight.Bold,
                        color = if (item.ml > 0) HydroBlue else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
