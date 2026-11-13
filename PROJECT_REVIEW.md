# مراجعة تقنية شاملة — VeroSip

> تطبيق أندرويد لتذكير شرب الماء، فلسفته أن **التنبيه لا يتوقف إلا بعد إثبات الشرب عبر الكاميرا + الذكاء الاصطناعي**.
> اسم العلامة المعروض: **VeroSip** (يبقى بالحروف اللاتينية دائماً، حتى في الواجهة العربية). الحزمة (ثابتة): `com.hydroforce.pro` — الإصدار الحالي: **1.21.0** (`VERSION_CODE = 23`).
> `minSdk = 26` ، `targetSdk = 34` ، `compileSdk = 34` ، لغة Kotlin + Jetpack Compose (Material 3) ، الواجهة ثنائية اللغة (عربي/إنجليزي) وتدعم RTL والوضع الليلي (هوية Bio-Tech Aqua).

---

## 1. شجرة ملفات المشروع (Project Directory Tree)

```
app/
├─ build.gradle.kts                         # إعداد الوحدة، الإصدار، التوقيع، المكتبات
├─ src/main/
│  ├─ AndroidManifest.xml                   # الصلاحيات، الخدمة، المستقبلات، الويدجت
│  ├─ java/com/hydroforce/pro/
│  │  ├─ HydroApp.kt                        # Application: إنشاء قنوات الإشعارات
│  │  ├─ MainActivity.kt                    # نقطة الدخول + NavHost (التنقل)
│  │  ├─ alarm/
│  │  │  ├─ AlarmScheduler.kt               # جدولة التنبيهات عبر AlarmManager (exact)
│  │  │  ├─ AlarmService.kt                 # ForegroundService: الرنين والإشعارات
│  │  │  ├─ AlarmVerifyActivity.kt          # شاشة التنبيه فوق القفل (ملء الشاشة)
│  │  │  ├─ HydrationManager.kt             # منطق الجدولة وتسجيل الشرب (نقطة مركزية)
│  │  │  └─ Receivers.kt                    # AlarmReceiver + BootReceiver
│  │  ├─ camera/
│  │  │  └─ CameraVerificationScreen.kt     # CameraX + ML Kit للتحقق من الكأس
│  │  ├─ data/
│  │  │  ├─ UserProfile.kt                  # نموذج الملف الطبي + الثوابت + enums
│  │  │  ├─ HydrationCalculator.kt          # معادلة حساب الماء + التنسيقات
│  │  │  ├─ ScheduleMath.kt                 # حسابات الوقت (يقظة/نوم/وجبات)
│  │  │  ├─ SettingsRepository.kt           # DataStore (الإعدادات والحالة)
│  │  │  ├─ IntakeLog.kt                    # Room: جدول سجل الشرب + DAO + DB
│  │  │  ├─ DrinkType.kt                    # أنواع المشروبات ومعامل الترطيب
│  │  │  ├─ Meeting.kt                      # نموذج/ترميز الاجتماعات
│  │  │  ├─ StatsCalculator.kt              # حساب الإحصائيات
│  │  │  ├─ CsvExporter.kt                  # تصدير السجل CSV
│  │  │  ├─ HydrationTips.kt                # محتوى النصائح (محلي، ثنائي اللغة)
│  │  │  ├─ Language.kt                     # enum اللغة
│  │  │  └─ ThemeMode.kt                    # enum وضع السمة (نظام/فاتح/داكن)
│  │  ├─ ui/
│  │  │  ├─ MainViewModel.kt                # AndroidViewModel: الحالة والتدفقات
│  │  │  ├─ DashboardScreen.kt              # لوحة التحكم الرئيسية
│  │  │  ├─ ProfileScreen.kt                # الإعداد الأولي + شاشة الإعدادات
│  │  │  ├─ HydrationTipsScreen.kt          # تبويب النصائح الصحية
│  │  │  ├─ StatsScreen.kt                  # الإحصائيات والرسوم
│  │  │  ├─ MeetingsScreen.kt               # إدارة الاجتماعات
│  │  │  ├─ DrinkTypePicker.kt              # اختيار نوع المشروب
│  │  │  ├─ ThemeModeSection.kt             # قسم اختيار السمة
│  │  │  ├─ Permissions.kt                  # فحص/طلب الصلاحيات
│  │  │  ├─ Theme.kt                        # الألوان والثيم (فاتح/داكن)
│  │  │  └─ Localization.kt                 # LocalLanguage + دالة tr()
│  │  ├─ update/
│  │  │  ├─ UpdateChecker.kt                # التحديث الذاتي من GitHub Releases
│  │  │  └─ UpdateUi.kt                     # واجهات/حوار التحديث
│  │  └─ widget/
│  │     └─ HydroWidgets.kt                 # 3 ويدجت (Glance)
│  └─ res/
│     ├─ drawable/ic_launcher_foreground.xml, ic_stat_drop.xml
│     ├─ mipmap-anydpi-v26/ic_launcher.xml
│     ├─ values/ (colors.xml, strings.xml, themes.xml)
│     ├─ values-en/strings.xml             # نصوص الويدجت بالإنجليزية
│     └─ xml/ (file_paths.xml, locales_config.xml,
│              widget_progress_info.xml, widget_next_info.xml, widget_button_info.xml)
```

