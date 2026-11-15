package com.hydroforce.pro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val CloudGreen = Color(0xFF00E676)
private const val API_KEY_URL = "https://aistudio.google.com/apikey"

/**
 * BYOK Cloud AI config dialog: toggle, key field with paste, and a beginner "learn how" guide.
 * Shared by ProfileScreen and the camera's ☁️ button.
 */
@Composable
fun CloudAiDialog(
    enabled: Boolean,
    apiKey: String,
    onEnabledChange: (Boolean) -> Unit,
    onKeyChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var keyText by remember { mutableStateOf(apiKey) }
    var showLearn by remember { mutableStateOf(false) }

    if (showLearn) {
        LearnHowDialog(onDismiss = { showLearn = false })
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onKeyChange(keyText.trim()); onDismiss() }) {
                Text(tr("حفظ", "Save"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("إغلاق", "Close")) } },
        title = { Text(tr("الفحص السحابي الذكي 100% (Cloud AI) 🌐", "Smart Cloud AI check 100% 🌐")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(tr("تفعيل الفحص السحابي", "Enable cloud check"), modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = onEnabledChange)
                }
                Text(
                    tr(
                        "اختياري تماماً. عند تعذّره يعمل التطبيق بالوضع المحلي تلقائياً.",
                        "Fully optional. Falls back to local mode automatically if unavailable."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    singleLine = true,
                    label = { Text(tr("مفتاح Gemini API", "Gemini API key")) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { clipboard.getText()?.text?.let { keyText = it.trim() } }) {
                        Text(tr("لصق من الحافظة 📋", "Paste 📋"))
                    }
                    Button(onClick = { showLearn = true }) {
                        Text(tr("تعلم كيف تحصل على مفتاحك المجاني 💡", "Get a free key 💡"))
                    }
                }
            }
        }
    )
}

@Composable
private fun LearnHowDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { runCatching { uriHandler.openUri(API_KEY_URL) } }) {
                Text(tr("فتح موقع المفتاح المجاني الآن 🔗", "Open the free key site now 🔗"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("رجوع", "Back")) } },
        title = { Text(tr("كيف تحصل على مفتاحك المجاني 💡", "How to get your free key 💡")) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    tr(
                        "✅ مجاني 100% مدى الحياة — لا يتطلب بطاقة بنكية، ولا يترتب عليه أي اشتراكات أو رسوم نهائياً (يمنحك 1,500 فحص ذكي مجاني يومياً من قوقل).",
                        "✅ 100% Free Forever — No credit card required, zero subscriptions or hidden fees (gives you 1,500 free smart checks daily from Google)."
                    ),
                    color = Color.Black,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(CloudGreen)
                        .padding(10.dp)
                )
                Text(tr("الخطوات:", "Steps:"), fontWeight = FontWeight.Bold)
                Text(tr("1) اضغط الزر بالأسفل وسجّل الدخول بحساب Gmail العادي.", "1) Tap the button below and sign in with your normal Gmail."))
                Text(tr("2) اضغط \"Get API key\" ثم \"Create API key\".", "2) Tap \"Get API key\", then \"Create API key\"."))
                Text(tr("3) اضغط أيقونة النسخ (Copy) لنسخ الكود.", "3) Tap the Copy icon to copy the code."))
                Text(tr("4) ارجع هنا واضغط \"لصق من الحافظة 📋\" ثم احفظ.", "4) Come back here, tap \"Paste 📋\", then Save."))
            }
        }
    )
}
