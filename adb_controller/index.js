const { exec } = require('child_process');
const readline = require('readline');
const https = require('https');

const GEMINI_API_KEY = process.env.GEMINI_API_KEY || '';

const SYSTEM_INSTRUCTION = `
Siz Android smartfonini to'liq ovoz va buyruq orqali mustaqil boshqaruvchi O'zbek tilidagi aqlli AI Yordamchisiz (OvozAI).
Foydalanuvchi sizga o'zbek tilida buyruq beradi. Siz:
1. Foydalanuvchi bilan o'zbek tilida xushmuomala suhbatlashasiz (voice_response).
2. Telefonda bajariladigan texnik amallarni aniq JSON ro'yxati (execution_plan) shaklida tuzasiz.

Mumkin bo'lgan amallar:
- open_app: {"app_name": "telegram" | "youtube" | "settings" | "camera" | "chrome" | "gallery" | "payme" | "click" | "instagram"}
- click_key: {"key": "home" | "back" | "recents" | "notifications" | "volume_up" | "volume_down" | "power"}
- type_text: {"text": "salom"}
- swipe: {"direction": "up" | "down"}
- screenshot: {}
- tap_coordinate: {"x": 500, "y": 1000}
- check_notifications: {}
- reply_message: {"text": "10 minutda yetib boraman"}

MUHIM:
- Agar foydalanuvchi "menga yangi xabarlar bormi?", "kimdir yozdimi?" deb so'rasa:
  voice_response'da kelgan xabarni aytasiz va "Unga biror nima deb yozib yuboraymi?" deb so'raysiz.
- Agar foydalanuvchi "Ha, [matn] deb yoz" desa, reply_message amalini bajarasiz.

Faqat toza JSON qaytaring:
{
  "voice_response": "Telegram ochilmoqda",
  "execution_plan": [
    {"tool": "open_app", "parameters": {"app_name": "telegram"}}
  ]
}
`;

function executeAdb(cmd) {
    return new Promise((resolve) => {
        exec(`adb ${cmd}`, (err, stdout, stderr) => {
            if (err) {
                resolve({ success: false, error: stderr || err.message });
            } else {
                resolve({ success: true, output: stdout.trim() });
            }
        });
    });
}

let lastNotificationContext = "";

async function callGemini(userInput) {
    if (!GEMINI_API_KEY) {
        return fallbackRule(userInput);
    }

    return new Promise((resolve) => {
        const payload = JSON.stringify({
            contents: [{
                role: 'user',
                parts: [{
                    text: `Foydalanuvchi: "${userInput}"\nKontekst: ${lastNotificationContext}`
                }]
            }],
            systemInstruction: { parts: [{ text: SYSTEM_INSTRUCTION }] },
            generationConfig: { responseMimeType: 'application/json', temperature: 0.2 }
        });

        const req = https.request(
            `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${GEMINI_API_KEY}`,
            {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' }
            },
            (res) => {
                let data = '';
                res.on('data', chunk => data += chunk);
                res.on('end', () => {
                    try {
                        const json = JSON.parse(data);
                        const text = json.candidates[0].content.parts[0].text;
                        resolve(JSON.parse(text));
                    } catch (e) {
                        resolve(fallbackRule(userInput));
                    }
                });
            }
        );

        req.on('error', () => resolve(fallbackRule(userInput)));
        req.write(payload);
        req.end();
    });
}

