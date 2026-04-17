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
    WshShell.Run "ollama serve", 0, False
    WScript.Sleep 5000  ' Wait 5s for Ollama to initialize
End If

' ---- Start Ngrok in background (hidden, no window) ----
Dim ngrokPath
ngrokPath = "C:\Users\nani0\AppData\Local\Microsoft\WinGet\Packages\Ngrok.Ngrok_Microsoft.Winget.Source_8wekyb3d8bbwe\ngrok.exe"
WshShell.Run Chr(34) & ngrokPath & Chr(34) & " http --domain=shakiest-unspotlighted-priscila.ngrok-free.dev --host-header=rewrite 11434", 0, False

Set WshShell = Nothing
Set objWMI = Nothing
