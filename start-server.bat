@echo off
title ES Career Guide Server Startup
color 0A

echo =============================================
echo   Embedded Systems Career Guide - Server
echo =============================================
echo.

:: ---- Step 1: Start Ollama (if not already running) ----
echo [1/2] Checking Ollama...
:: Flash Attention's MMA CUDA kernel crashes llama-server on this GPU
:: (exit 0xc0000409, "CUDA error: shared object initialization failed" in
:: ggml_cuda_flash_attn_ext_mma_f16_case). Disabling it trades ~31 tok/s for
:: ~7 tok/s but the server no longer dies on the first generate call.
set OLLAMA_FLASH_ATTENTION=false
tasklist /FI "IMAGENAME eq ollama.exe" | find /I "ollama.exe" >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo     Starting Ollama...
    start "" ollama serve
    timeout /t 5 /nobreak >nul
    echo     Ollama started!
) else (
    echo     Ollama is already running.
)

:: ---- Step 2: Kill any existing Ngrok instance ----
echo [2/2] Starting Ngrok tunnel...
tasklist /FI "IMAGENAME eq ngrok.exe" | find /I "ngrok.exe" >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo     Stopping existing Ngrok instance...
    taskkill /F /IM ngrok.exe >nul 2>&1
    timeout /t 2 /nobreak >nul
)

:: ---- Step 3: Start Ngrok with the correct flags ----
:: Free dev domain reserved for the "khashyap" ngrok account - persists
:: across restarts, unlike a plain `ngrok http 11434` which auto-assigns a
:: new ephemeral *.ngrok-free.app URL every time.
echo     Launching Ngrok tunnel (landfall-quilt-passover.ngrok-free.dev)...
start "" "C:\Users\nani0\AppData\Local\Microsoft\WinGet\Packages\Ngrok.Ngrok_Microsoft.Winget.Source_8wekyb3d8bbwe\ngrok.exe" http --domain=landfall-quilt-passover.ngrok-free.dev --host-header=rewrite 11434
timeout /t 4 /nobreak >nul

echo.
echo =============================================
echo   Server is LIVE!
echo   Domain: landfall-quilt-passover.ngrok-free.dev
echo   Model:  es-career-guide-14b  (falls back: es-guide-gemma4)
echo   Status: Ready for app connections
echo =============================================
echo.
echo [This window can be minimized. DO NOT close it.]
echo.
pause
