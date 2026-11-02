import json
import re
from typing import Any, Dict, List

SYSTEM_PROMPT = """
Siz mobil qurilmalarni (Android va iOS) to'liq ovoz orqali boshqaruvchi, intellektual va yuqori aniqlikda ishlovchi Ovozli Sun'iy Intellekt Yordamchisisiz.
Sizning yagona maqsadingiz foydalanuvchining ovozli buyruqlarini tushunish, ularni telefonda bajariladigan texnik buyruqlarga (JSON formatida) aylantirish va foydalanuvchi bilan faqat o'zbek tilida muloqot qilishdir.

Qoidalar:
1. Faqat o'zbek tilida (Lotin alifbosida) javob bering.
2. Har doim ikki qism bo'lsin: execution_plan va voice_response.
3. execution_plan ro'yxati bo'lib, ichida tool va parameters bo'lishi kerak.
4. Agar buyruq noaniq bo'lsa, execution_plan [] bo'lsin va savol bersin.
5. Agar xavfsizlikka oid buyruq bo'lsa, tasdiq so'rang.
6. voice_response qisqa, aniq va eshitish uchun qulay bo'lsin.
"""


def normalize_text(text: str) -> str:
    text = text.lower().strip()
    text = text.replace("’", "'").replace("`", "'")
    text = re.sub(r"\s+", " ", text)
    return text


def build_plan(*steps: Dict[str, Any]) -> List[Dict[str, Any]]:
    return [step for step in steps if step is not None]


