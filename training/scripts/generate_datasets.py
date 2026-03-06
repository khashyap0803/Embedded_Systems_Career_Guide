#!/usr/bin/env python3
"""
Master Dataset Generation Script for Qwen3-14B Fine-Tuning
==========================================================
Generates 30,000+ training examples across 8 categories using
Qwen3-30B-A3B via vLLM OpenAI-compatible API.

Usage:
  GPU-1: python3 generate_datasets.py --gpu 1 --output-dir /mnt/ssd1/datasets
  GPU-2: python3 generate_datasets.py --gpu 2 --output-dir /mnt/ssd2/datasets

Categories per GPU:
  GPU-1: Assessment Reports (2000), Learning Paths (1500), Stage Content (8000), Quizzes (5000)
  GPU-2: Flashcards (4000), Q&A Doubts (6000), Practice/Challenges (2000), Interview (1500)
"""

import json
import os
import sys
import time
import random
import argparse
import hashlib
import logging
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from openai import OpenAI

# ──────────────────────────────────────────────────────────────
# Configuration
# ──────────────────────────────────────────────────────────────
VLLM_BASE_URL = "http://localhost:{port}/v1"

# All embedded systems topics for diverse generation
ES_TOPICS = [
    # Foundation
    "Digital Electronics", "Number Systems (Binary, Hex, Octal)", "Logic Gates & Boolean Algebra",
    "Combinational Circuits", "Sequential Circuits", "Flip-Flops & Latches",
    "Semiconductor Basics", "Analog Electronics Fundamentals",
    # Microcontrollers
    "8051 Microcontroller Architecture", "AVR ATmega328P", "PIC Microcontrollers",
    "ARM Cortex-M0/M0+", "ARM Cortex-M3/M4", "ARM Cortex-M7",
    "STM32 Family Overview", "ESP32 Architecture", "ESP8266",
    "Raspberry Pi Pico (RP2040)", "Nordic nRF52 Series", "Texas Instruments MSP430",
    "Renesas RA Family", "NXP LPC Series", "Microchip SAM Series",
    # Programming
    "Embedded C Programming", "C for Microcontrollers", "Pointers & Memory in C",
    "Bit Manipulation Techniques", "Structures & Unions in Embedded C",
    "Volatile & Const Qualifiers", "Inline Assembly", "Startup Code & Linker Scripts",
    "Makefile & Build Systems", "CMake for Embedded", "GCC Toolchain",
    "Embedded C++ (Modern)", "Rust for Embedded Systems", "MicroPython",
    # Memory
    "Memory Architecture (ROM/RAM/Flash)", "Stack & Heap Management",
    "Memory-Mapped I/O", "DMA (Direct Memory Access)", "Cache Memory",
    "Flash Memory Programming", "EEPROM Operations", "Memory Protection Unit (MPU)",
    "External Memory Interfaces (SDRAM, QSPI)", "Bootloader Design",
    # GPIO & Peripherals
    "GPIO Configuration & Control", "LED & Button Interfacing",
    "LCD Display (16x2, I2C)", "OLED Display (SSD1306)", "7-Segment Display",
    "Keypad Matrix Interfacing", "Relay & Motor Control", "Sensor Interfacing (DHT11, BMP280)",
    "ADC (Analog-to-Digital Converter)", "DAC (Digital-to-Analog Converter)",
    "PWM (Pulse Width Modulation)", "Servo Motor Control",
    # Timers & Interrupts
    "Timer/Counter Operations", "Timer Modes (Capture, Compare, PWM)",
    "Watchdog Timer", "SysTick Timer", "RTC (Real-Time Clock)",
    "Interrupt Handling Basics", "NVIC (Nested Vector Interrupt Controller)",
    "Interrupt Priority & Nesting", "External Interrupts", "Interrupt Service Routines (ISR)",
    "DMA with Interrupts", "Timer Interrupts",
    # Communication Protocols
    "UART (Universal Asynchronous Receiver/Transmitter)", "SPI (Serial Peripheral Interface)",
    "I2C (Inter-Integrated Circuit)", "CAN Bus Protocol", "LIN Bus",
    "RS-232 & RS-485", "USB Protocol Basics", "USB-CDC & USB-HID",
    "Ethernet (MAC/PHY)", "Wi-Fi (ESP32)", "Bluetooth & BLE",
    "Zigbee & Thread", "LoRa & LoRaWAN", "MQTT Protocol", "CoAP Protocol",
    "Modbus (RTU & TCP)", "1-Wire Protocol", "SDIO Interface",
    # RTOS
    "RTOS Fundamentals", "FreeRTOS Task Management", "FreeRTOS Queues",
    "FreeRTOS Semaphores & Mutexes", "FreeRTOS Timers", "FreeRTOS Event Groups",
    "Task Scheduling Algorithms", "Priority Inversion & Inheritance",
    "Context Switching", "Inter-Task Communication", "Memory Management in RTOS",
    "Zephyr RTOS", "RT-Thread", "ThreadX", "Contiki-NG",
    # IoT
    "IoT System Architecture", "Edge Computing", "Cloud Connectivity (AWS IoT, Azure IoT)",
    "Sensor Networks", "Smart Home Systems", "Industrial IoT (IIoT)",
    "OTA (Over-The-Air) Updates", "Power Management for IoT",
    "Low-Power Design Techniques", "Sleep Modes & Wake Sources",
    "Battery Management Systems", "Energy Harvesting",
    # Advanced
    "PCB Design Basics", "Schematic Capture", "PCB Layout Guidelines",
    "EMC (Electromagnetic Compatibility)", "Signal Integrity",
    "Embedded Linux Basics", "Device Drivers (Linux)", "Yocto Project",
    "FPGA Basics for Embedded", "DSP (Digital Signal Processing)",
    "Motor Control (BLDC, Stepper)", "PID Controller Implementation",
    "State Machines in Embedded", "Design Patterns for Embedded",
    "Testing & Debugging Techniques", "JTAG & SWD Debugging",
    "Logic Analyzer Usage", "Oscilloscope Usage", "Unit Testing (Unity, CMock)",
    # Industry
    "Automotive Embedded (AUTOSAR)", "Medical Device Standards (IEC 62304)",
    "Aerospace Embedded (DO-178C)", "Safety-Critical Systems (IEC 61508)",
    "Functional Safety (ISO 26262)", "MISRA C Guidelines",
    "Firmware Development Lifecycle", "Version Control for Embedded",
    "CI/CD for Embedded", "Embedded Security (Secure Boot, TrustZone)",
]