---

## 2. المكتبات والتقنيات المستخدمة (Dependencies & Versions)

من `app/build.gradle.kts`:

| المكتبة | الإصدار | الاستخدام |
|---|---|---|
| Jetpack Compose BOM | `2024.06.00` | واجهة المستخدم |
| Compose UI / Material3 / Animation / Icons | عبر BOM | المكونات والثيم |
| `androidx.core:core-ktx` | `1.13.1` | أدوات أساسية |
| `androidx.activity:activity-compose` | `1.9.0` | ربط Activity بـ Compose |
| `androidx.lifecycle:*` (runtime, viewmodel, runtime-compose, service) | `2.8.3` | دورة الحياة وViewModel وLifecycleService |
| `androidx.navigation:navigation-compose` | `2.7.7` | التنقل بين الشاشات |
| `androidx.camera:*` (core, camera2, lifecycle, view) | `1.3.4` | معاينة والتقاط الكاميرا |
| `com.google.mlkit:image-labeling` | `17.0.8` | التعرف على الصورة على الجهاز |
| `androidx.datastore:datastore-preferences` | `1.1.1` | تخزين الإعدادات والحالة |
| `androidx.room:*` (runtime, ktx, compiler/KSP) | `2.6.1` | قاعدة بيانات سجل الشرب |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | `1.8.1` | التزامن |
| `androidx.glance:glance-appwidget` | `1.1.0` | ويدجت الشاشة الرئيسية |

- Compose Compiler: `1.5.14` ، Java/Kotlin JVM target: `17`.
- `buildConfigField("String", "UPDATE_REPO", "mohamadkousa87/HydroForcePro")` — يُستخدم للتحديث الذاتي.
- التوقيع: يُفعّل تلقائياً عند وجود `keystore.properties` (يوفّره CI عبر GitHub Actions). الإصدارات الفعلية تُبنى وتُوقّع وتُنشر عبر CI، والمفتاح مخزّن في أسرار GitHub.

---

## 3. تفصيل المنطق البرمجي الفعلي (Implementation Details)

### 3.1 معادلة حساب الماء الطبية

الملف: `data/HydrationCalculator.kt` — الدالة `calculate(p: UserProfile, lang)`.
الهدف = مجموع عناصر (`BreakdownItem`): أساس حسب الوزن والعمر + معامل الجنس + معامل الطول + إضافة النشاط.

