# VeroSip Vision Core

نظام تعرّف **سلس ومريح** على الكأس/العبوة في VeroSip. يعمل بالكامل **على الجهاز** دون إنترنت
ودون تخزين أي صورة (مع فحص سحابي اختياري BYOK). لا بصمة ألوان ولا بوابات هندسية صارمة — فتلك
كانت ترفض العبوات والكاسات الشفافة وتطابق الخلفية.

> **VeroSip** always written in Latin letters.

## كيف يتخذ المحرك القرار (`VeroSipVisionEngine.decide`)

يُقبل الإطار (أخضر) بمجرد أي مؤشر مشروب/وعاء. الترتيب في
[`VeroSipVisionEngine.kt`](../app/src/main/java/com/hydroforce/pro/camera/VeroSipVisionEngine.kt):

1. **رفض OCR** — [`VeroSipOcr.kt`](../app/src/main/java/com/hydroforce/pro/camera/VeroSipOcr.kt):
   كلمة تدخين (nicotine/vape/tobacco…) على الملصق → **رفض فوري**.
2. **عبوة محفوظة ⭐:** تطابق **باركود** العبوة أو كلمتها المخزّنة → "تم التعرف على عبوتك: [الاسم] ⭐".
3. **أي باركود** في الإطار ([`VeroSipBarcode.kt`](../app/src/main/java/com/hydroforce/pro/camera/VeroSipBarcode.kt), `com.google.mlkit:barcode-scanning`) → قبول (منتج معبّأ).
4. **كلمة شراب على الملصق (OCR):** water/aqua/milk/laban/ayran/juice/…ml → قبول مع "تم قراءة ملصق العبوة: X ✓".
5. **كاشف COCO** (`OptionalTfliteModel`, SSD MobileNet v1 quant) يرصد `bottle`/`cup`/`wine glass` → قبول.
6. **تلميح ML Kit** من `ACCEPT_HINTS` (قائمة واسعة: bottle/cup/glass/milk/dairy/juice/carton/…) بعتبة مريحة **≥ 0.28** → قبول.
7. **حظر ضيّق فقط** (`BLOCK_OBJECTS`: cigarette/vape/lighter/cell phone/laptop/keyboard/mouse) **≥ 0.58**
   وبشرط عدم وجود باركود أو نص مشروب → رفض.

الباركود و OCR يعملان على **خيط خلفي** بتبريد ~1.2ث في المعاينة الحية وتشغيل كامل عند التوثيق.

النتيجة `Decision(locked, rejected, label, savedName, ocrLabel)`:
- `locked` → مُثبَّت (قطرة زرقاء ملكية `#003B99` + ✓ خضراء + إطار أخضر `#00E676`).
- `rejected` → ممنوع (إطار أحمر).
- غير ذلك → مسح (قطرة سماوية).

**التأكيد يدوي دائماً** — لا تسجيل إلا بضغط "توثيق الشرب".

## أكوابي وعبواتي ⭐

- شريط اختصارات أفقي بالأكواب المحفوظة؛ الضغط على كوب يضبط حجمه ويفعّل القبول الفوري.
- زر **"+ تعريف كأسي/عبوتي ⭐"** يلتقط إطاراً، يقرأ **الباركود أو كلمة الملصق**، ويحفظ
  (الاسم + الحجم + نوع المشروب + `barcodeOrKeyword`) في Room (`saved_cup_signatures`, DB v5).
  بوجود باركود/كلمة → تعرّف تلقائي 100% بغضّ النظر عن الخلفية/الإضاءة.

## الفحص السحابي الاختياري (Cloud AI — BYOK)

[`CloudVisionVerifier.kt`](../app/src/main/java/com/hydroforce/pro/camera/CloudVisionVerifier.kt)
+ [`ui/CloudAiDialog.kt`](../app/src/main/java/com/hydroforce/pro/ui/CloudAiDialog.kt): عند التفعيل
ووجود مفتاح Gemini مجاني من المستخدم واتصال إنترنت، تُرسل صورة مصغّرة إلى `gemini-2.5-flash`
للتحقق بدقة؛ أي خطأ/عدم اتصال → **رجوع تلقائي للوضع المحلي**. المفتاح يُخزّن محلياً فقط
(`SettingsRepository`)، والدليل المبسّط يفتح `https://aistudio.google.com/apikey`.

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

المعالجة المحلية (باركود/OCR/COCO/ML Kit) كلها في الذاكرة لحظياً ولا تُحفظ أي صورة. المخزّن مع
الكوب نص مجرّد فقط (باركود أو كلمة). **الفحص السحابي اختياري** ومعطّل افتراضياً — لا يُرسل شيء
إلا إذا فعّله المستخدم وأدخل مفتاحه؛ وقتها تُرسل صورة الإطار المصغّرة إلى خدمة Gemini بمفتاحه هو.
