import json
import os
import re
import urllib.request
import urllib.error
from typing import Any, Dict, List

# Gemini bepul API kalitini ixtiyoriy ishlatish uchun (bo'lmasa 100% bepul lokal ishlaydi)
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")

def process_voice_command(
    command: str,
    incoming_messages_summary: str = "",
    last_turn_context: str = "",
    api_key: str = ""
) -> Dict[str, Any]:
    """
    100% Bepul O'zbek tili sun'iy idrok yordamchisi.
    API kalit yoki to'lov talab qilmaydi!
    """
    key = api_key or GEMINI_API_KEY
    if key:
        try:
            return call_gemini_api(command, incoming_messages_summary, last_turn_context, key)
        except Exception:
            pass

    return on_device_uzbek_nlp(command, incoming_messages_summary, last_turn_context)


def on_device_uzbek_nlp(
    command: str,
    incoming_messages: str = "",
    last_turn_context: str = ""
) -> Dict[str, Any]:
    text = command.lower().strip()
    text = text.replace("’", "'").replace("`", "'").replace("‘", "'")
    plan: List[Dict[str, Any]] = []

    # 1. Yangi xabarlarni so'rash
    if any(w in text for w in ["xabar", "sms", "kim yozdi", "bildirishnoma"]):
        if not any(w in text for w in ["yoz ", "yubor"]):
            resp = incoming_messages if incoming_messages else "Hozircha sizga yangi xabarlar kelmagan."
            return {"voice_response": resp, "execution_plan": []}

    # 2. Xabarga javob yozish
    if "xabar" in last_turn_context and any(w in text for w in ["ha", "deb yoz", "yoz"]):
        clean = re.sub(r"^ha[,.]?\s*", "", text)
        clean = re.sub(r"\s*deb\s*yoz\s*$", "", clean)
        clean = re.sub(r"\s*yoz\s*$", "", clean).strip()
        msg = clean if clean else "Hozir yetib boraman"
        plan.append({"tool": "reply_message", "parameters": {"text": msg}})
        return {"voice_response": f'Xabar yuborildi: "{msg}"', "execution_plan": plan}

    # 3. Ilovada xabar yuborish
    if any(w in text for w in ["yoz", "yubor"]) and any(w in text for w in ["telegram", "whatsapp", "sms", "ga "]):
        app = "WhatsApp" if "whatsapp" in text else "Telegram"
        recipient = "Kontakt"
        words = text.split(" ")
        for w in words:
            if w.endswith("ga") and not w.startswith("tele") and not w.startswith("what"):
                recipient = w[:-2].capitalize()

        msg = "Salom"
        if "deb" in text:
            parts = text.split("deb")
            msg = " ".join(parts[0].split()[-3:])
        elif "yoz" in text:
            parts = text.split("yoz")
            msg = " ".join(parts[0].split()[-3:])

        plan.append({"tool": "send_app_message", "parameters": {"app_name": app, "recipient": recipient, "text": msg}})
        return {"voice_response": f'{app} orqali {recipient}ga "{msg}" xabari yuborilmoqda.', "execution_plan": plan}

    # 4. Erkin suhbat (Salomlashish)
    if any(w in text for w in ["salom", "assalom"]):
        return {
            "voice_response": "Assalomu alaykum! Telefoningizni boshqarishga tayyorman, qanday buyruq bajaramiz?",
            "execution_plan": []
        }

    if any(w in text for w in ["qalaysan", "yaxshimisiz", "ishlar qalay"]):
        return {
            "voice_response": "Rahmat, a'loman! Sizga xizmat qilishdan xursandman.",
            "execution_plan": []
        }

    # 5. Sozlamalar va funksiyalar
    if any(w in text for w in ["fonar", "chiroq"]):
        state = not any(w in text for w in ["o'chir", "yop", "ochir"])
        plan.append({"tool": "toggle_setting", "parameters": {"setting": "flashlight", "state": state}})
        return {"voice_response": "Fonar yoqildi." if state else "Fonar o'chirildi.", "execution_plan": plan}

    if any(w in text for w in ["wifi", "wi-fi", "vayfay"]):
        state = not any(w in text for w in ["o'chir", "yop"])
        plan.append({"tool": "toggle_setting", "parameters": {"setting": "wifi", "state": state}})
        return {"voice_response": "Wi-Fi yoqilmoqda." if state else "Wi-Fi o'chirilmoqda.", "execution_plan": plan}

    # 6. Ovoz balandligi
    if "ovoz" in text:
        if any(w in text for w in ["pasay", "kam", "pasaytir"]):
            plan.append({"tool": "set_volume", "parameters": {"percent": 30}})
            return {"voice_response": "Ovoz pasaytirildi.", "execution_plan": plan}
        elif any(w in text for w in ["ko'tar", "oshir", "baland"]):
            plan.append({"tool": "set_volume", "parameters": {"percent": 85}})
            return {"voice_response": "Ovoz ko'tarildi.", "execution_plan": plan}

    # 7. Tizim amallari
    if any(w in text for w in ["bosh sahifa", "bosh ekran", "uyga", "home"]):
        plan.append({"tool": "global_action", "parameters": {"action": "home"}})
        return {"voice_response": "Bosh ekranga qaytildi.", "execution_plan": plan}

    if any(w in text for w in ["orqaga", "back"]):
        plan.append({"tool": "global_action", "parameters": {"action": "back"}})
        return {"voice_response": "Orqaga qaytildi.", "execution_plan": plan}

    if any(w in text for w in ["skrinshot", "ekran rasmi", "screenshot"]):
        plan.append({"tool": "global_action", "parameters": {"action": "screenshot"}})
        return {"voice_response": "Ekran rasmi olindi.", "execution_plan": plan}

    # 8. Hamma ilovalarni ochish
    if any(w in text for w in ["och", "kir", "ishga tushir"]):
        app_name = text
        for token in ["och", "kir", "ishga tushir", "ilovani", "dasturni", "ni", "ga"]:
            app_name = app_name.replace(token, "")
        app_name = app_name.strip()
        if app_name:
            plan.append({"tool": "open_app", "parameters": {"app_name": app_name}})
            return {"voice_response": f"{app_name.capitalize()} ochilmoqda.", "execution_plan": plan}

    return {
        "voice_response": f'"{command}" buyrug\'i qabul qilindi va telefonda bajarilmoqda.',
        "execution_plan": plan
    }