```kotlin
// المعامل حسب العمر (مل لكل كغ)
fun mlPerKg(age: Int): Int = when {
    age > 65 -> 23
    age > 55 -> 25
    else     -> 27
}

// داخل calculate():
val factor = mlPerKg(p.age)
val base   = (p.weightKg * factor).roundToInt()          // الأساس حسب الوزن
// + معامل الجنس: الذكر +250 مل ، الأنثى 0
// + معامل الطول: إن كان > 175 سم ← +100 مل ، وإلا 0
// + إضافة النشاط (ActivityLevel.bonusMl):
//     SEDENTARY=0 , LIGHT=150 , MODERATE=300 , HIGH=500
val target = items.sumOf { it.ml }

val cups     = ceil(target.toDouble() / p.cupSizeMl).toInt().coerceAtLeast(1)
val awake    = awakeMinutes(p.wakeMinutes, p.sleepMinutes)
val interval = (awake / cups).coerceAtLeast(MIN_INTERVAL_MINUTES) // الحد الأدنى 120 دقيقة
```

- النتيجة تُغلّف في `HydrationResult(targetMl, cupsNeeded, intervalMinutes, awakeMinutes, items)`.
- مثال (وزن 107 كغ، ذكر، عمر بالغ): الأساس 107×27 = 2889 + 250 (جنس) + (0/100 طول) = ~3.1–3.2 لتر عادي، و~3.7 لتر عند النشاط العالي.
- `MIN_INTERVAL_MINUTES = 60` يمنع التنبيهات الأقرب من ساعة. حجم الكوب الافتراضي 400 مل، والخيارات: `[200, 250, 330, 400, 500]`.
- **رصيد الوقت المائي (Time Credit):** عند التوثيق تُحسب `earnedMinutes = (baseInterval × amount/cupSize).roundToInt().coerceAtLeast(30)`. إن كان ردّاً على منبه يرن/مستحق ⇒ التنبيه القادم بعد `now + earnedMinutes`. وإن كان توثيقاً استباقياً قبل موعد المنبه ⇒ يُمدَّد الموعد (ولا يُقصَّر) بسقف فاصل كامل من الآن.

### 3.2 نظام الجدولة والمنبهات (AlarmManager / ForegroundService)

**الجدولة** — `alarm/AlarmScheduler.kt`:
- `schedule(context, triggerAt)` يستخدم `setExactAndAllowWhileIdle(RTC_WAKEUP, …)` عند توفر صلاحية التنبيه الدقيق (`canScheduleExactAlarms()` على Android 12+)، وإلا يسقط إلى `setAndAllowWhileIdle` (وكذلك عند `SecurityException`).
- يستهدف `AlarmReceiver` عبر `PendingIntent` (FLAG_IMMUTABLE).

**الإطلاق** — `alarm/Receivers.kt`:
- `AlarmReceiver.onReceive` يبدأ `AlarmService` كخدمة أمامية (`startForegroundService`) بإجراء `ACTION_START`.
- `BootReceiver` يستجيب لـ `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` / `TIME_SET` / `TIMEZONE_CHANGED` / تغيّر صلاحية التنبيه الدقيق، وينادي `HydrationManager.restore()` لإعادة تسليح التنبيه.

**الخدمة الأمامية** — `alarm/AlarmService.kt` (`LifecycleService`, نوع `specialUse`):
- عند `startRinging()`: يرفع إشعاراً أمامياً فوراً، ثم يحمّل الملف واللغة.
- **إيقاف ذكي قبل الرنين:** إن كان المستخدم في مكالمة (`AudioManager.mode`) يؤجّل (`schedulePostpone`) ويظهر إشعاراً؛ وإن كان ضمن اجتماع مجدول يؤجّل حتى نهايته (`schedulePostponeUntil`).
- **وضعان:** `isWorkTime()` ⇒ وضع هادئ (`playDiscreet`: نغمة واحدة + اهتزاز خفيف)، وإلا الوضع الكامل (`playEnforcer`: نغمة متكررة + اهتزاز قوي). النغمة قابلة للاختيار (`profile.ringtoneUri`) والاهتزاز قابل للإيقاف (`profile.vibrationEnabled`).
- **الإشعارات:** إشعار دائم بملء الشاشة (`CHANNEL_ALARM`) مع `setFullScreenIntent` + زرّا «توثيق الآن» و«تأجيل»؛ وإشعار إضافي قابل للإزالة (`CHANNEL_WATCH`) يُنشر مع الرنين لينعكس على الساعة الذكية (لأن الساعات لا تعكس الإشعارات الأمامية الدائمة).
- **مهلة الرنين:** `RING_TIMEOUT_MS = 10 دقائق` ⇒ عند انتهائها يسجّل «تنبيه فائت» ويعيد الجدولة (`scheduleRetry`, بعد `RETRY_MINUTES = 10`).
- يمسك `WAKE_LOCK` أثناء الرنين، ويحرره عند التوقف.