# Difficulty levels
DIFFICULTIES = ["Beginner", "Intermediate", "Advanced", "Expert"]

# ──────────────────────────────────────────────────────────────
# Logging
# ──────────────────────────────────────────────────────────────
def setup_logging(output_dir):
    os.makedirs(output_dir, exist_ok=True)
    log_file = os.path.join(output_dir, f"generation_{datetime.now():%Y%m%d_%H%M%S}.log")
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(message)s",
        handlers=[
            logging.FileHandler(log_file),
            logging.StreamHandler()
        ]
    )
    return logging.getLogger(__name__)

# ──────────────────────────────────────────────────────────────
# LLM Client
# ──────────────────────────────────────────────────────────────
# Quality enforcement: appended to ALL system prompts
QUALITY_RULES = """

CRITICAL RULES FOR YOUR RESPONSE:
- NEVER start with "Sure!", "Of course!", "Certainly!", "Absolutely!", "Great question!" or similar filler.
- NEVER mention that you are an AI, language model, or assistant.
- NEVER include meta-commentary about the prompt or your response.
- ALWAYS use simple, clear language that a beginner can understand.
- ALWAYS explain technical jargon when first introduced.
- ALWAYS include real-world analogies to explain abstract concepts.
- ALWAYS add detailed comments in any code (at least 1 comment per 3 lines).
- ALWAYS explain WHY something works, not just HOW.
- Structure your response with clear headings (##) and bullet points.
- Be thorough, professional, and educational.
"""


class LLMClient:
    def __init__(self, port=8000):
        self.client = OpenAI(
            base_url=f"http://localhost:{port}/v1",
            api_key="not-needed"
        )
        # Cache the model name once to avoid listing on every call
        self._model_name = None

    @property
    def model_name(self):
        if self._model_name is None:
            self._model_name = self.client.models.list().data[0].id
            logging.info(f"Detected model: {self._model_name}")
        return self._model_name

    @staticmethod
    def strip_think(text):
        """Remove <think>...</think> blocks from response."""
        import re
        if text and '<think>' in text:
            text = re.sub(r'<think>.*?</think>', '', text, flags=re.DOTALL).strip()
        return text

    def generate(self, system_prompt, user_prompt, max_tokens=4096, temperature=0.8):
        """Generate a response. System prompt gets quality rules appended.
        Only user + assistant messages are saved to training data (NOT system prompt).
        Thinking mode is DISABLED to save ~40% tokens and produce cleaner output."""
        try:
            # Append quality rules to system prompt (NOT saved in training data)
            enhanced_system = system_prompt + QUALITY_RULES
            response = self.client.chat.completions.create(
                model=self.model_name,
                messages=[
                    {"role": "system", "content": enhanced_system},
                    {"role": "user", "content": user_prompt}
                ],
                max_tokens=max_tokens,
                temperature=temperature,
                top_p=0.9,
                extra_body={
                    "chat_template_kwargs": {"enable_thinking": False}
                },
            )
            result = response.choices[0].message.content.strip()
            # Safety net: strip any residual think tags
            return self.strip_think(result)
        except Exception as e:
            logging.error(f"Generation error: {e}")
            return None

