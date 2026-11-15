# VeroSip Vision Core

نظام التعرّف الذكي على الكأس/العبوة في VeroSip. يعمل بالكامل **على الجهاز** دون إنترنت
ودون تخزين أي صورة. يجمع ثلاثة مصادر قرار مع حارسٍ هندسي وقائمة حظر.

> **VeroSip** always written in Latin letters.

## كيف يتخذ المحرك القرار (`VeroSipVisionEngine.decide`)

الترتيب في [`VeroSipVisionEngine.kt`](../app/src/main/java/com/hydroforce/pro/camera/VeroSipVisionEngine.kt):

1. **المصدر (أ) بصمة أكوابك المحفوظة** — [`CupFingerprintMatcher.kt`](../app/src/main/java/com/hydroforce/pro/camera/CupFingerprintMatcher.kt):
   بصمة 64-قيمة من **قصّ عمودي ضيّق (36%×55%)** لجسم العبوة فقط. المطابقة **منفصلة للون والحواف**
   (أرضية حواف `0.78` + مجموع `0.90`). ولا تُقبل البصمة إطلاقاً إلا مع:
   - **بوابة هندسية** (`passesGeometry`)، و
   - **بوابة وجود وعاء**: COCO يرصد حاوية، أو ML Kit يرصد كلمة وعاء/سائل بعتبة منخفضة `0.22`، و
   - **نقض الأرضية/الجدار**: لو رصد ML Kit `floor/tile/wall/brick/carpet ≥ 0.45` بلا وعاء من COCO → تُرفض البصمة فوراً.
   > بعد هذا التحديث قد تحتاج **إعادة تعريف** أي كوب سجّلته سابقاً لضمان أفضل دقة.
2. **المصدر (ب) كاشف الكائنات COCO (مُفعَّل ✅)** — [`OptionalTfliteModel`]:
   نموذج Google **SSD MobileNet v1 (quant, ~4MB)** في `app/src/main/assets/verosip_coco.tflite`
   مع `labelmap.txt`. يرصد الكائنات مع صناديق أبعادها (BoundingBox) ويطبّق:
   - **قبول:** `bottle` / `cup` / `wine glass` بثقة ≥ 0.35 **وبشرط** أن الصندوق ليس مسطحاً كالصحن
     (الارتفاع/العرض ≥ 0.65).
   - **رفض فوري:** `bowl`, `cell phone`, `remote` (غالباً الفيب/الولاعة), `banana`, `apple`,
     `sandwich`, `orange`, `broccoli`, `carrot`, `hot dog`, `pizza`, `donut`, `cake` بثقة ≥ 0.40.
   إن غاب الملف أو تعذّر تحميله، يرجع `None` بأمان ويعتمد التطبيق على (أ) + (ج).
3. **الحظر الصلب:** تدخين/إلكترونيات ≥ 0.40، أو طعام بثقة عالية ≥ 0.62 فقط (لا يُرفض كوب الماء
   الصافي الذي يصنّفه ML Kit "food ~42%").
4. **المصدر (ج) ML Kit + الحارس الهندسي:** كلمة شراب صريحة (زجاجة/كوب/ماء…)، أو "glass"
   مشروطة بوجود سياق سائل، **ثم** `passesGeometry` يرفض الأجسام المسطّحة (صحون/منافض:
   الحواف الأفقية تطغى) والصغيرة/النحيلة (ولاعة/فيب لا تملأ الإطار).

النتيجة `Decision(locked, rejected, label, savedName)`:
- `locked` → الهدف مُثبَّت (قطرة زرقاء ملكية + علامة ✓ خضراء + إطار نابض).
- `rejected` → جسم ممنوع (إطار أحمر + رسالة).
- غير ذلك → مسح (قطرة زجاجية سماوية).

**التأكيد يدوي دائماً** — لا تسجيل تلقائي؛ المستخدم يضغط "توثيق الشرب".

## تعريف كأسك/عبوتك ⭐ (أدق طريقة)

داخل شاشة الكاميرا: زر **"+ تعريف كأسي/عبوتي ⭐"** → التقاط إطار → استخراج بصمة → تسمية
(عبوة مياه / مطارتي الرياضية / كوب أعشاب) مربوطة بالحجم المختار → حفظ في Room (جدول
`saved_cup_signatures`, DB v4). بعدها يتعرّف عليها فوراً ويظهر "تم التعرف على: [الاسم] ⭐".
إدارة/حذف الأكواب من زر ⭐ في أعلى الكاميرا.

## كاشف COCO الجاهز (مُدمَج)

الملفان `verosip_coco.tflite` + `labelmap.txt` من حزمة Google الرسمية
`coco_ssd_mobilenet_v1_1.0_quant_2018_06_29`. الاستدلال يعمل عبر `org.tensorflow:tensorflow-lite`
مع `androidResources { noCompress += "tflite" }` كي يُربَط النموذج بالذاكرة (memory-map).
لا تدريب ولا تصوير — يتعرّف على القوارير/الأكواب/الكاسات فوراً.

## نموذج مُدرَّب مخصّص (اختياري) — لمن يريد طبقة أدق

التطبيق **لا يحتاجه** (كاشف COCO يكفي). لتدريب نموذجك الخاص:

```bash
pip install "tensorflow>=2.13" pillow
# جهّز مجلد dataset/ (مجلد لكل صنف) كما في رأس السكربت
python tools/build_verosip_vision.py --data dataset --epochs 12
cp build/verosip_vision.tflite app/src/main/assets/verosip_vision.tflite
cp build/verosip_labels.txt    app/src/main/assets/verosip_labels.txt
```

الأصناف الخمسة: `water_bottle`, `drinking_glass`, `mug_cup`, `gym_flask`, `negative_reject`
(الأخير = حظر). المخرج مُكمَّم (float16) بحجم < 4MB تقريباً.

> لتشغيل الاستدلال فعلياً أضِف لاحقاً اعتمادية `org.tensorflow:tensorflow-lite` وفعِّل جسم
> `OptionalTfliteModel.classify`. تُرك مُعطَّلاً عمداً كي يبقى البناء خفيفاً ومستقراً 100%.

## الخصوصية

كل المعالجة في الذاكرة لحظياً. لا تُحفظ أي صورة. البصمة المخزّنة أرقام مجرّدة (64 قيمة)
لا يمكن منها إعادة بناء صورة.