**المنطق المركزي** — `alarm/HydrationManager.kt`:
- `rescheduleFrom(anchor)`: يحسب الهدف والمستهلك اليوم (`totalSince(calendarDayStart)`)، ثم يختار التنبيه التالي: إن اكتمل الهدف ⇒ بداية نافذة اليقظة التالية، وإلا `nextReminder(anchor, interval)`.
- `arm(...)`: نقطة التجميع التي تطبّق `adjustForMeetings` ثم تجدول وتخزّن الموعد **وتحدّث الويدجت**.
- `adjustForMeetings(...)`: حلقة تدفع الوقت خارج: (1) أوقات النوم (`nextWindowStart`)، (2) نوافذ الوجبات (`ScheduleMath.mealPauseEnd`)، (3) الاجتماعات (`meetings.covering`).
- `recordVerifiedDrink(amountMl, source, drinkType)`: يُستدعى **فقط بعد** تأكيد الكاميرا — يستقبل **الكمية الفعلية** التي اختارها المستخدم، يُدرج `IntakeLog` بها و`effectiveMl` حسب نوع المشروب، يصفّر الغفوات، ثم يطبّق **معادلة رصيد الوقت المائي** لجدولة التنبيه القادم (مع احترام اكتمال الهدف → نافذة اليقظة التالية).
- `rescheduleKeepingCountdown()`: يُستدعى عند حفظ الإعدادات — **يحافظ على العد التنازلي القائم** إن كان هناك تنبيه قادم صالح ضمن اليقظة، وإلا يعيد الحساب (إصلاح خلل تصفير العداد).
- `snooze(minutes)` (حد أقصى `MAX_SNOOZES = 3`)، `schedulePostpone` (مكالمة، `CALL_POSTPONE_MINUTES = 5`)، `restore()` (بعد الإقلاع).

**حسابات الوقت** — `data/ScheduleMath.kt`:
- نافذة اليقظة من وقت الاستيقاظ حتى النوم (قد يتجاوز منتصف الليل)، **لا يُوضع أي تنبيه خارجها**.
- `currentHydrationCycleStart(now, wakeMinutes)` (بداية دورة اليقظة — التقدم اليومي يُحسب منها لا من منتصف الليل، فلا يتصفّر لمن يسهر بعد 12)، `calendarDayStart` (يُستخدم الآن في الإحصائيات فقط)، `isWorkTime`, `mealPauseEnd` (نافذة 30 دق قبل الوجبة و45 دق بعدها).

### 3.3 نظام التحقق بالكاميرا (CameraX + ML Kit)