# ──────────────────────────────────────────────────────────────
# Dataset Writer (thread-safe, append-mode)
# ──────────────────────────────────────────────────────────────
class DatasetWriter:
    def __init__(self, output_dir, category_name):
        self.filepath = os.path.join(output_dir, f"{category_name}.jsonl")
        self.count = 0
        self.seen_hashes = set()
        # Load existing if resuming
        if os.path.exists(self.filepath):
            with open(self.filepath, 'r', encoding='utf-8') as f:
                for line in f:
                    self.count += 1
                    try:
                        data = json.loads(line)
                        h = hashlib.md5(json.dumps(data, sort_keys=True).encode()).hexdigest()
                        self.seen_hashes.add(h)
                    except:
                        pass
            logging.info(f"Resuming {category_name}: {self.count} existing examples")

    def write(self, user_content, assistant_content):
        """Write a single training example in ChatML format. Returns True if written."""
        if not assistant_content or len(assistant_content.strip()) < 50:
            return False

        example = {
            "messages": [
                {"role": "user", "content": user_content},
                {"role": "assistant", "content": assistant_content}
            ]
        }

        # Dedup check
        h = hashlib.md5(json.dumps(example, sort_keys=True).encode()).hexdigest()
        if h in self.seen_hashes:
            return False
        self.seen_hashes.add(h)

        with open(self.filepath, 'a', encoding='utf-8') as f:
            f.write(json.dumps(example, ensure_ascii=False) + '\n')
        self.count += 1
        return True


# ──────────────────────────────────────────────────────────────
# Concurrent Batch Generation Helper
# ──────────────────────────────────────────────────────────────
BATCH_SIZE = 32  # Concurrent requests to vLLM

def batch_generate(llm, writer, prompt_builder_fn, target_count, category_label, logger, max_tokens=4096, temperature=0.85):
    """High-throughput generation: submits BATCH_SIZE requests concurrently.
    vLLM batches them on GPU for 5-10x throughput vs sequential."""
    log = logger or logging.getLogger(__name__)
    log.info(f"  [{category_label}] Batch mode: {BATCH_SIZE} concurrent requests")

    while writer.count < target_count:
        # Build a batch of prompts
        batch = []
        for _ in range(min(BATCH_SIZE, target_count - writer.count)):
            sys_prompt, usr_prompt = prompt_builder_fn()
            batch.append((sys_prompt, usr_prompt))

        # Submit all concurrently
        with ThreadPoolExecutor(max_workers=BATCH_SIZE) as executor:
            futures = {}
            for sys_p, usr_p in batch:
                f = executor.submit(llm.generate, sys_p, usr_p, max_tokens, temperature)
                futures[f] = usr_p

            for future in as_completed(futures):
                usr_p = futures[future]
                try:
                    response = future.result()
                    writer.write(usr_p, response)
                except Exception as e:
                    log.error(f"Batch item failed: {e}")

        if writer.count % 100 < BATCH_SIZE:
            log.info(f"  [{category_label}] Progress: {writer.count}/{target_count}")


# ══════════════════════════════════════════════════════════════
# CATEGORY 1: Assessment Reports (2,000 examples)
# ══════════════════════════════════════════════════════════════
def generate_assessment_reports(llm, writer, target_count=2000, logger=None):
    """Generate assessment analysis and report examples."""
    log = logger or logging.getLogger(__name__)
    log.info(f"[CAT-1] Generating Assessment Reports (target: {target_count})...")

    # Diverse assessment scenarios
    score_ranges = [
        ("Complete Beginner", {t: random.randint(0, 30) for t in random.sample(ES_TOPICS[:40], 8)}),
        ("Beginner", {t: random.randint(15, 50) for t in random.sample(ES_TOPICS[:60], 8)}),
        ("Intermediate", {t: random.randint(30, 75) for t in random.sample(ES_TOPICS, 8)}),
        ("Advanced", {t: random.randint(50, 95) for t in random.sample(ES_TOPICS, 8)}),
        ("Mixed", {t: random.randint(10, 100) for t in random.sample(ES_TOPICS, 8)}),
    ]

    prompt_templates = [
        "Analyze this embedded systems assessment and generate a comprehensive report:\n\nStudent Assessment Results:\n{scores}\n\nGenerate a detailed assessment report covering: overall skill level, strengths, weaknesses, specific skill gaps, recommended focus areas, and a personalized improvement plan.",
        "Based on the following embedded systems test scores, create a hyper-detailed assessment report:\n\n{scores}\n\nInclude: percentage breakdown per category, skill gap analysis, comparison to industry expectations, learning priority ranking, and specific resources/projects to improve weak areas.",
        "A student completed an embedded systems skills assessment. Here are their results:\n\n{scores}\n\nWrite a professional, encouraging, and detailed assessment report. Include specific examples of what they need to learn, practice projects they should try, and a realistic timeline to reach job-ready status.",
        "Review these embedded systems assessment scores and provide a thorough analysis:\n\n{scores}\n\nYour report should include: 1) Overall evaluation, 2) Category-by-category breakdown, 3) Knowledge gap identification, 4) Strengths to leverage, 5) Step-by-step improvement roadmap, 6) Estimated time to reach each milestone.",
    ]

    system_prompt = "You are an expert embedded systems educator. Generate detailed, accurate, and encouraging assessment reports. Use specific examples, real-world references, and actionable recommendations. Format your response clearly with headings and bullet points."

    def build_prompt():
        level_name, scores_template = random.choice(score_ranges)
        num_topics = random.randint(6, 12)
        topics = random.sample(ES_TOPICS, num_topics)
        scores = {t: random.randint(
            max(0, scores_template.get(list(scores_template.keys())[0], 50) - 20),
            min(100, scores_template.get(list(scores_template.keys())[0], 50) + 20)
        ) for t in topics}
        scores_text = "\n".join([f"- {topic}: {score}%" for topic, score in scores.items()])
        user_prompt = random.choice(prompt_templates).format(scores=scores_text)
        return system_prompt, user_prompt

    batch_generate(llm, writer, build_prompt, target_count, "CAT-1", log)


