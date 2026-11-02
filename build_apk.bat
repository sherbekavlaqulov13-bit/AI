@echo off
chcp 65001 >nul
echo ===================================================
echo 🛠 OvozAI APK Yaratuvchi (Build Script)
echo ===================================================
echo.

where java >nul 2>&1
if %errorlevel% neq 0 (
    echo [!] Kompyuteringizda Java (JDK 17) topilmadi.
    echo.
    echo APK yaratish uchun kompyuterda Java JDK yoki Android Studio kerak.
    echo.
    echo Eng oson va tez 2 ta yo'l:
    echo 1-usul: Android Studio dasturida "android" papkasini ochib, Build -^> Build APK tugmasini bosing.
    echo 2-usul: Loyihani GitHub'ga yuklasangiz, biz tayyorlab qo'ygan GitHub Actions 2 daqiqada avtomatik APK chiqarib beradi.
    echo.
    pause
    exit /b 1
)

echo [✓] Java topildi!
cd /d "%~dp0android"
call gradlew.bat assembleDebug

if %errorlevel% equ 0 (
    echo.
    echo [✓] APK muvaffaqiyatli tayyorlandi!
    echo Fayl manzili: %~dp0android\app\build\outputs\apk\debug\app-debug.apk
) else (
    echo.
    echo [X] Build jarayonida xatolik yuz berdi.
)
pause
