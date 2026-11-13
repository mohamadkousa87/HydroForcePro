package com.hydroforce.pro.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Exports the intake log to a CSV file and shares it. */
object CsvExporter {

    suspend fun write(context: Context, logs: List<IntakeLog>): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "hydroforce_log.csv")
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        buildString {
            append("date_time,drink_type,amount_ml,effective_ml,source\n")
            logs.forEach { log ->
                append(fmt.format(Date(log.timestamp)))
                append(',')
                append(DrinkType.fromName(log.drinkType).label)
                append(',')
                append(log.amountMl)
                append(',')
                append(log.effectiveMl)
                append(',')
                append(log.source)
                append('\n')
            }
        }.let { file.writeText(it) }
        file
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "VeroSip — سجل الترطيب")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "تصدير السجل").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