# ══════════════════════════════════════════════════════════════
# CATEGORY 2: Learning Path Generation (1,500 examples)
# ══════════════════════════════════════════════════════════════
def generate_learning_paths(llm, writer, target_count=1500, logger=None):
    """Generate personalized learning path examples."""
    log = logger or logging.getLogger(__name__)
    log.info(f"[CAT-2] Generating Learning Paths (target: {target_count})...")

    prompt_templates = [
        "Based on this assessment:\n\nWeak Areas: {weak}\nStrong Areas: {strong}\nOverall Level: {level}\n\nGenerate a personalized 40-stage learning path. Each stage should have: id (1-40), title, description, topics (list), difficulty (Beginner/Intermediate/Advanced/Expert), estimated_hours, and xp_reward. Order stages from easiest to hardest, focusing more stages on weak areas.",
        "Create a customized embedded systems learning curriculum for a student with these results:\n\nStrengths: {strong}\nWeaknesses: {weak}\nCurrent Level: {level}\n\nDesign exactly 40 learning stages. Each stage must include a unique title, 2-4 specific topics, difficulty level, and XP reward (10-100). The path should start with foundational topics the student is weak in and progress to advanced topics, while reinforcing strong areas along the way.",
        "Design a 40-stage embedded systems mastery path for this student profile:\n\nAreas needing improvement: {weak}\nAreas of competence: {strong}\nSkill level: {level}\n\nFor each stage, provide: stage number, catchy title, detailed topic list, difficulty rating, time estimate, XP reward, and prerequisite stages. Make it feel like a game progression — each stage unlocking new knowledge.",
    ]

    system_prompt = "You are an expert embedded systems curriculum designer. Generate comprehensive, well-structured 40-stage learning paths. Each stage must have clear, actionable topics. Order by difficulty progression. Output valid JSON array format."

    def build_prompt():
        all_topics = random.sample(ES_TOPICS, 16)
        weak = all_topics[:random.randint(4, 8)]
        strong = all_topics[8:8+random.randint(3, 6)]
        level = random.choice(["Complete Beginner", "Beginner", "Lower Intermediate",
                               "Intermediate", "Upper Intermediate", "Advanced"])
        weak_text = ", ".join(weak)
        strong_text = ", ".join(strong)
        user_prompt = random.choice(prompt_templates).format(
            weak=weak_text, strong=strong_text, level=level
        )
        return system_prompt, user_prompt

    batch_generate(llm, writer, build_prompt, target_count, "CAT-2", log, max_tokens=7500, temperature=0.8)


