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
    ' Flash Attention's MMA CUDA kernel crashes llama-server on this GPU
    ' (exit 0xc0000409, "CUDA error: shared object initialization failed").
    ' Disabling it trades ~31 tok/s for ~7 tok/s but the server no longer
    ' dies on the first generate call. WshShell.Run does not inherit a
    ' Dim'd VBS variable as a process env var, so set it in the child
    ' cmd.exe's own environment instead.
    WshShell.Run "cmd /c set OLLAMA_FLASH_ATTENTION=false&& ollama serve", 0, False
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
