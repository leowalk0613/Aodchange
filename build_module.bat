@echo off
echo Building AOD Lock Screen Real-time Refresh Module...

cd /d "D:\work\aodchange"

echo Cleaning previous builds...
call gradlew clean

echo Compiling the project...
call gradlew assembleDebug

if %ERRORLEVEL% == 0 (
    echo Build successful!
    echo APK located at: app\build\outputs\apk\debug\app-debug.apk
    echo Copying to releases folder...
    if not exist releases mkdir releases
    copy "app\build\outputs\apk\debug\app-debug.apk" "releases\AODRealTimeRefresh-v1.0.apk"
    echo Done!
) else (
    echo Build failed!
    pause
)