# ══════════════════════════════════════════════════════════════
# CATEGORY 3: Stage Content — 4-Part (8,000 examples)
# ══════════════════════════════════════════════════════════════
def generate_stage_content(llm, writer, target_count=8000, logger=None):
    """Generate 4-part educational content per stage."""
    log = logger or logging.getLogger(__name__)
    log.info(f"[CAT-3] Generating Stage Content (target: {target_count})...")

    part_prompts = {
        "theory": [
            "Generate detailed learning content for the stage: '{topic}'\nTopics to cover: {subtopics}\nDifficulty: {difficulty}\n\nWrite a comprehensive theory section (1500-2000 words) with:\n- Clear introduction with real-world analogy\n- Detailed explanation of each concept\n- How it works at the hardware/register level\n- Practical real-world applications\n- Diagrams described in text\n- Key formulas or calculations if applicable\n\nWrite for a {difficulty} level student. Use simple language with technical accuracy.",
            "Create university-level educational content about '{topic}'.\nSubtopics: {subtopics}\nLevel: {difficulty}\n\nWrite a thorough 1500-2000 word explanation covering:\n1. What it is and why it matters\n2. Core concepts with analogies\n3. Technical deep-dive with register-level details\n4. Step-by-step working mechanism\n5. Common configurations and modes\n6. Real-world example applications\n7. Connection to other embedded topics\n\nMake it engaging, detailed, and easy to understand for a {difficulty} student.",
        ],
        "key_points": [
            "Generate 8 key points summarizing the most important concepts about '{topic}'.\nTopics: {subtopics}\nDifficulty: {difficulty}\n\nEach key point should:\n- Have a clear title\n- Include a 2-3 sentence explanation\n- Include a practical tip or gotcha\n- Be immediately useful for a {difficulty} level student",
            "Create a summary of 8 essential takeaways about '{topic}' ({subtopics}).\nLevel: {difficulty}\n\nFor each point include: title, concise explanation, why it matters, and a practical example or tip. These should capture the core knowledge a student needs.",
        ],
        "code_example": [
            "Write a complete, well-commented code example demonstrating '{topic}'.\nFocus on: {subtopics}\nDifficulty: {difficulty}\n\nRequirements:\n- {code_lines} lines of C code (or appropriate language)\n- Detailed line-by-line comments explaining every step\n- Register-level operations where relevant\n- Proper error handling\n- A complete, compilable/runnable example\n- After the code, provide a detailed walkthrough explaining what each section does",
            "Create a practical embedded systems code example for '{topic}' covering {subtopics}.\nLevel: {difficulty}\n\nProvide:\n1. Problem statement (what we're building)\n2. Complete source code ({code_lines} lines) with extensive comments\n3. Step-by-step explanation of the code\n4. Expected output/behavior\n5. How to test/verify it works\n6. Common modifications students can try",
        ],
        "tips_and_mistakes": [
            "For the topic '{topic}' ({subtopics}), generate:\n\n1. Top 5 Common Mistakes that {difficulty} students make, with:\n   - What the mistake is\n   - Why it happens\n   - How to fix it\n   - Code example showing wrong vs correct approach\n\n2. 5 Pro Tips for writing better embedded code related to this topic\n\n3. A Mini Challenge: a small exercise the student can try on their own (with hidden solution)",
            "List the most important pitfalls and best practices for '{topic}' ({subtopics}) at the {difficulty} level.\n\nInclude:\n- 5 common errors with debugging tips and corrected code\n- 5 professional tips used in industry\n- 1 hands-on mini challenge with problem statement, hints, and solution\n- Real-world debugging scenarios",
        ],
    }

    system_prompt = "You are an expert embedded systems professor creating the highest quality educational content. Your explanations are thorough, accurate, and easy to understand. You always use real-world examples and practical code. You explain complex concepts with simple analogies."

    def build_prompt():
        topic = random.choice(ES_TOPICS)
        related = [t for t in ES_TOPICS if t != topic]
        subtopics = ", ".join(random.sample(related[:20], random.randint(2, 4)))
        difficulty = random.choice(DIFFICULTIES)
        code_lines_map = {"Beginner": "15-25", "Intermediate": "25-45",
                          "Advanced": "40-70", "Expert": "60-100"}
        code_lines = code_lines_map.get(difficulty, "25-45")
        part = random.choice(list(part_prompts.keys()))
        template = random.choice(part_prompts[part])
        user_prompt = template.format(
            topic=topic, subtopics=subtopics,
            difficulty=difficulty, code_lines=code_lines
        )
        return system_prompt, user_prompt

    batch_generate(llm, writer, build_prompt, target_count, "CAT-3", log, max_tokens=6144)


# ══════════════════════════════════════════════════════════════
# CATEGORY 4: Quiz Generation (5,000 examples)
# ══════════════════════════════════════════════════════════════
def generate_quizzes(llm, writer, target_count=5000, logger=None):
    """Generate MCQ quiz questions with detailed explanations."""
    log = logger or logging.getLogger(__name__)
    log.info(f"[CAT-4] Generating Quizzes (target: {target_count})...")

    prompt_templates = [
        "Generate {num_q} multiple-choice quiz questions about '{topic}' for a {difficulty} level student.\n\nFor each question:\n- Write a clear, specific question\n- Provide exactly 4 options (A, B, C, D)\n- Indicate the correct answer\n- Write a detailed explanation (3-5 sentences) of WHY the correct answer is right and why each wrong answer is wrong\n- Include code snippets in questions where relevant",
        "Create {num_q} embedded systems quiz questions on '{topic}' at {difficulty} level.\n\nRequirements:\n- Questions should test understanding, not just memorization\n- Include at least 1 code-based question\n- Include at least 1 scenario/application question\n- Each question needs: question text, 4 options, correct answer, and thorough explanation\n- Make distractors (wrong options) realistic — common misconceptions",
        "Write {num_q} challenging but fair quiz questions about '{topic}' for {difficulty} students.\n\nFormat each question as:\nQ: [question]\nA) [option]\nB) [option]\nC) [option]\nD) [option]\nCorrect: [letter]\nExplanation: [detailed explanation covering why correct answer is right and others are wrong]",
    ]

    system_prompt = "You are an expert embedded systems quiz creator. Generate accurate, well-crafted multiple-choice questions that test real understanding. Include code-based questions where appropriate. Every explanation must be detailed and educational."

    def build_prompt():
        topic = random.choice(ES_TOPICS)
        difficulty = random.choice(DIFFICULTIES)
        num_q = random.choice([3, 5, 7, 10])
        user_prompt = random.choice(prompt_templates).format(
            topic=topic, difficulty=difficulty, num_q=num_q
        )
        return system_prompt, user_prompt

    batch_generate(llm, writer, build_prompt, target_count, "CAT-4", log)


