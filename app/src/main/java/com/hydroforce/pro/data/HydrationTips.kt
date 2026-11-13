package com.hydroforce.pro.data

import java.util.Calendar

/** A single educational card shown in the health tab. Content is bundled (offline), bilingual. */
data class HydrationTip(
    val emoji: String,
    val titleAr: String,
    val titleEn: String,
    val bodyAr: String,
    val bodyEn: String,
    val category: TipCategory
) {
    fun title(lang: Language): String = if (lang == Language.EN) titleEn else titleAr
    fun body(lang: Language): String = if (lang == Language.EN) bodyEn else bodyAr
}

enum class TipCategory(val emoji: String, val labelAr: String, val labelEn: String) {
    BENEFITS("💧", "فوائد الماء", "Benefits"),
    DEHYDRATION("⚠️", "علامات الجفاف", "Dehydration signs"),
    SUMMER("☀️", "الصيف والحرارة", "Heat & summer"),
    SPORT("🏃", "الرياضة والنشاط", "Exercise"),
    FASTING("🌙", "الصيام", "Fasting"),
    HABITS("✅", "عادات ذكية", "Smart habits");

    fun label(lang: Language): String = if (lang == Language.EN) labelEn else labelAr
}

object HydrationTips {

    val all: List<HydrationTip> = listOf(
        HydrationTip("🧠", "الماء يقظة لعقلك", "Water sharpens your mind",
            "فقدان 1–2% فقط من ماء الجسم يقلل التركيز والذاكرة قصيرة المدى. كأس ماء الآن قد يكون أفضل من فنجان قهوة.",
            "Losing just 1–2% of body water hurts focus and short-term memory. A glass of water now may beat a cup of coffee.",
            TipCategory.BENEFITS),
        HydrationTip("❤️", "قلب أكثر راحة", "An easier job for your heart",
            "الترطيب الجيد يحافظ على حجم الدم، فيضخ قلبك بجهد أقل. الجفاف يرفع ضربات القلب ويزيد الإجهاد.",
            "Good hydration keeps blood volume up so your heart pumps with less effort. Dehydration raises heart rate and strain.",
            TipCategory.BENEFITS),
        HydrationTip("⚖️", "يساعد على التحكم بالوزن", "Helps weight control",
            "شرب كوب ماء قبل الوجبة بنصف ساعة يزيد الشبع ويقلل السعرات المتناولة. وأحياناً نخلط العطش بالجوع.",
            "A glass of water 30 min before meals boosts fullness and cuts calories. We often mistake thirst for hunger.",
            TipCategory.BENEFITS),
        HydrationTip("✨", "بشرة أكثر نضارة", "Fresher skin",
            "الترطيب الكافي يحافظ على مرونة الجلد ويقلل جفافه. الماء وقود طبيعي لنضارة البشرة.",
            "Enough water keeps skin elastic and less dry. Hydration is natural fuel for a fresh complexion.",
            TipCategory.BENEFITS),
        HydrationTip("🦾", "مفاصل أكثر ليونة", "Smoother joints",
            "السائل الزلالي حول المفاصل يحتاج ماءً ليعمل كوسادة. الترطيب يقلل الاحتكاك والآلام.",
            "The fluid cushioning your joints needs water to work. Staying hydrated reduces friction and aches.",
            TipCategory.BENEFITS),
        HydrationTip("🫧", "طرد السموم", "Flushing out waste",
            "الكلى تحتاج ماءً كافياً لتصفية الفضلات. الترطيب الجيد يقي من حصوات الكلى والتهابات المسالك.",
            "Kidneys need enough water to filter waste. Good hydration helps prevent kidney stones and urinary infections.",
            TipCategory.BENEFITS),

        HydrationTip("🟡", "راقب لون البول", "Watch your urine color",
            "اللون الفاتح الشفاف = ترطيب جيد. الأصفر الداكن أو الكهرماني = جسمك يطلب الماء فوراً.",
            "Pale, clear = well hydrated. Dark yellow or amber = your body needs water now.",
            TipCategory.DEHYDRATION),
        HydrationTip("😵", "الصداع والدوار", "Headache and dizziness",
            "من أوائل علامات الجفاف صداع خفيف ودوخة. جرّب كأسي ماء قبل مسكّن الألم.",
            "Mild headache and dizziness are early dehydration signs. Try two glasses of water before a painkiller.",
            TipCategory.DEHYDRATION),
        HydrationTip("👄", "جفاف الفم والعطش", "Dry mouth and thirst",
            "الشعور بالعطش يعني أنك بدأت تجف فعلاً. لا تنتظر العطش — اشرب بانتظام.",
            "Feeling thirsty means you're already drying out. Don't wait for thirst — drink regularly.",
            TipCategory.DEHYDRATION),
        HydrationTip("😴", "التعب غير المبرر", "Unexplained fatigue",
            "الخمول وقلة الطاقة قد يكون سببها نقص الماء وليس قلة النوم. اختبرها بكأس ماء.",
            "Low energy and sluggishness can come from lack of water, not just lack of sleep. Test it with a glass of water.",
            TipCategory.DEHYDRATION),
        HydrationTip("💓", "تسارع النبض", "Faster pulse",
            "عند الجفاف ينخفض حجم الدم فيتسارع القلب لتعويض ذلك. الترطيب يعيد النبض لطبيعته.",
            "When dehydrated, blood volume drops and the heart speeds up to compensate. Hydration brings the pulse back to normal.",
            TipCategory.DEHYDRATION),

        HydrationTip("🥵", "الحر يسرّق ماءك", "Heat steals your water",
            "في الأجواء الحارة تفقد سوائل أكثر عبر العرق دون أن تشعر. زد كمية الماء في الصيف.",
            "In hot weather you lose more fluid through sweat without noticing. Drink more in summer.",
            TipCategory.SUMMER),
        HydrationTip("🧊", "ماء بارد باعتدال", "Cool water, in moderation",
            "الماء البارد منعش لكن الماء المعتدل يُمتص أسرع ويُشرب بكميات أكبر دون انزعاج.",
            "Cold water is refreshing, but cool (not icy) water absorbs faster and is easier to drink in larger amounts.",
            TipCategory.SUMMER),
        HydrationTip("🏖️", "قبل الخروج للشمس", "Before heading into the sun",
            "اشرب كأساً قبل التعرض للشمس، وخذ قارورة معك. الوقاية أسهل من علاج ضربة الحر.",
            "Drink a glass before sun exposure and take a bottle with you. Prevention beats treating heat stroke.",
            TipCategory.SUMMER),
        HydrationTip("🍉", "أطعمة مرطبة", "Hydrating foods",
            "البطيخ والخيار والبرتقال تحتوي ماءً عالياً وتدعم ترطيبك في الحر، إضافة إلى شرب الماء.",
            "Watermelon, cucumber and oranges are high in water and support hydration in the heat, alongside drinking water.",
            TipCategory.SUMMER),

        HydrationTip("🏋️", "قبل التمرين", "Before exercise",
            "اشرب 300–500 مل قبل التمرين بـ 30 دقيقة لتبدأ وأنت مرطب جيداً.",
            "Drink 300–500 ml about 30 min before training so you start well hydrated.",
            TipCategory.SPORT),
        HydrationTip("💦", "أثناء التمرين", "During exercise",
            "رشفات منتظمة كل 15–20 دقيقة أفضل من كمية كبيرة دفعة واحدة أثناء المجهود.",
            "Regular sips every 15–20 min beat one large gulp during effort.",
            TipCategory.SPORT),
        HydrationTip("🧂", "بعد المجهود الطويل", "After long effort",
            "بعد التمرين الطويل أو العرق الغزير، عوّض الماء والأملاح (الإلكتروليت) معاً لا الماء وحده.",
            "After long workouts or heavy sweating, replace both water and salts (electrolytes), not water alone.",
            TipCategory.SPORT),

        HydrationTip("🌙", "ترطيب أثناء الصيام", "Hydration while fasting",
            "وزّع الماء على رشفات خلال ساعات الأكل بعد انتهاء الصيام، وتجنّب شربه دفعة واحدة لتحسين الامتصاص.",
            "Spread water as sips through your eating window after the fast ends, and avoid gulping it all at once for better absorption.",
            TipCategory.FASTING),
        HydrationTip("☕", "قلّل المنبهات", "Go easy on stimulants",
            "القهوة والشاي بكثرة تزيد إدرار البول وتسرّع الجفاف أثناء الصيام. اعتدل فيها.",
            "Lots of coffee and tea increase urine output and speed up dehydration while fasting. Keep them moderate.",
            TipCategory.FASTING),
        HydrationTip("🥛", "وجبة ما قبل الصيام", "Pre-fast meal",
            "اجعل آخر وجبة قبل الصيام غنية بأطعمة مائية كالزبادي والخيار لتقليل العطش أثناء الصيام.",
            "Make your last meal before the fast rich in water-heavy foods like yogurt and cucumber to reduce thirst during the fast.",
            TipCategory.FASTING),

        HydrationTip("🌅", "ابدأ يومك بكأس", "Start your day with a glass",
            "كأس ماء بعد الاستيقاظ يعوّض ما فقدته أثناء النوم وينشّط الجسم والأيض.",
            "A glass of water after waking replaces what you lost overnight and kick-starts your body and metabolism.",
            TipCategory.HABITS),
        HydrationTip("🍶", "قارورة بجانبك", "Keep a bottle nearby",
            "وجود الماء أمام عينك يضاعف شربك. احتفظ بقارورة على مكتبك وفي سيارتك.",
            "Water in sight doubles how much you drink. Keep a bottle on your desk and in your car.",
            TipCategory.HABITS),
        HydrationTip("⏰", "اربط الماء بعاداتك", "Anchor water to routines",
            "اشرب كأساً مع كل صلاة أو عند كل استراحة. ربط الماء بعادة ثابتة يجعله تلقائياً.",
            "Drink a glass with each prayer or break. Tying water to a fixed habit makes it automatic.",
            TipCategory.HABITS),
        HydrationTip("🚫", "انتبه للمشروبات السكرية", "Beware sugary drinks",
            "العصائر والمشروبات الغازية لا تعوّض الماء جيداً وتضيف سعرات. الماء يبقى الأفضل.",
            "Juices and sodas don't replace water well and add calories. Water is still best.",
            TipCategory.HABITS)
    )

    /** A stable tip for today, rotating daily through the whole list. */
    fun tipOfTheDay(now: Long = System.currentTimeMillis()): HydrationTip {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val year = cal.get(Calendar.YEAR)
        val index = ((dayOfYear + year) % all.size + all.size) % all.size
        return all[index]
    }

    fun byCategory(category: TipCategory): List<HydrationTip> = all.filter { it.category == category }
}