الملف: `camera/CameraVerificationScreen.kt`.
- معاينة حية عبر `CameraX` (`Preview` + `ImageCapture` بوضع `CAPTURE_MODE_MINIMIZE_LATENCY`, الكاميرا الخلفية `DEFAULT_BACK_CAMERA`).
- عند الالتقاط: تُحوّل الصورة إلى `Bitmap` في الذاكرة فقط، وتُحلّل عبر `ImageLabeling` الافتراضي من ML Kit، ثم **تُتلف فوراً** (`bitmap.recycle()`) — لا حفظ على التخزين ولا منتقي معرض/ملفات (قرار تصميمي للخصوصية).
- **نسبة الثقة:**
```kotlin
const val CONFIDENCE_THRESHOLD = 0.65f
ImageLabeling.getClient(
    ImageLabelerOptions.Builder().setConfidenceThreshold(CONFIDENCE_THRESHOLD).build()
)
```
- **الكلمات المفتاحية المقبولة** (يجب أن يطابقها تصنيف واحد على الأقل بثقة ≥ 0.65 — حُذفت `tableware` لمنع قبول الصحون الفارغة):
```kotlin
val ACCEPTED_LABELS = setOf(
    "water", "bottle", "water bottle", "plastic bottle", "glass", "cup", "drink",
    "liquid", "beverage", "drinkware", "mug", "pitcher", "tumbler"
)
```
- **إضافات Sprint 1:** شريط اختيار كمية سريع فوق زر الالتقاط `[100, 200, 300, 400, 500]` مل (الافتراضي = كوب المستخدم)، وتُمرَّر الكمية المختارة عبر `onVerified(selectedAmountMl)`؛ زر تشغيل/إيقاف الفلاش (`camera.cameraControl.enableTorch`)؛ وإطار تركيز بزوايا شعار VeroSip باللون السيان `#00E5FF` يتحول أخضر `#00E676` عند النجاح.
- منطق القرار:
```kotlin
val strong = labels.filter { it.confidence >= CONFIDENCE_THRESHOLD }
val match  = strong.firstOrNull { it.text.lowercase() in ACCEPTED_LABELS }
state = if (match != null) Verified(match.text) else Rejected(strong.take(4).map { it.text })
```
- عند النجاح: نغمة تأكيد (`ToneGenerator`) ثم استدعاء `onVerified()` (الذي يسجّل الشرب ويعيد ضبط المؤقت).

---

### 3.4 محرك الأنماط الحيوية (Sprint 2)

- **قاعدة البيانات (v3):** جدول `bio_check_logs` (`BioCheckLog`: timestamp، `urineColorGrade` 1–8، `addedBonusMl`) مع `MIGRATION_2_3` صريح (`CREATE TABLE IF NOT EXISTS`) يحفظ بيانات الشرب السابقة.
- **عزل مسارات المنبهات** (`AlarmScheduler`): ثلاثة مسارات مستقلة بمعرّفات وأفعال منفصلة لمنع التصادم:
  `REQ_MAIN_ALARM=100` (الرنين الكامل)، `REQ_WORKOUT_SIP=200` (نبضة رشفة في المستقبل مباشرة)، `REQ_DAILY_TIP=300` (معلومة اليوم). التوجيه في `AlarmReceiver`.
- **محرك الصيام** (`FastingMode`, `FastingRecurrence` في `UserProfile`؛ منطق في `ScheduleMath` و`HydrationManager`):
  - صيام جاف: تجميد النهار ودفع أي تنبيه إلى الإفطار (`fastEndFor`)، فاصل ليلي 45د (`effectiveMinInterval`)، تنبيه سحور قبل الإمساك بـ30د، وانتهاء تلقائي لوضع «اليوم فقط». حلقة `adjustForMeetings` محميّة بحد `20` تكرار.
  - صيام متقطع: +10% للهدف، تجاهل توقف الوجبات ضمن نافذة الصيام، وشارة «يكسر الصيام ⚠️» على المشروبات ذات السعرات (`DrinkType.breaksFast`).
- **وضع التمرين** (`HydrationManager.startWorkout/endWorkout/onWorkoutSipAlarm`): توثيق مطارة بالكاميرا مرة واحدة، +500 مل تعويض تعرق للدورة، نبضات رشفة كل 15د تُسجّل 120 مل تلقائياً وتُرسل لقناة الساعة، تأجيل المنبه الرئيسي، وزر إنهاء مبكر. أثناء الصيام الجاف: +500 مل فقط بدون كاميرا/نبضات.
- **المؤشر الحيوي** (`HydrationManager.applyBioCheck`): الدرجات 1–8 تضيف ماء إنقاذ بسقف 500 مل/دورة (3–4: +150، 5–6: +350 مع تقريب المنبه لـ30د، 7–8: +500 وفتح الكاميرا)، تحذير «الطرد السريع» للدرجة 1 بعد شرب كبير، عدّاد مرات الحمام وزر تراجع.
- **المعلومة اليومية** (`CHANNEL_DAILY_TIPS`): إشعار هادئ مجدول بعد 60د من الاستيقاظ (`scheduleNextDailyTip`)، يُعاد جدولته عند الإقلاع وتغيير الإعدادات، ومفتاح `dailyTipsEnabled`.
- **الهدف الفعّال** = الأساس الطبي + مكافأة البول (من `bio_check_logs`) + تعويض التمرين (مخزّن لكل دورة) — يُحتسب في جدولة `HydrationManager` وعرض `MainViewModel`.