# ══════════════════════════════════════════════════════════════
# CATEGORY 5: Flashcard Generation (4,000 examples)
# ══════════════════════════════════════════════════════════════
def generate_flashcards(llm, writer, target_count=4000, logger=None):
    """Generate front/back flashcards for quick review."""
    log = logger or logging.getLogger(__name__)
    log.info(f"[CAT-5] Generating Flashcards (target: {target_count})...")

    prompt_templates = [
        "Generate {num_cards} flashcards about '{topic}' for embedded systems study.\n\nFor each flashcard:\nFront: [A clear, specific question or term]\nBack: [A concise but complete answer (2-4 sentences) with key details, examples, or code snippets]\n\nMake cards progressively harder. Include a mix of definition cards, concept cards, and code/application cards.",
        "Create {num_cards} study flashcards covering '{topic}' in embedded systems.\n\nEach card should have:\n- Front: A focused question (what, why, how, when to use)\n- Back: Direct answer with practical example or code snippet\n\nInclude cards about: key concepts, common register values, code patterns, debugging tips, and best practices.",
        "Design {num_cards} embedded systems flashcards for '{topic}'.\n\nFormat:\nCard N:\nQ: [question]\nA: [answer]\n\nMix of types: definitions, comparisons, code output prediction, register configurations, protocol details, and troubleshooting scenarios.",
    ]

    system_prompt = "You are creating high-quality study flashcards for embedded systems students. Each card should test one specific concept. Answers should be concise but complete. Include code snippets where helpful."

    def build_prompt():
        topic = random.choice(ES_TOPICS)
        num_cards = random.choice([5, 8, 10, 12, 15])
        user_prompt = random.choice(prompt_templates).format(
            topic=topic, num_cards=num_cards
        )
        return system_prompt, user_prompt

    batch_generate(llm, writer, build_prompt, target_count, "CAT-5", log, temperature=0.8)


# ══════════════════════════════════════════════════════════════
# CATEGORY 6: Q&A / Doubt Resolution (6,000 examples)
# ══════════════════════════════════════════════════════════════
def generate_qa_doubts(llm, writer, target_count=6000, logger=None):
    """Generate Q&A pairs simulating student doubts and expert answers."""
    log = logger or logging.getLogger(__name__)
    log.info(f"[CAT-6] Generating Q&A Doubts (target: {target_count})...")

    # Diverse question styles students ask
    question_templates = [
        # Confusion questions
        "I'm confused about {topic}. Can you explain it simply with a real-world analogy?",
        "What exactly is the difference between {topic1} and {topic2}? I keep mixing them up.",
        "Why do we need {topic} in embedded systems? What problem does it solve?",
        "I don't understand how {topic} works at the register level. Can you walk me through it step by step?",
        # How-to questions
        "How do I implement {topic} on an STM32F4? Can you show me the code?",
        "How do I configure {topic} on ESP32 using ESP-IDF? Show me a complete example.",
        "What's the best way to debug {topic} issues? What tools should I use?",
        "How do I optimize {topic} for low power consumption?",
        # Why questions
        "Why does my {topic} code not work? Here's what I have:\n```c\n// student's buggy code placeholder\nint main() {{\n    // TODO: {topic} initialization\n    while(1) {{ }}\n}}\n```\nWhat's wrong?",
        "Why is {topic} important for job interviews? What questions do companies ask?",
        "Why would I choose {topic1} over {topic2} in a real project? Give me a concrete scenario.",
        # Scenario questions
        "I'm building a {project} using {mcu}. How should I implement {topic}?",
        "In my project, I need to handle {topic} with low latency. What's the best approach?",
        "I got an error when trying to use {topic} with {topic2}. How do I resolve this?",
        # Deep-dive questions
        "Can you explain the complete data flow when {topic} sends/receives data?",
        "What happens at the hardware level during {topic}? Explain the timing diagram.",
        "How does the CPU handle {topic} internally? Walk me through the pipeline.",
        # Comparison questions
        "Compare {topic1} vs {topic2} for embedded systems. Which should I learn first?",
        "{topic1} or {topic2} — which is better for {project}? Pros and cons?",
        # Career questions
        "How important is {topic} for getting a job in embedded systems?",
        "What level of {topic} knowledge do employers expect from a junior embedded engineer?",
    ]

    projects = [
        "a weather station", "a home automation system", "a drone controller",
        "an industrial sensor node", "a smart watch", "a motor controller",
        "a CAN-based automotive ECU", "a Bluetooth fitness tracker",
        "a LoRa-based agricultural monitor", "a robotic arm controller",
        "an IoT gateway", "a PLC replacement", "a barcode scanner",
        "a medical pulse oximeter", "a USB oscilloscope",
    ]

    mcus = ["STM32F4", "ESP32", "Arduino Uno", "Raspberry Pi Pico",
            "STM32H7", "nRF52840", "MSP430", "PIC18F", "ATmega328P"]

    system_prompt = "You are a friendly, expert embedded systems tutor. Answer student questions with clear, detailed explanations. Always include: 1) Simple analogy, 2) Technical explanation, 3) Code example when relevant, 4) Common pitfalls to avoid, 5) Further learning suggestions. Use simple language but maintain technical accuracy."

    def build_prompt():
        topic = random.choice(ES_TOPICS)
        topic1, topic2 = random.sample(ES_TOPICS, 2)
        project = random.choice(projects)
        mcu = random.choice(mcus)
        template = random.choice(question_templates)
        try:
            user_prompt = template.format(
                topic=topic, topic1=topic1, topic2=topic2,
                project=project, mcu=mcu
            )
        except (KeyError, IndexError):
            user_prompt = f"Explain {topic} in embedded systems in detail with examples and code."
        return system_prompt, user_prompt

    batch_generate(llm, writer, build_prompt, target_count, "CAT-6", log)