def respond(command: str) -> Dict[str, Any]:
    text = normalize_text(command)

    if any(word in text for word in ["o'chir", "power off", "telefonni o'chirish", "qurilmani o'chirish"]):
        return {
            "execution_plan": [],
            "voice_response": "Bu xavfsiz buyruq emas. Telefonni o'chirishni tasdiqlashingiz kerak."
        }

    if any(word in text for word in ["pul", "otkazma", "bank", "transfer", "to'lov", "to'lov qiling"]):
        return {
            "execution_plan": [],
            "voice_response": "Pul o'tkazma buyruqini bajarish uchun tasdiq kerak. Iltimos, tasdiqlang."
        }

    plan: List[Dict[str, Any]] = []
    voice_parts: List[str] = []

    if any(word in text for word in ["wi-fi", "wifi", "wi fi", "internet"]):
        if any(word in text for word in ["yoq", "ishga tushir", "faollashtir"]):
            plan.append({
                "tool": "toggle_feature",
                "parameters": {"feature_name": "wifi", "state": True}
            })
            voice_parts.append("Wi-Fi yoqildi")
        elif any(word in text for word in ["o'chir", "yoqilmasin", "faolsizlantir"]):
            plan.append({
                "tool": "toggle_feature",
                "parameters": {"feature_name": "wifi", "state": False}
            })
            voice_parts.append("Wi-Fi o'chirildi")

    if any(word in text for word in ["bluetooth", "bluetoothni", "bluetooth yoq", "bluetooth o'chir"]):
        if any(word in text for word in ["yoq", "faollashtir"]):
            plan.append({
                "tool": "toggle_feature",
                "parameters": {"feature_name": "bluetooth", "state": True}
            })
            voice_parts.append("Bluetooth yoqildi")
        else:
            plan.append({
                "tool": "toggle_feature",
                "parameters": {"feature_name": "bluetooth", "state": False}
            })
            voice_parts.append("Bluetooth o'chirildi")

    if any(word in text for word in ["fonar", "chiroq", "flashlight", "lampochka"]):
        if any(word in text for word in ["yoq", "faollashtir", "yalang'och"]):
            plan.append({
                "tool": "toggle_feature",
                "parameters": {"feature_name": "flashlight", "state": True}
            })
            voice_parts.append("Fonar yoqildi")
        elif any(word in text for word in ["o'chir", "yop"]):
            plan.append({
                "tool": "toggle_feature",
                "parameters": {"feature_name": "flashlight", "state": False}
            })
            voice_parts.append("Fonar o'chirildi")

    if any(word in text for word in ["mobil internet", "mobil_data", "mobil data", "internetni yoq", "internet yoq", "ma'lumotlar"]):
        if any(word in text for word in ["yoq", "faollashtir"]):
            plan.append({
                "tool": "toggle_feature",
                "parameters": {"feature_name": "mobile_data", "state": True}
            })
            voice_parts.append("Mobil internet yoqildi")
        elif any(word in text for word in ["o'chir", "yop"]):
            plan.append({
                "tool": "toggle_feature",
                "parameters": {"feature_name": "mobile_data", "state": False}
            })
            voice_parts.append("Mobil internet o'chirildi")

    if any(word in text for word in ["joylashuv", "location", "geolokatsiya"]):
        if any(word in text for word in ["yoq", "faollashtir"]):
            plan.append({
                "tool": "toggle_feature",
                "parameters": {"feature_name": "location", "state": True}
            })
            voice_parts.append("Joylashuv yoqildi")
        elif any(word in text for word in ["o'chir", "yop"]):
            plan.append({
                "tool": "toggle_feature",
                "parameters": {"feature_name": "location", "state": False}
            })
            voice_parts.append("Joylashuv o'chirildi")

    if any(word in text for word in ["ovoz", "volume", "balandlik"]):
        if "pasay" in text or "kam" in text or "pasaytir" in text:
            plan.append({
                "tool": "set_volume",
                "parameters": {"level": 30}
            })
            voice_parts.append("Ovoz pasaytirildi")
        elif "oshir" in text or "ko'paytir" in text or "baland" in text:
            plan.append({
                "tool": "set_volume",
                "parameters": {"level": 80}
            })
            voice_parts.append("Ovoz ko'tarildi")

    if any(word in text for word in ["telegram", "telegramga kir", "telegram och", "telegramda"]):
        plan.append({
            "tool": "open_app",
            "parameters": {"app_name": "Telegram"}
        })
        voice_parts.append("Telegram ochilmoqda")

    if any(word in text for word in ["youtube", "youtubega kir", "youtube och", "video ko'r"]):
        plan.append({
            "tool": "open_app",
            "parameters": {"app_name": "YouTube"}
        })
        voice_parts.append("YouTube ochilmoqda")

    if any(word in text for word in ["telefonni qulfla", "ekranni qulfla", "screen lock", "qulfla"]):
        plan.append({
            "tool": "lock_screen",
            "parameters": {}
        })
        voice_parts.append("Ekran qulflandi")

    if any(word in text for word in ["skrinshot", "ekran rasmini ol", "screen shot", "ss ol"]):
        plan.append({
            "tool": "take_screenshot",
            "parameters": {}
        })
        voice_parts.append("Ekran rasmi olindi")

    if any(word in text for word in ["xabar yubor", "sms yubor", "yozib yubor", "text yubor"]):
        recipient = "Shaxzod"
        msg = "Men yetib keldim"

        if "shaxzod" in text:
            recipient = "Shaxzod"
        elif "ali" in text:
            recipient = "Ali"

        if "matn" in text:
            m = re.search(r"matn\s+(.*)", text)
            if m:
                msg = m.group(1).strip()

        plan.append({
            "tool": "send_message",
            "parameters": {"platform": "Telegram", "recipient": recipient, "text": msg}
        })
        voice_parts.append(f"{recipient}ga xabar yuborilmoqda")

    if any(word in text for word in ["qo'ng'iroq", "call", "telefon qiling"]):
        contact = "Ali"
        if "shaxzod" in text:
            contact = "Shaxzod"
        plan.append({
            "tool": "make_call",
            "parameters": {"contact_name_or_number": contact}
        })
        voice_parts.append(f"{contact}ga qo'ng'iroq qilinmoqda")

    if any(word in text for word in ["bildirishnoma", "xabarlarni o'qing", "notification", "bildirishnomalar"]):
        plan.append({
            "tool": "read_notifications",
            "parameters": {}
        })
        voice_parts.append("Bildirishnomalar tekshirilmoqda")

    if not plan:
        return {
            "execution_plan": [],
            "voice_response": "Kechirasiz, nimani nazarda tutganingizni tushunmadim. Qaysi ilova yoki sozlamani o'zgartirmoqchisiz?"
        }

    return {
        "execution_plan": plan,
        "voice_response": ". ".join(voice_parts) + "."
    }


if __name__ == "__main__":
    examples = [
        "Wi-Fi ni yoqib, Telegramga kir va Shaxzodga 'Men yetib keldim' deb yozib yubor",
        "Ovozni pasaytir va chiroqni yoq",
        "YouTube och",
        "Telefonni qulfla",
        "U yoqqa o't berib yubor"
    ]

    for ex in examples:
        print(json.dumps(respond(ex), ensure_ascii=False, indent=2))
        print("-" * 60)