### 3.5 الهيكلة والوحدات (Sprint 3)

- **وحدة القياس** `VolumeUnit` (ML / FL_OZ) في `UserProfile` (عرض فقط؛ التخزين والحساب بالمليلتر). دالة مركزية `formatVolume(ml, unit, lang)` في `HydrationCalculator` مستخدمة في اللوحة والكاميرا والإحصائيات والويدجت.
- **تنقّل بأربع تبويبات** عبر `Scaffold` + `NavigationBar` (خلفية `#0A1118`، مؤشر `#00E5FF`): `dashboard` (اليوم)، `schedule` (جدولي)، `insights` (المؤشرات)، `profile` (ملفي). يُخفى الشريط في `onboarding` و`camera`/`workout_camera`.
- **توزيع المحتوى:** Dashboard (هيدر VeroSip + شارة الوضع، حلقة تقدم + عد تنازلي + زر ℹ️ يفتح حوار تفصيل الهدف، منتقي المشروب + زر التوثيق، بطاقة الفحص الحيوي، سجل اليوم، بطاقة التمرين). `ScheduleScreen` (الصيام + اليقظة/النوم/العمل + الوجبات + الاجتماعات). `InsightsScreen` (تبويبان: الإحصائيات | النصائح). `ProfileScreen` (البيانات الحيوية + الوحدة/الكوب + النغمة/الاهتزاز + المعلومة اليومية + المظهر/اللغة + التحديث).

## 4. واجهات المستخدم (Screens & UI Flows)

التنقل عبر `NavHost` في `MainActivity.kt` (المسارات: onboarding / dashboard / settings / camera / meetings / stats / tips):

| الشاشة | الملف | المحتوى |
|---|---|---|
| **الإعداد الأولي** (Onboarding) | `ProfileScreen` (isOnboarding=true) | إدخال البيانات الحيوية/الجدول لأول مرة ثم الانتقال للوحة |
| **لوحة التحكم** (Dashboard) | `DashboardScreen` | بطاقة الصلاحيات، دائرة التقدم اليومي، العد التنازلي ووضع التنبيه، اختيار نوع المشروب، زر «شربت الآن 📷»، أزرار الإحصائيات/الاجتماعات/النصائح، دليل ألوان البول، سجل اليوم، تفصيل الهدف |
| **الكاميرا** (Camera) | `CameraVerificationScreen` | معاينة حية + التقاط + تحقق ML Kit + تأكيد |
| **الإعدادات / الملف الطبي** | `ProfileScreen` (isOnboarding=false) | البيانات الحيوية، النشاط، حجم الكوب، الجدول اليومي، وضع العمل، **النغمة والاهتزاز**، **التوقف حول الوجبات**، السمة، التحديث، حفظ |
| **الإحصائيات** (Stats) | `StatsScreen` | ملخص/رسوم تاريخية + تصدير CSV |
| **الاجتماعات** (Meetings) | `MeetingsScreen` | إضافة/حذف اجتماعات لتأجيل التنبيهات |
| **النصائح الصحية** (Tips) | `HydrationTipsScreen` | نصيحة اليوم + بطاقات نصائح محلية في 6 فئات + فلاتر |

**الويدجت** (`widget/HydroWidgets.kt`، عبر Glance): `TodayProgressWidget` (نسبة اليوم)، `NextReminderWidget` (موعد التنبيه القادم)، `QuickButtonWidget` (فتح سريع) — الثلاثة تفتح التطبيق وتتحدّث مع كل تغيّر في الاستهلاك/الجدولة.