# ══════════════════════════════════════════════════════════════
# CATEGORY 7: Practice/Challenge Questions (2,000 examples)
# ══════════════════════════════════════════════════════════════
def generate_practice_challenges(llm, writer, target_count=2000, logger=None):
    """Generate coding challenges and practice problems with solutions."""
    log = logger or logging.getLogger(__name__)
    log.info(f"[CAT-7] Generating Practice Challenges (target: {target_count})...")

    prompt_templates = [
        "Create a hands-on coding challenge about '{topic}' for {difficulty} embedded systems students.\n\nInclude:\n1. Challenge Title\n2. Problem Statement (clear, specific)\n3. Requirements & Constraints\n4. Hints (3 progressive hints)\n5. Complete Solution with extensive comments\n6. Explanation of the solution approach\n7. How to test/verify the solution\n8. Bonus: extension ideas to make it harder",
        "Design a practical embedded systems exercise about '{topic}' at {difficulty} level.\n\nProvide:\n- Scenario (real-world context)\n- Task description\n- Expected inputs/outputs\n- Skeleton code (with TODO comments)\n- Complete reference solution\n- Testing strategy\n- Common mistakes to watch for",
        "Write a {difficulty}-level embedded systems mini-project brief about '{topic}'.\n\nInclude:\n1. Project overview and objectives\n2. Hardware requirements (or simulation setup)\n3. Step-by-step implementation guide\n4. Complete code with line-by-line comments\n5. Debugging tips\n6. Expected results/output\n7. Assessment criteria (what makes a good solution)",
    ]

    system_prompt = "You are an expert embedded systems instructor creating hands-on practice challenges. Make challenges realistic, achievable, and educational. Always provide complete working solutions with thorough explanations. Code should compile and run on real hardware or simulators."

    def build_prompt():
        topic = random.choice(ES_TOPICS)
        difficulty = random.choice(DIFFICULTIES)
        user_prompt = random.choice(prompt_templates).format(
            topic=topic, difficulty=difficulty
        )
        return system_prompt, user_prompt

    batch_generate(llm, writer, build_prompt, target_count, "CAT-7", log, max_tokens=6144)


# ══════════════════════════════════════════════════════════════
# CATEGORY 8: Interview Prep & Career (1,500 examples)
# ══════════════════════════════════════════════════════════════
def generate_interview_career(llm, writer, target_count=1500, logger=None):
    """Generate interview Q&A and career guidance content."""
    log = logger or logging.getLogger(__name__)
    log.info(f"[CAT-8] Generating Interview & Career (target: {target_count})...")

    companies = [
        "Texas Instruments", "Qualcomm", "Intel", "Broadcom", "NXP",
        "STMicroelectronics", "Microchip", "Infineon", "Renesas",
        "Bosch", "Continental", "NVIDIA (Embedded)", "AMD (Embedded)",
        "Samsung Semiconductor", "MediaTek", "Analog Devices",
        "Tesla (Firmware)", "Apple (Hardware)", "Google (Embedded)",
        "Amazon (Lab126)", "SpaceX (Avionics)", "ARM",
    ]

    prompt_templates = [
        "List the top {num_q} embedded systems interview questions about '{topic}' asked at companies like {company}.\n\nFor each question:\n- The exact question as asked\n- A comprehensive model answer (what the interviewer expects)\n- Follow-up questions they might ask\n- Tips on how to answer confidently",
        "Prepare me for an embedded systems interview at {company}. Focus on '{topic}'.\n\nProvide:\n- {num_q} likely technical questions with ideal answers\n- What the interviewer is really testing with each question\n- Common mistakes candidates make\n- How to demonstrate practical experience even if you're a fresher",
        "What should an embedded systems engineer know about '{topic}' to pass a technical interview?\n\nCover:\n- Key concepts the interviewer will expect you to know\n- Whiteboard coding questions with solutions\n- System design questions about {topic}\n- How to relate academic knowledge to industry applications",
        "I'm preparing for an embedded systems role. Create a study guide for '{topic}' interview preparation.\n\nInclude:\n- Must-know theory (bulleted summary)\n- Commonly asked questions with answers\n- Hands-on tasks that might come up in a practical round\n- Resources for deeper study\n- How to frame your answers to impress the interviewer",
        "Give career advice for someone wanting to specialize in '{topic}' in the embedded systems industry.\n\nCover:\n- Job roles that require this skill\n- Salary expectations (entry/mid/senior)\n- Companies hiring for this\n- Portfolio projects to showcase this skill\n- Certifications that help\n- Learning roadmap from beginner to expert",
    ]

    system_prompt = "You are a senior embedded systems hiring manager and career mentor. Provide realistic, detailed interview preparation content. Include actual questions that are asked in interviews, with comprehensive answers. Give honest career advice based on current industry trends."

    def build_prompt():
        topic = random.choice(ES_TOPICS)
        company = random.choice(companies)
        num_q = random.choice([3, 5, 7, 10])
        user_prompt = random.choice(prompt_templates).format(
            topic=topic, company=company, num_q=num_q
        )
        return system_prompt, user_prompt

    batch_generate(llm, writer, build_prompt, target_count, "CAT-8", log)