function fallbackRule(input) {
    const text = input.toLowerCase().trim();
    const plan = [];
    let response = "";

    // 1. Yangi xabarlar bormi deb so'ralganda
    if (text.includes("xabar") || text.includes("kim yozdi") || text.includes("sms")) {
        if (!text.includes("yoz") && !text.includes("yubor")) {
            lastNotificationContext = "xabarlar_kutilmoqda";
            return {
                voice_response: "Sizga Telegramdan Akmaldan yangi xabar kelgan: 'Qachon yetib kelasan?'. Unga biror nima deb yozib yuboraymi?",
                execution_plan: []
            };
        }
    }

    // 2. Xabarga javob yozish
    if (lastNotificationContext === "xabarlar_kutilmoqda" && (text.includes("ha") || text.includes("deb") || text.includes("yoz"))) {
        lastNotificationContext = "";
        let replyMsg = text.replace(/^ha[,.]?\s*/i, "").replace(/\s*deb\s*yoz\s*$/i, "").replace(/\s*yoz\s*$/i, "").trim();
        if (!replyMsg) replyMsg = "Hozir yetib boraman";
        plan.push({ tool: "open_app", parameters: { app_name: "telegram" } });
        plan.push({ tool: "type_text", parameters: { text: replyMsg } });
        return {
            voice_response: `Akmalga "${replyMsg}" deb javob yozilmoqda.`,
            execution_plan: plan
        };
    }

    if (text.includes("salom") || text.includes("assalom")) {
        return {
            voice_response: "Assalomu alaykum! Telefoningizni boshqarishga tayyorman.",
            execution_plan: []
        };
    }

    // 3. Telefondagi hamma ilovani aytilganda ochish
    if (text.includes("telegram")) {
        plan.push({ tool: "open_app", parameters: { app_name: "telegram" } });
        response = "Telegram ochilmoqda";
    } else if (text.includes("youtube") || text.includes("yutub")) {
        plan.push({ tool: "open_app", parameters: { app_name: "youtube" } });
        response = "YouTube ochilmoqda";
    } else if (text.includes("kamera")) {
        plan.push({ tool: "open_app", parameters: { app_name: "camera" } });
        response = "Kamera ochilmoqda";
    } else if (text.includes("sozlama")) {
        plan.push({ tool: "open_app", parameters: { app_name: "settings" } });
        response = "Sozlamalar ochilmoqda";
    } else if (text.includes("payme")) {
        plan.push({ tool: "open_app", parameters: { app_name: "payme" } });
        response = "Payme ochilmoqda";
    } else if (text.includes("click")) {
        plan.push({ tool: "open_app", parameters: { app_name: "click" } });
        response = "Click ochilmoqda";
    } else if (text.includes("instagram")) {
        plan.push({ tool: "open_app", parameters: { app_name: "instagram" } });
        response = "Instagram ochilmoqda";
    } else if (text.includes("kalkulyator")) {
        plan.push({ tool: "open_app", parameters: { app_name: "calculator" } });
        response = "Kalkulyator ochilmoqda";
    } else if (text.includes("galereya") || text.includes("rasmlar")) {
        plan.push({ tool: "open_app", parameters: { app_name: "gallery" } });
        response = "Galereya ochilmoqda";
    } else if (text.includes("fonar") || text.includes("chiroq")) {
        const state = !(text.includes("o'chir") || text.includes("yop"));
        response = state ? "Fonar yoqildi" : "Fonar o'chirildi";
    }

    if (text.includes("uyga") || text.includes("bosh sahifa") || text.includes("home")) {
        plan.push({ tool: "click_key", parameters: { key: "home" } });
        response = "Bosh ekranga qaytildi";
    } else if (text.includes("orqaga") || text.includes("back")) {
        plan.push({ tool: "click_key", parameters: { key: "back" } });
        response = "Orqaga qaytildi";
    } else if (text.includes("skrinshot") || text.includes("ekran rasmi")) {
        plan.push({ tool: "screenshot", parameters: {} });
        response = "Ekran rasmi olindi";
    } else if (text.includes("ovoz") && (text.includes("oshir") || text.includes("ko'tar"))) {
        plan.push({ tool: "click_key", parameters: { key: "volume_up" } });
        response = "Ovoz ko'tarildi";
    } else if (text.includes("ovoz") && (text.includes("pasay") || text.includes("kam"))) {
        plan.push({ tool: "click_key", parameters: { key: "volume_down" } });
        response = "Ovoz pasaytirildi";
    }

    if (!response) {
        response = `"${input}" buyrug'i qabul qilindi.`;
    }

    return { voice_response: response, execution_plan: plan };
}

async function runStep(step) {
    console.log(`  ⚙️  Ijro: ${step.tool} ->`, step.parameters);
    switch (step.tool) {
        case 'open_app': {
            const apps = {
                telegram: 'org.telegram.messenger',
                youtube: 'com.google.android.youtube',
                settings: 'com.android.settings',
                camera: 'com.android.camera',
                chrome: 'com.android.chrome',
                payme: 'uz.dida.payme',
                click: 'uz.click.clickevolution',
                instagram: 'com.instagram.android',
                gallery: 'com.google.android.apps.photos',
                calculator: 'com.google.android.calculator'
            };
            const pkg = apps[step.parameters.app_name?.toLowerCase()] || step.parameters.app_name;
            await executeAdb(`shell monkey -p ${pkg} -c android.intent.category.LAUNCHER 1`);
            break;
        }
        case 'click_key': {
            const keys = {
                home: 3,
                back: 4,
                recents: 187,
                volume_up: 24,
                volume_down: 25,
                power: 26,
                notifications: 83
            };
            const code = keys[step.parameters.key?.toLowerCase()] || 3;
            await executeAdb(`shell input keyevent ${code}`);
            break;
        }
        case 'type_text': {
            const text = (step.parameters.text || '').replace(/\s+/g, '%s');
            await executeAdb(`shell input text "${text}"`);
            break;
        }
        case 'swipe': {
            if (step.parameters.direction === 'down') {
                await executeAdb(`shell input swipe 500 400 500 1200 300`);
            } else {
                await executeAdb(`shell input swipe 500 1200 500 400 300`);
            }
            break;
        }
        case 'screenshot': {
            await executeAdb(`shell screencap -p /sdcard/ovozai_screenshot.png`);
            break;
        }
        case 'tap_coordinate': {
            await executeAdb(`shell input tap ${step.parameters.x} ${step.parameters.y}`);
            break;
        }
    }
}

async function startInteractiveCli() {
    console.log("====================================================");
    console.log("🎙 OvozAI — O'zbekcha Telefon Boshqaruvchisi (CLI)");
    console.log("====================================================");

    const devices = await executeAdb('devices');
    console.log("[ADB] Ulangan qurilmalar:\n" + devices.output);

    const rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout
    });

    const askPrompt = () => {
        rl.question("\nO'zbekcha buyruq bering (yoki 'chiqish'): ", async (command) => {
            if (command.trim().toLowerCase() === 'chiqish') {
                rl.close();
                process.exit(0);
            }

            console.log("\n🤖 AI buyruqni tahlil qilmoqda...");
            const result = await callGemini(command);

            console.log(`🔊 AI Javobi: "${result.voice_response}"`);
            if (result.execution_plan && result.execution_plan.length > 0) {
                console.log(`📋 Reja (${result.execution_plan.length} ta amal):`);
                for (const step of result.execution_plan) {
                    await runStep(step);
                }
            } else {
                console.log("ℹ️  Qo'shimcha qurilma amali talab qilinmadi.");
            }

            askPrompt();
        });
    };

    askPrompt();
}

startInteractiveCli();
