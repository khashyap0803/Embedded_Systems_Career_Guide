# Comprehensive LLM stress test - runs 7 test categories on all 4 models
# Tests: Reasoning, Context, Code Quality, Hallucination, Output Length, Instruction Following, Technical Depth

$llama = "C:\es-training\llama-cpp\llama-cli.exe"
$results = "C:\es-training\stress_tests"
New-Item -ItemType Directory -Force -Path $results | Out-Null

$models = @("Q4_K_M", "Q5_K_M", "Q6_K", "Q8_0")

# Define all test prompts
$tests = @{
    # TEST 1: Multi-step Reasoning
    "T1_reasoning"     = @{
        Prompt   = "A microcontroller runs at 72 MHz. It needs to generate a PWM signal at exactly 50 Hz with 12-bit resolution (4096 steps). Calculate: 1) The required prescaler value, 2) The auto-reload register value, 3) If I want 75% duty cycle, what compare register value do I set? 4) What is the actual PWM frequency after rounding? 5) What is the percentage error from desired 50 Hz? Show all your work step by step."
        Tokens   = 500
        Category = "Reasoning"
    }
    
    # TEST 2: Context Awareness & Memory
    "T2_context"       = @{
        Prompt   = "I am building an IoT weather station using ESP32 with BME280 sensor over I2C, an SD card over SPI for logging, and WiFi for MQTT publishing. The system must: wake up every 5 minutes, read temperature/humidity/pressure, log to SD card with timestamp, publish to MQTT broker, and go back to deep sleep. Now, considering ALL of the above requirements, answer these: 1) What GPIO pins would conflict if I use default I2C and SPI? 2) How would I calculate battery life with a 3000mAh battery? 3) What happens to the SD card file if the ESP32 crashes mid-write? 4) Write the complete main loop pseudocode that handles ALL these requirements including error recovery."
        Tokens   = 600
        Category = "Context Awareness"
    }
    
    # TEST 3: Complex Code Generation
    "T3_code"          = @{
        Prompt   = "Write a complete, production-ready circular buffer (ring buffer) implementation in C for an embedded system. Requirements: 1) Thread-safe using volatile and atomic operations (no mutex), 2) Fixed 256-byte buffer, 3) Support for both single-byte and multi-byte read/write, 4) Overflow detection and handling, 5) Full Doxygen documentation, 6) Unit test cases. The code must compile on ARM Cortex-M4 with GCC."
        Tokens   = 800
        Category = "Code Quality"
    }
    
    # TEST 4: Hallucination Detection
    "T4_hallucination" = @{
        Prompt   = "Answer these questions about embedded systems. If you are not sure or the question contains incorrect premises, say so clearly: 1) What is the clock speed of the ATmega328P's built-in DSP unit? 2) Explain the CAN FD protocol's maximum baud rate and how it differs from classic CAN. 3) Does the STM32F103 have a built-in Ethernet MAC? 4) What is the maximum I2C speed defined in the Ultra-Fast mode specification? 5) Can you use DMA on the Arduino Uno's ATmega328P?"
        Tokens   = 500
        Category = "Hallucination Resistance"
    }
    
    # TEST 5: Output Length & Coherence
    "T5_length"        = @{
        Prompt   = "Write a complete tutorial on designing a custom PCB for an STM32-based data logger. Cover: schematic design, component selection, power supply design, PCB layout best practices, signal integrity, EMC considerations, manufacturing files, assembly, testing, and debugging. Make this comprehensive enough to be a standalone reference guide."
        Tokens   = 1500
        Category = "Output Length"
    }
    
    # TEST 6: Instruction Following
    "T6_instructions"  = @{
        Prompt   = "I need you to follow these EXACT formatting rules: 1) Start with a one-line summary in bold, 2) Use exactly 3 bullet points for 'Prerequisites', 3) Create a table with exactly 4 columns: Protocol, Speed, Wires, Best-For, 4) Include exactly 2 code blocks, one in C and one in Python, 5) End with a single-sentence conclusion in italics. Topic: Comparing serial communication protocols for embedded systems."
        Tokens   = 500
        Category = "Instruction Following"
    }
    
    # TEST 7: Technical Depth & Edge Cases
    "T7_depth"         = @{
        Prompt   = "I have a hard fault on my STM32F407. The fault registers show: CFSR=0x00008200, HFSR=0x40000000, MMFAR=0x00000000, BFAR=0x20030000. Debug the issue: 1) Decode each fault register bit by bit, 2) What type of fault occurred? 3) What is the most likely cause? 4) How would I find the offending instruction using the stacked PC? 5) Write a hard fault handler in C that dumps all registers and the call stack."
        Tokens   = 600
        Category = "Technical Depth"
    }
}

# Run all tests on all models
foreach ($m in $models) {
    $gguf = "C:\es-training\gguf\es-career-guide-qwen3-14b-$m.gguf"
    Write-Host "`n##### MODEL: $m #####"
    
    foreach ($testName in ($tests.Keys | Sort-Object)) {
        $test = $tests[$testName]
        Write-Host "  Running $testName ($($test.Category))..."
        
        $promptStr = "<|im_start|>user\n$($test.Prompt)<|im_end|>\n<|im_start|>assistant\n"
        $outFile = Join-Path $results "${m}_${testName}.txt"
        $errFile = Join-Path $results "${m}_${testName}_err.txt"
        
        $p = Start-Process -FilePath $llama -ArgumentList @(
            "-m", "`"$gguf`"",
            "--prompt", "`"$promptStr`"",
            "-n", $test.Tokens,
            "-c", "4096",
            "-ngl", "99",
            "--escape",
            "--simple-io",
            "--log-disable"
        ) -PassThru -WindowStyle Hidden -RedirectStandardOutput $outFile -RedirectStandardError $errFile
        
        # Wait based on token count (roughly 1 tok/s worst case + 15s load)
        $waitTime = [math]::Min(90, [math]::Max(30, [int]($test.Tokens / 20) + 15))
        Start-Sleep -Seconds $waitTime
        
        if (!$p.HasExited) {
            Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
        }
        Start-Sleep -Seconds 5
        
        $sz = if (Test-Path $outFile) { [math]::Round((Get-Item $outFile).Length / 1KB, 1) } else { 0 }
        Write-Host "    -> $sz KB captured"
    }
    
    # Clear VRAM between models
    Get-Process -Name "llama*" -ErrorAction SilentlyContinue | Stop-Process -Force
    Start-Sleep -Seconds 8
}

Write-Host "`n===== ALL_STRESS_TESTS_DONE ====="
