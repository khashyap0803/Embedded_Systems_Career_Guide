@echo off
title ES Career Guide Server Startup
color 0A

echo =============================================
echo   Embedded Systems Career Guide - Server
echo =============================================
echo.

:: ---- Step 1: Start Ollama (if not already running) ----
echo [1/2] Checking Ollama...

:: Flash attention was previously forced off here: its MMA CUDA kernel used to
:: abort llama-server on this card ("CUDA error: shared object initialization
:: failed"). Re-tested on driver 610.62 / Ollama 0.32.4 and it no longer
:: reproduces - that failure was a host-side module-load problem, not a kernel
:: bug, and it is gone. Measured with llama-bench on the 14B Q4_K_M:
:: prompt processing 1166 -> 1953 t/s, generation 41.0 -> 42.4 tok/s.
set OLLAMA_FLASH_ATTENTION=1

:: Halves KV cache memory, which is what buys the larger context below.
:: Ollama applies this to BOTH the K and V cache, and a MATCHED pair is what
:: matters: llama.cpp only compiles flash-attention kernels for matching cache
:: types, so a mismatched pair silently falls back to CPU attention. Measured
:: cost of the matched q8_0 pair is inside noise (pp 2011 -> 1980 t/s,
:: tg 42.7 -> 42.3 tok/s); a mismatched pair collapses prefill to 280 t/s.
:: Note quantizing the V cache REQUIRES flash attention - the two settings
:: above are a package, do not disable one without the other.
set OLLAMA_KV_CACHE_TYPE=q8_0

:: Two concurrent request slots. Ollama allocates num_ctx * NUM_PARALLEL of KV
:: cache, so this is 2 x 16384 = 32768 tokens; raising it further needs the
:: per-request context lowered to match or the model spills out of the 16 GB.
set OLLAMA_NUM_PARALLEL=2

:: Ollama derives a default context from total VRAM, and every card under
:: 23 GiB gets 4096 - which is what this box was silently running. The app asks
:: for num_predict 16384, and rather than erroring, Ollama's default
:: --context-shift quietly evicts the oldest tokens once the window fills. A
:: report generation therefore lost the very questions it was answering
:: partway through, which is what produced truncated and malformed reports.
:: The model itself is trained for 40960; 16384 is what fits alongside two
:: slots. The Modelfile sets this too - this is the belt-and-braces default.
set OLLAMA_CONTEXT_LENGTH=16384
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
