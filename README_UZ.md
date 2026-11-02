# 🎙 OvozAI — O'zbek tilida telefonni to'liq ovoz orqali boshqaradigan AI tizimi

Bu loyiha Android smartfonini o'zbek tilidagi ovozli buyruqlar orqali 100% boshqarish, barcha ilovalarni ochish, yangi kelgan xabarlarni ovozda o'qib javob yozish va foydalanuvchi bilan jonli suhbatlashish uchun yaratilgan to'liq tizimdir.

---

## 📱 Asosiy imkoniyatlar:

1. **Telefonni o'zi mustaqil boshqarishi (Avtonom harakatlar)**:
   - Maxsus imkoniyatlar (Accessibility Service) orqali AI ekranni tahlil qiladi, kerakli tugmalarni topib bosadi, matn kiritadi va ko'rsatilgan amallarni to'liq bajaradi.
   - Messenjerlarda (Telegram, WhatsApp) kontaktni topib, matn yozib, avtomatik yuboradi.
2. **Telefondagi HAMMA ilovani aytilganda ochish**:
   - `AppLauncher` tizimi telefonga o'rnatilgan 100% barcha ilovalarni (Payme, Click, Uzum, Instagram, YouTube, Telegram, Sozlamalar, Kalkulyator, Galereya, O'yinlar va boshqalar) o'zbekcha, ruscha va inglizcha nomlari hamda sinonimlari bo'yicha darhol topib ochadi.
3. **Yangi xabarlarni o'qib berish va ovoz orqali javob yozish**:
   - Foydalanuvchi: *"Menga yangi xabarlar bormi?"* yoki *"Kimdir yozdimi?"*
   - OvozAI: *"Sizga Telegramdan Akmaldan yangi xabar kelgan: 'Qachon yetib kelasan?'. Unga biror nima deb yozib yuboraymi?"*
   - Foydalanuvchi: *"Ha, 10 minutda yetib boraman deb yoz"*
   - OvozAI: Akmalga to'g'ridan-to'g'ri xabar yuboradi va *"Akmalga javob yuborildi"* deb aytadi!
4. **Barcha ilovalar ustida suzuvchi mikrofon (Floating Overlay)**:
   - Har doim ekranning burchagida turadi, xohlagan joyga suriladi va bir marta bosish bilan ovozli buyruqlarni qabul qiladi.
5. **Qurilma sozlamalari**:
   - Chiroq (fonar)ni yoqish/o'chirish, ovozni ko'tarish/pasaytirish, skrinshot olish, uyga (Home) va orqaga (Back) qaytish.

---

## 📂 Loyiha tuzilishi

```
ovozAI/
├── android/                         # To'liq Android Studio (Kotlin) loyihasi
│   ├── app/src/main/
│   │   ├── AndroidManifest.xml      # Barcha ruxsatlar (Accessibility, Overlay, Notifications, Mic)
│   │   ├── res/layout/              # Ilova va suzuvchi mikrofon UI fayllari
│   │   └── java/com/ovozai/assistant/
│   │       ├── MainActivity.kt      # Ruxsatlarni berish va sozlash ekrani
│   │       ├── service/
│   │       │   ├── OvozAccessibilityService.kt      # Ekranni bosish va avtonom boshqarish
│   │       │   ├── OvozNotificationListenerService.kt # Kelgan xabarlarni o'qish va javob berish
│   │       │   ├── FloatingOverlayService.kt        # Suzuvchi mikrofon xizmati
│   │       │   └── DeviceActionController.kt        # Barcha amallarni ijro qiluvchi kontroller
│   │       ├── util/
│   │       │   └── AppLauncher.kt                   # Hamma ilovalarni topib ochuvchi modul
│   │       ├── ai/
│   │       │   └── GeminiBrain.kt                   # Gemini AI miyasi (O'zbekcha)
│   │       └── voice/
│   │           ├── UzbekSpeechRecognizer.kt         # O'zbekcha nutqni tanish
│   │           └── UzbekTextToSpeech.kt             # O'zbekcha ovoz chiqarish
├── adb_controller/                  # Kompyuterdan USB orqali darhol sinab ko'rish
│   └── index.js
├── build_apk.bat                    # Mahalliy APK yaratuvchi skript
└── README_UZ.md                     # Qo'llanma
```

---

## 🚀 Telefonda ruxsatlarni berish:

Ilova telefonga o'rnatilgach, quyidagi ruxsatlar beriladi:
1. **Maxsus Imkoniyatlar (Accessibility)**: Tugmani bosing -> *OvozAI* xizmatini yoqing (ekranni avtonom boshqarish uchun).
2. **Boshqa ilovalar ustidan ko'rsatish (Overlay)**: *OvozAI* ga ruxsat bering.
3. **Bildirishnomalar (Notification Access)**: *OvozAI* ga ruxsat bering (yangi xabarlarni aytib berish va javob yozish uchun).
4. **Mikrofon**: Ovoz yozish ruxsatini tasdiqlang.
5. **"🚀 Ovozli Yordamchini Ishga Tushirish"** tugmasini bosing!

---

## 🗣 Ovozli muloqot misollari:

### 1. Yangi xabarlar va javob yozish:
> 👤 **Siz:** *"Menga yangi xabarlar bormi?"*  
> 🤖 **OvozAI:** *"Sizga Telegramdan Akmaldan yangi xabar kelgan: 'Qachon yetib kelasan?'. Unga biror nima deb yozib yuboraymi?"*  
> 👤 **Siz:** *"Ha, 10 minutda yetib boraman deb yoz"*  
> 🤖 **OvozAI:** *"Akmalga '10 minutda yetib boraman' deb javob yuborildi."*

### 2. Har qanday ilovani ochish:
> 👤 **Siz:** *"Paymeni och"* / *"Kalkulyatorga kir"* / *"Instagramni och"* / *"Galereyani och"*  
> 🤖 **OvozAI:** *"Payme ochilmoqda"* *(telefondagi mos ilova darhol ochiladi)*

### 3. Telefonni avtonom boshqarish:
> 👤 **Siz:** *"Chiroqni yoqib ber"* / *"Ovozni ko'tar"* / *"Ekran rasmini ol"* / *"Bosh ekranga qayt"*  
> 🤖 **OvozAI:** Amallarni birin-ketin telefonda bajaradi.
