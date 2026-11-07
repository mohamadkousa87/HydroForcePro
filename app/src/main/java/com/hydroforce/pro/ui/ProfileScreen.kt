package com.hydroforce.pro.ui

import android.app.TimePickerDialog
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
        workEndMinutes = workEnd
    )
    val result = HydrationCalculator.calculate(draft)
    val lang = LocalLanguage.current
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
                if (isOnboarding) tr("مرحباً بك في هايدرو فورس 💧", "Welcome to HydroForce 💧") else tr("الملف الطبي والجدول", "Medical profile & schedule"),
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
                        "ساعات اليقظة: ${formatDuration(result.awakeMinutes)} — لا تنبيهات إطلاقاً أثناء النوم 😴",
                        "Waking hours: ${formatDuration(result.awakeMinutes)} — no reminders at all during sleep 😴"
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
            Text(if (isOnboarding) tr("ابدأ الالتزام بالترطيب 🚀", "Start staying hydrated 🚀") else tr("حفظ وإعادة جدولة التنبيهات ✅", "Save & reschedule reminders ✅"))
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
        ) { Text(formatClock(minutes)) }
    }
}

@Composable
fun ResultCard(result: HydrationResult, cup: Int, title: String = "📋 هدفك الطبي اليومي") {
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
                    "= ${result.cupsNeeded} كأس × $cup مل • تنبيه كل ${formatDuration(result.intervalMinutes)}",
                    "= ${result.cupsNeeded} cups × $cup ml • reminder every ${formatDuration(result.intervalMinutes)}"
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