**حوار التحديث الذاتي** (`update/UpdateUi.kt`): يُفحص عند الإقلاع (صامت) وفي الإعدادات، ويعرض زر «البحث عن تحديثات».

---

## 5. الصلاحيات والقيود التقنية (Permissions & Known Limitations / Edge Cases)

**الصلاحيات المعلنة** (`AndroidManifest.xml`):
`CAMERA` ، `INTERNET` ، `ACCESS_NETWORK_STATE` ، `REQUEST_INSTALL_PACKAGES` ، `POST_NOTIFICATIONS` ، `SCHEDULE_EXACT_ALARM` ، `USE_FULL_SCREEN_INTENT` ، `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` ، `FOREGROUND_SERVICE` ، `FOREGROUND_SERVICE_SPECIAL_USE` ، `RECEIVE_BOOT_COMPLETED` ، `VIBRATE` ، `WAKE_LOCK`. و`allowBackup=true`.

**نقاط حسّاسة / حالات حدّية:**
1. **إعادة التشغيل (BOOT_COMPLETED):** `BootReceiver` يعيد التسليح عبر `restore()`؛ لكن على بعض الأجهزة قد يتأخر البث أو يُمنع المستقبل حتى يفتح المستخدم التطبيق مرة واحدة بعد التثبيت.
2. **قتل النظام للخلفية / توفير البطارية:** الشركات المصنّعة (هواوي/شاومي/سامسونج) قد توقف التطبيق أو تؤخر `AlarmManager` ما لم يُستثنَ من تحسين البطارية. هناك طلب `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`، لكن قد يتطلب ضبطاً يدوياً.
3. **التنبيه الدقيق (Exact Alarm):** على Android 12+ يحتاج صلاحية؛ عند غيابها يسقط إلى تنبيه غير دقيق (`setAndAllowWhileIdle`) فتتأخر المواعيد.
4. **بدء نشاط من الخلفية:** `AlarmService` يحاول فتح `AlarmVerifyActivity`؛ قد يُحجب على بعض الإصدارات، ويُعتمد حينها على `setFullScreenIntent`.
5. **عكس الإشعار للساعة:** ساعات هواوي (HarmonyOS) تعكس **نص** الإشعار فقط عبر Huawei Health ولا تدعم **الأزرار التفاعلية** (تأجيل/توثيق) لتطبيقات أندرويد؛ التأجيل من المعصم يتطلب تطبيق ساعة أصلي (خارج النطاق الحالي). كما يتطلب تفعيل عكس الإشعارات يدوياً.
6. **قاعدة البيانات Room:** الإصدار `version = 2` مع `MIGRATION_1_2` (أضاف `drinkType` و`effectiveMl`)؛ أي تغيير مستقبلي في المخطط يتطلب Migration للحفاظ على البيانات.
7. **التقدم اليومي** يُحتسب الآن من `currentHydrationCycleStart` (بداية دورة اليقظة) فلا يتصفّر عند منتصف الليل لمن يسهر؛ أما شاشة الإحصائيات فما زالت تجمّع «اليوم» حسب التقويم (`calendarDayStart`).
8. **الكاميرا:** لا تُحفظ أي صورة (تحليل في الذاكرة فقط)؛ دقة التحقق تعتمد نموذج ML Kit العام وقائمة `ACCEPTED_LABELS` وعتبة 0.65 — قد تُرفض عبوات غير معتادة أو تُقبل أجسام مشابهة.
9. **التحديث الذاتي** يتطلب صلاحية «تثبيت تطبيقات من مصدر غير معروف» مرة واحدة، ويحمّل APK موقّعاً من GitHub Releases ويثبّته فوق الحالي (الحفاظ على البيانات مشروط ببقاء نفس مفتاح التوقيع عبر CI).

---

*أُعدّ هذا التقرير آلياً من قراءة كود المشروع فعلياً، وحُدّث عند الإصدار 1.16.0 (VeroSip — Sprint 1).*
