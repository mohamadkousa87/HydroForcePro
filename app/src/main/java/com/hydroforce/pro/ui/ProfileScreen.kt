package com.hydroforce.pro.ui

import android.app.Activity
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
import com.hydroforce.pro.data.VolumeUnit
import com.hydroforce.pro.data.formatDuration
import com.hydroforce.pro.data.formatVolume
import com.hydroforce.pro.update.UpdateSection
import java.util.Locale

/** The "Profile" tab (and first-run onboarding): personal data, container, units, tone, appearance. */
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
    var volumeUnit by rememberSaveable { mutableStateOf(initial.volumeUnit) }
    var ringtoneUri by rememberSaveable { mutableStateOf(initial.ringtoneUri) }
    var vibration by rememberSaveable { mutableStateOf(initial.vibrationEnabled) }
    var dailyTipsEnabled by rememberSaveable { mutableStateOf(initial.dailyTipsEnabled) }

    val age = ageText.toIntOrNull()
    val weight = weightText.replace(',', '.').toFloatOrNull()
    val height = heightText.toIntOrNull()
    val ageError = age == null || age !in 5..120
    val weightError = weight == null || weight !in 20f..300f
    val heightError = height == null || height !in 80..250
    val valid = !ageError && !weightError && !heightError

    // Keep all schedule/fasting/meal fields from [initial]; this screen only edits personal settings.
    val draft = initial.copy(
        gender = gender,
        age = age ?: initial.age,
        weightKg = weight ?: initial.weightKg,
        heightCm = height ?: initial.heightCm,
        activity = activity,
        cupSizeMl = cup,
        volumeUnit = volumeUnit,
        ringtoneUri = ringtoneUri,
        vibrationEnabled = vibration,
        dailyTipsEnabled = dailyTipsEnabled
    )
    val lang = LocalLanguage.current
    val result = HydrationCalculator.calculate(draft, lang)

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
                if (isOnboarding) tr("مرحباً بك في VeroSip 💧", "Welcome to VeroSip 💧") else tr("👤 ملفي", "👤 Profile"),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (isOnboarding) {
            Text(
                tr(
                    "أدخل بياناتك لحساب احتياجك الطبي اليومي من الماء. يمكنك ضبط الجدول والصيام لاحقاً من تبويب «جدولي».",
                    "Enter your details to compute your daily medical water need. You can set your schedule & fasting later in the Schedule tab."
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
                    Text("+${formatVolume(level.bonusMl, volumeUnit, lang)}", color = HydroBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Section(tr("🥤 وحدة القياس وحجم الكأس", "🥤 Unit & cup size")) {
            Text(tr("وحدة العرض", "Display unit"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = volumeUnit == VolumeUnit.ML, onClick = { volumeUnit = VolumeUnit.ML }, label = { Text(tr("مل", "ml")) })
                FilterChip(selected = volumeUnit == VolumeUnit.FL_OZ, onClick = { volumeUnit = VolumeUnit.FL_OZ }, label = { Text(tr("أونصة", "fl oz")) })
            }
            Text(tr("حجم الكأس المعتاد", "Usual cup size"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CUP_SIZES.forEach {
                    FilterChip(selected = cup == it, onClick = { cup = it }, label = { Text(formatVolume(it, volumeUnit, lang)) })
                }
            }
        }

        AlarmSoundSection(
            ringtoneUri = ringtoneUri,
            vibration = vibration,
            onRingtoneChange = { ringtoneUri = it },
            onVibrationChange = { vibration = it }
        )

        Section(tr("💡 إشعار المعلومة الطبية اليومية", "💡 Daily medical tip")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("نصيحة ترطيب يومية", "A daily hydration tip"), fontWeight = FontWeight.SemiBold)
                    Text(
                        tr("إشعار هادئ مرة واحدة بعد ساعة من الاستيقاظ", "One quiet notification an hour after wake-up"),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = dailyTipsEnabled, onCheckedChange = { dailyTipsEnabled = it })
            }
        }

        ResultCard(result, cup, volumeUnit, title = tr("📋 هدفك الطبي اليومي", "📋 Your daily medical goal"))

        if (!isOnboarding) {
            ThemeModeSection()
            UpdateSection(com.hydroforce.pro.update.rememberUpdateController())
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
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onRingtoneChange(uri.toString())
        }
    }

    val pickerTitle = tr("اختر نغمة التنبيه", "Choose alarm tone")

    Section(tr("🔔 نغمة التنبيه والاهتزاز", "🔔 Alarm tone & vibration")) {
        Text(tr("النغمة الحالية:", "Current tone:"), style = MaterialTheme.typography.labelLarge)
        Text(ringtoneTitle, fontWeight = FontWeight.SemiBold, color = HydroBlue)
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
fun ResultCard(result: HydrationResult, cup: Int, unit: VolumeUnit, title: String = "📋 هدفك الطبي اليومي") {
    val lang = LocalLanguage.current
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                tr("${formatVolume(result.targetMl, unit, lang)} يومياً", "${formatVolume(result.targetMl, unit, lang)} per day"),
                style = MaterialTheme.typography.headlineMedium,
                color = HydroBlue
            )
            Text(
                tr(
                    "= ${result.cupsNeeded} كأس × ${formatVolume(cup, unit, lang)} • تنبيه كل ${formatDuration(result.intervalMinutes, lang)}",
                    "= ${result.cupsNeeded} cups × ${formatVolume(cup, unit, lang)} • reminder every ${formatDuration(result.intervalMinutes, lang)}"
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
                        if (item.ml > 0) "+${formatVolume(item.ml, unit, lang)}" else formatVolume(0, unit, lang),
                        fontWeight = FontWeight.Bold,
                        color = if (item.ml > 0) HydroBlue else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
