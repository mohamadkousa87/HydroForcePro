#!/usr/bin/env python3
"""
VeroSip Vision — optional on-device TFLite model builder.

Trains a tiny MobileNetV2 transfer-learning classifier to tell drink containers
(water bottles / glasses / mugs / gym flasks) from everything else, then exports a
quantized .tflite under ~4 MB plus a labels file. Drop the two outputs into
app/src/main/assets/ and VeroSip's OptionalTfliteModel picks them up automatically
as acceptance "Source B" — no app code change needed.

The app already works WITHOUT this model (saved fingerprints + ML Kit + geometry
guard). This script is only for users who want an extra, custom-trained layer.

--------------------------------------------------------------------------------
USAGE
--------------------------------------------------------------------------------
1. Collect images into this folder layout (a few hundred per class is plenty):

   dataset/
     water_bottle/    *.jpg   (transparent plastic/glass bottles with water)
     drinking_glass/  *.jpg   (clear glasses/tumblers of water)
     mug_cup/         *.jpg   (mugs, herbal/tea cups, coffee cups)
     gym_flask/       *.jpg   (opaque sports/gym bottles, shakers)
     negative_reject/ *.jpg   (food, plates, phones, lighters, vapes, hands, faces)

2. Install deps:
     pip install "tensorflow>=2.13" pillow

3. Train + export:
     python tools/build_verosip_vision.py --data dataset --epochs 12

4. Copy outputs into the app and rebuild:
     cp build/verosip_vision.tflite app/src/main/assets/verosip_vision.tflite
     cp build/verosip_labels.txt    app/src/main/assets/verosip_labels.txt

The label file lists one class per line IN THE SAME ORDER as the model's output
logits. "negative_reject" MUST be present — the app treats that class as "block".
--------------------------------------------------------------------------------
"""
import argparse
import os
import sys

CLASSES = ["water_bottle", "drinking_glass", "mug_cup", "gym_flask", "negative_reject"]
IMG_SIZE = 224


def build_model(num_classes: int):
    import tensorflow as tf
    base = tf.keras.applications.MobileNetV2(
        input_shape=(IMG_SIZE, IMG_SIZE, 3), include_top=False, weights="imagenet"
    )
    base.trainable = False  # transfer learning: freeze the backbone
    model = tf.keras.Sequential([
        tf.keras.layers.Rescaling(1.0 / 127.5, offset=-1.0),
        base,
        tf.keras.layers.GlobalAveragePooling2D(),
        tf.keras.layers.Dropout(0.2),
        tf.keras.layers.Dense(num_classes, activation="softmax"),
    ])
    model.compile(
        optimizer="adam",
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )
    return model


def main():
    ap = argparse.ArgumentParser(description="Build VeroSip optional TFLite vision model")
    ap.add_argument("--data", default="dataset", help="dataset root (one subfolder per class)")
    ap.add_argument("--out", default="build", help="output directory")
    ap.add_argument("--epochs", type=int, default=12)
    ap.add_argument("--batch", type=int, default=16)
    args = ap.parse_args()

    try:
        import tensorflow as tf
    except ImportError:
        print("ERROR: TensorFlow not installed. Run: pip install \"tensorflow>=2.13\" pillow")
        sys.exit(1)

    if not os.path.isdir(args.data):
        print(f"ERROR: dataset folder '{args.data}' not found. See the header of this script.")
        sys.exit(1)

    os.makedirs(args.out, exist_ok=True)

    # Load images; label order is alphabetical to match CLASSES above.
    train_ds = tf.keras.utils.image_dataset_from_directory(
        args.data, validation_split=0.2, subset="training", seed=42,
        image_size=(IMG_SIZE, IMG_SIZE), batch_size=args.batch,
        class_names=[c for c in CLASSES if os.path.isdir(os.path.join(args.data, c))],
    )
    val_ds = tf.keras.utils.image_dataset_from_directory(
        args.data, validation_split=0.2, subset="validation", seed=42,
        image_size=(IMG_SIZE, IMG_SIZE), batch_size=args.batch,
        class_names=[c for c in CLASSES if os.path.isdir(os.path.join(args.data, c))],
    )
    class_names = train_ds.class_names
    print("Classes:", class_names)
    if "negative_reject" not in class_names:
        print("WARNING: no 'negative_reject' class found — the app needs it to block non-drinks.")

    aug = tf.keras.Sequential([
        tf.keras.layers.RandomFlip("horizontal"),
        tf.keras.layers.RandomRotation(0.1),
        tf.keras.layers.RandomZoom(0.1),
        tf.keras.layers.RandomBrightness(0.1),
    ])
    train_ds = train_ds.map(lambda x, y: (aug(x), y)).prefetch(tf.data.AUTOTUNE)
    val_ds = val_ds.prefetch(tf.data.AUTOTUNE)

    model = build_model(len(class_names))
    model.fit(train_ds, validation_data=val_ds, epochs=args.epochs)

    # Quantize to int8-friendly float16 for a small, fast on-device model.
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]
    tflite_model = converter.convert()

    model_path = os.path.join(args.out, "verosip_vision.tflite")
    labels_path = os.path.join(args.out, "verosip_labels.txt")
    with open(model_path, "wb") as f:
        f.write(tflite_model)
    with open(labels_path, "w", encoding="utf-8") as f:
        f.write("\n".join(class_names) + "\n")

    size_mb = os.path.getsize(model_path) / (1024 * 1024)
    print(f"\nWrote {model_path} ({size_mb:.2f} MB)")
    print(f"Wrote {labels_path}")
    if size_mb > 4.0:
        print("NOTE: model is >4 MB — consider full int8 quantization with a representative dataset.")
    print("\nNext: copy both files into app/src/main/assets/ and rebuild the APK.")


if __name__ == "__main__":
    main()
