# VeroSip Vision Core

نظام التعرّف الذكي على الكأس/العبوة في VeroSip. يعمل بالكامل **على الجهاز** دون إنترنت
ودون تخزين أي صورة. يجمع ثلاثة مصادر قرار مع حارسٍ هندسي وقائمة حظر.

> **VeroSip** always written in Latin letters.

## كيف يتخذ المحرك القرار (`VeroSipVisionEngine.decide`)

الترتيب في [`VeroSipVisionEngine.kt`](../app/src/main/java/com/hydroforce/pro/camera/VeroSipVisionEngine.kt):

1. **قائمة الحظر (Blacklist):** طعام/صحون/سجائر/فيب/ولاعة/إلكترونيات ≥ 0.38 → **رفض فوري**.
2. **المصدر (أ) بصمة أكوابك المحفوظة** — [`CupFingerprintMatcher.kt`](../app/src/main/java/com/hydroforce/pro/camera/CupFingerprintMatcher.kt):
   بصمة بصرية 64-قيمة (شبكة ألوان 4×4×3 + شبكة كثافة حواف 4×4)، مطابقة بتشابه جيب التمام
   (cosine) ≥ **0.88**. هذا أدق مصدر لأنه يتعلّم عبواتك أنت بالذات (الشفافة منها خصوصاً).
3. **المصدر (ب) نموذج TFLite اختياري** — [`OptionalTfliteModel`]:
   يُفعَّل تلقائياً **فقط** عند وجود `app/src/main/assets/verosip_vision.tflite`.
   بدونه يرجع `null` ولا يؤثر على البناء إطلاقاً (التطبيق يبقى خفيفاً ويبني 100%).
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

## النموذج الاختياري (TFLite) — لمن يريد طبقة إضافية

التطبيق **لا يحتاجه**. لإضافته:

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