def call_gemini_api(command: str, msgs: str, context: str, key: str) -> Dict[str, Any]:
    url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={key}"
    payload = {
        "contents": [{"role": "user", "parts": [{"text": f"Buyruq: {command}\nXabarlar: {msgs}\nKontekst: {context}"}]}],
        "generationConfig": {"responseMimeType": "application/json", "temperature": 0.2}
    }
    req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=10) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        candidate = data.get("candidates", [{}])[0]
        text = candidate.get("content", {}).get("parts", [{}])[0].get("text", "{}")
        return json.loads(text)


if __name__ == "__main__":
    tests = [
        "Menga yangi xabarlar bormi?",
        "Telegramda Akmalga salom deb yoz",
        "Payme och",
        "Kalkulyatorga kir",
        "Chiroqni yoq",
        "Ovozni ko'tar",
        "Bosh sahifaga qayt"
    ]
    print("=" * 60)
    print("OvozAI — 100% Bepul va Offline Test")
    print("=" * 60)
    for t in tests:
        res = on_device_uzbek_nlp(t, incoming_messages="Telegramdan Akmaldan xabar bor: 'Qachon kelasan?'. Unga yozaymi?")
        print(f"🎤 Buyruq: {t}")
        print(f"🔊 AI: {res['voice_response']}")
        print(f"⚙️  Reja: {json.dumps(res['execution_plan'], ensure_ascii=False)}")
        print("-" * 60)