# ══════════════════════════════════════════════════════════════
# Main Orchestrator
# ══════════════════════════════════════════════════════════════
def main():
    parser = argparse.ArgumentParser(description="Generate training datasets")
    parser.add_argument("--gpu", type=int, required=True, choices=[1, 2],
                        help="GPU ID (1 or 2), determines which categories to generate")
    parser.add_argument("--output-dir", type=str, required=True,
                        help="Output directory for JSONL files")
    parser.add_argument("--port", type=int, default=8000,
                        help="vLLM server port")
    parser.add_argument("--workers", type=int, default=32,
                        help="Number of parallel generation workers")
    args = parser.parse_args()

    logger = setup_logging(args.output_dir)
    logger.info(f"Starting dataset generation on GPU-{args.gpu}")
    logger.info(f"Output dir: {args.output_dir}")

    # ┌─────────────────────────────────────────────────────────┐
    # │ GPU-SPECIFIC RANDOM SEED                                │
    # │ GPU-1 uses seed 1000, GPU-2 uses seed 2000              │
    # │ This ensures both GPUs select different random topics,   │
    # │ different prompt templates, and different combinations   │
    # │ even if they share the same topic pool.                  │
    # │ Combined with category separation (1-4 vs 5-8),          │
    # │ there is ZERO chance of duplicate content.               │
    # └─────────────────────────────────────────────────────────┘
    gpu_seed = args.gpu * 1000
    random.seed(gpu_seed)
    logger.info(f"Random seed set to {gpu_seed} for GPU-{args.gpu}")

    # Shuffle topic list differently per GPU for extra diversity
    random.shuffle(ES_TOPICS)
    logger.info(f"Topic pool shuffled with GPU-specific seed")

    llm = LLMClient(port=args.port)

    # Test connection
    logger.info("Testing vLLM connection...")
    test = llm.generate("You are a test.", "Say 'OK' if you're working.", max_tokens=10)
    if not test:
        logger.error("FATAL: Cannot connect to vLLM server!")
        sys.exit(1)
    logger.info(f"vLLM connection OK: {test[:50]}")

    start_time = time.time()

    if args.gpu == 1:
        # GPU-1: Categories 1-4 (16,500 examples)
        categories = [
            ("cat1_assessment_reports", generate_assessment_reports, 2000),
            ("cat2_learning_paths", generate_learning_paths, 1500),
            ("cat3_stage_content", generate_stage_content, 8000),
            ("cat4_quizzes", generate_quizzes, 5000),
        ]
    else:
        # GPU-2: Categories 5-8 (13,500 examples)
        categories = [
            ("cat5_flashcards", generate_flashcards, 4000),
            ("cat6_qa_doubts", generate_qa_doubts, 6000),
            ("cat7_practice_challenges", generate_practice_challenges, 2000),
            ("cat8_interview_career", generate_interview_career, 1500),
        ]

    total_generated = 0
    for cat_name, gen_func, target in categories:
        writer = DatasetWriter(args.output_dir, cat_name)
        remaining = target - writer.count
        if remaining <= 0:
            logger.info(f"✅ {cat_name} already complete ({writer.count}/{target})")
            total_generated += writer.count
            continue

        logger.info(f"Generating {cat_name}: {writer.count}/{target} (need {remaining} more)")
        gen_func(llm, writer, target_count=target, logger=logger)
        total_generated += writer.count
        logger.info(f"✅ {cat_name} complete: {writer.count} examples")

    elapsed = time.time() - start_time
    hours = elapsed / 3600
    logger.info(f"\n{'='*60}")
    logger.info(f"  GPU-{args.gpu} Generation Complete!")
    logger.info(f"  Total examples: {total_generated}")
    logger.info(f"  Time: {hours:.1f} hours ({elapsed:.0f} seconds)")
    logger.info(f"  Rate: {total_generated/hours:.0f} examples/hour")
    logger.info(f"{'='*60}")

    # Print file sizes
    logger.info("\nDataset files:")
    for f in sorted(os.listdir(args.output_dir)):
        if f.endswith('.jsonl'):
            path = os.path.join(args.output_dir, f)
            size_mb = os.path.getsize(path) / (1024*1024)
            with open(path, 'r') as fh:
                lines = sum(1 for _ in fh)
            logger.info(f"  {f}: {lines} examples, {size_mb:.1f} MB")


if __name__ == "__main__":
    main()
