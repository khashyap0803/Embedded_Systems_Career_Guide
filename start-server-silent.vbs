Dim WshShell, oExec
Set WshShell = CreateObject("WScript.Shell")
Set objWMI = GetObject("winmgmts:\\.\root\cimv2")

' ---- Kill any old Ngrok instance ----
Set colProcesses = objWMI.ExecQuery("SELECT * FROM Win32_Process WHERE Name = 'ngrok.exe'")
For Each proc In colProcesses
    proc.Terminate()
Next

' ---- Start Ollama in background (hidden, no window) ----
Dim ollamaRunning
ollamaRunning = False
Set colOllama = objWMI.ExecQuery("SELECT * FROM Win32_Process WHERE Name = 'ollama.exe'")
For Each proc In colOllama
    ollamaRunning = True
Next

If Not ollamaRunning Then
    ' Keep these four in sync with start-server.bat, which documents why each
    ' one is set. In short: flash attention no longer crashes on driver 610.62
    ' and is a large prefill win; q8_0 halves the KV cache and must be paired
    ' with flash attention; two slots at 16384 tokens each is what fits in
    ' 16 GB; and without CONTEXT_LENGTH Ollama silently defaults this card to
    ' 4096 and then context-shifts away the prompt mid-generation.
    ' WshShell.Run does not inherit a Dim'd VBS variable as a process env var,
    ' so set them in the child cmd.exe's own environment instead.
    WshShell.Run "cmd /c set OLLAMA_FLASH_ATTENTION=1&& set OLLAMA_KV_CACHE_TYPE=q8_0&& set OLLAMA_NUM_PARALLEL=2&& set OLLAMA_CONTEXT_LENGTH=16384&& ollama serve", 0, False
    WScript.Sleep 5000  ' Wait 5s for Ollama to initialize
End If

' ---- Start Ngrok in background (hidden, no window) ----
' Free dev domain reserved for the "khashyap" ngrok account - persists
' across restarts, unlike a plain `ngrok http 11434` which auto-assigns a
' new ephemeral *.ngrok-free.app URL every time.
Dim ngrokPath
ngrokPath = "C:\Users\nani0\AppData\Local\Microsoft\WinGet\Packages\Ngrok.Ngrok_Microsoft.Winget.Source_8wekyb3d8bbwe\ngrok.exe"
WshShell.Run Chr(34) & ngrokPath & Chr(34) & " http --domain=landfall-quilt-passover.ngrok-free.dev --host-header=rewrite 11434", 0, False

Set WshShell = Nothing
Set objWMI = Nothing
