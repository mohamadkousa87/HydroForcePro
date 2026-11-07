package com.hydroforce.pro.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydroforce.pro.data.Meeting
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun MeetingsScreen(
    meetings: List<Meeting>,
    onAdd: (startMillis: Long, endMillis: Long, title: String) -> Unit,
    onRemove: (Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val now = remember { Calendar.getInstance() }

    var title by remember { mutableStateOf("") }
    var year by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(now.get(Calendar.MONTH)) }
    var day by remember { mutableIntStateOf(now.get(Calendar.DAY_OF_MONTH)) }
    val roundedStart = ((now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)) / 5) * 5
    var startMin by remember { mutableIntStateOf(roundedStart) }
    var endMin by remember { mutableIntStateOf((roundedStart + 60).coerceAtMost(23 * 60 + 59)) }

    fun millisFor(minuteOfDay: Int): Long {
        val c = Calendar.getInstance()
        c.set(year, month, day, minuteOfDay / 60, minuteOfDay % 60, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    val startMillis = millisFor(startMin)
    val endMillis = millisFor(endMin)
    val valid = endMillis > startMillis && endMillis > System.currentTimeMillis()

    val dateFmt = remember { SimpleDateFormat("EEEE d MMMM yyyy", Locale("ar")) }
    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale("ar")) }

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
            TextButton(onClick = onBack) { Text("→ رجوع") }
            Text("📅 جدولة الاجتماعات", style = MaterialTheme.typography.headlineMedium, color = HydroDeep)
        }
        Text(
            "أضف أوقات اجتماعاتك فتُؤجَّل التنبيهات خلالها وتُستأنف بعد انتهائها مباشرةً.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("اجتماع جديد", style = MaterialTheme.typography.titleMedium, color = HydroDeep)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(40) },
                    label = { Text("العنوان (اختياري)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                PickerRow("📆 التاريخ", dateFmt.format(Date(millisFor(startMin)))) {
                    DatePickerDialog(
                        context,
                        { _, y, m, d -> year = y; month = m; day = d },
                        year, month, day
                    ).show()
                }
                PickerRow("⏰ من الساعة", timeFmt.format(Date(millisFor(startMin)))) {
                    TimePickerDialog(context, { _, h, m ->
                        startMin = h * 60 + m
                        if (endMin <= startMin) endMin = (startMin + 30).coerceAtMost(23 * 60 + 59)
                    }, startMin / 60, startMin % 60, false).show()
                }
                PickerRow("⏱️ إلى الساعة", timeFmt.format(Date(millisFor(endMin)))) {
                    TimePickerDialog(context, { _, h, m -> endMin = h * 60 + m }, endMin / 60, endMin % 60, false).show()
                }
                if (!valid) {
                    Text(
                        "تأكد أن وقت الانتهاء بعد البداية وفي المستقبل.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
                Button(
                    onClick = {
                        onAdd(startMillis, endMillis, title.trim())
                        title = ""
                    },
                    enabled = valid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("➕ إضافة الاجتماع")
                }
            }
        }

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("الاجتماعات القادمة", style = MaterialTheme.typography.titleMedium, color = HydroDeep)
                Spacer(Modifier.height(6.dp))
                if (meetings.isEmpty()) {
                    Text("لا توجد اجتماعات مجدولة.", color = Color.Gray)
                } else {
                    meetings.forEachIndexed { i, m ->
                        if (i > 0) HorizontalDivider(color = Color(0xFFEFF3F7))
                        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    m.title.ifBlank { "اجتماع" },
                                    fontWeight = FontWeight.SemiBold,
                                    color = HydroDeep
                                )
                                Text(dateFmt.format(Date(m.startMillis)), fontSize = 13.sp, color = Color.Gray)
                                Text(
                                    "${timeFmt.format(Date(m.startMillis))} — ${timeFmt.format(Date(m.endMillis))}",
                                    fontSize = 13.sp,
                                    color = HydroBlue,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            TextButton(onClick = { onRemove(m.id) }) {
                                Text("حذف", color = HydroDanger)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PickerRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(
            onClick = onClick,
            border = BorderStroke(1.dp, HydroBlue),
            shape = RoundedCornerShape(12.dp)
        ) { Text(value) }
    }
}
