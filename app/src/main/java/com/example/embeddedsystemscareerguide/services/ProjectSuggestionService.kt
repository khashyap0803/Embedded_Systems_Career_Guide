package com.example.embeddedsystemscareerguide.services

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ProjectSuggestionService - AI-Powered Project Recommendations
 * 
 * Generates personalized project suggestions based on completed stages
 * and skill level. Provides step-by-step guidance and resources.
 * 
 * @author Embedded Systems Career Guide
 * @version 2.0
 */
class ProjectSuggestionService(private val context: Context) {

    companion object {
        private const val TAG = "ProjectSuggestionService"
        
        @Volatile
        private var instance: ProjectSuggestionService? = null
        
        fun getInstance(context: Context): ProjectSuggestionService {
            return instance ?: synchronized(this) {
                instance ?: ProjectSuggestionService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val geminiService = GeminiServiceV2.getInstance(context)
    private val firestoreManager = FirestoreManager.getInstance(context)
    private val gson = Gson()

    /**
     * Callback interface for project suggestions
     */
    interface ProjectCallback {
        fun onProgress(message: String)
        fun onSuccess(projects: List<Project>)
        fun onError(error: String)
    }

    /**
     * Generate project suggestions based on completed topics
     */
    suspend fun generateProjectSuggestions(
        callback: ProjectCallback
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generating project suggestions")
            callback.onProgress("Analyzing your skills...")

            // Get completed stages to understand skill level
            val stages = firestoreManager.getPersonalizedStages().getOrNull() ?: emptyList()
            val completedTopics = stages.filter { it.isCompleted }.map { it.title }
            
            if (completedTopics.isEmpty()) {
                callback.onSuccess(createBeginnerProjects())
                return@withContext
            }

            callback.onProgress("Finding projects matching your skills...")

            val prompt = GeminiServiceV2.PromptTemplates.projectSuggestions(
                completedStages = completedTopics,
                skillLevel = determineSkillLevel(stages)
            )

            val result = geminiService.generateContent(prompt, maxOutputTokens = 4096)
            
            if (result.isFailure) {
                Log.e(TAG, "API call failed: ${result.exceptionOrNull()?.message}")
                callback.onSuccess(createFallbackProjects(completedTopics))
                return@withContext
            }

            callback.onProgress("Preparing project details...")
            
            val responseText = result.getOrNull() ?: ""
            val projects = parseProjectsFromResponse(responseText)
            
            if (projects.isEmpty()) {
                callback.onSuccess(createFallbackProjects(completedTopics))
            } else {
                // Save to Firestore
                firestoreManager.saveProjects(projects)
                callback.onSuccess(projects)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error generating projects", e)
            callback.onError("Failed to generate projects: ${e.message}")
        }
    }

    /**
     * Determine skill level from completed stages
     */
    private fun determineSkillLevel(stages: List<PersonalizedStage>): String {
        val completedCount = stages.count { it.isCompleted }
        val totalStages = stages.size
        val percentage = if (totalStages > 0) (completedCount * 100) / totalStages else 0
        
        return when {
            percentage < 25 -> "beginner"
            percentage < 50 -> "intermediate"
            percentage < 75 -> "advanced"
            else -> "expert"
        }
    }

    /**
     * Parse projects from AI response
     */
    private fun parseProjectsFromResponse(response: String): List<Project> {
        return try {
            val cleanedResponse = response
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val jsonObject = gson.fromJson(cleanedResponse, JsonObject::class.java)
            val projectsArray = jsonObject.getAsJsonArray("projects")
            
            val projects = mutableListOf<Project>()
            
            projectsArray?.forEachIndexed { index, element ->
                try {
                    val obj = element.asJsonObject
                    projects.add(Project(
                        id = index + 1,
                        title = obj.get("title")?.asString ?: "Project ${index + 1}",
                        description = obj.get("description")?.asString ?: "",
                        difficulty = obj.get("difficulty")?.asString ?: "intermediate",
                        estimatedHours = obj.get("estimatedHours")?.asInt ?: 10,
                        skills = obj.getAsJsonArray("skills")?.map { it.asString } ?: emptyList(),
                        components = obj.getAsJsonArray("components")?.map { it.asString } ?: emptyList(),
                        learningOutcomes = obj.getAsJsonArray("learningOutcomes")?.map { it.asString } ?: emptyList(),
                        steps = obj.getAsJsonArray("steps")?.map { it.asString } ?: emptyList(),
                        status = "not_started"
                    ))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse project: ${e.message}")
                }
            }
            
            projects
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse projects response", e)
            emptyList()
        }
    }

    /**
     * Create beginner projects for new users
     */
    private fun createBeginnerProjects(): List<Project> {
        return listOf(
            Project(
                id = 1,
                title = "LED Blinker",
                description = "The 'Hello World' of embedded systems. Learn to control an LED using GPIO and understand the basics of microcontroller programming.",
                difficulty = "beginner",
                estimatedHours = 2,
                skills = listOf("GPIO", "C Programming", "Basic Electronics"),
                components = listOf("Arduino/STM32 Board", "LED", "330Ω Resistor", "Breadboard", "Jumper Wires"),
                learningOutcomes = listOf("GPIO configuration", "Delay functions", "Basic circuit design"),
                steps = listOf(
                    "Set up your development environment (Arduino IDE or STM32CubeIDE)",
                    "Connect LED with resistor to GPIO pin",
                    "Configure GPIO pin as output",
                    "Write code to toggle LED state",
                    "Add delay between toggles",
                    "Experiment with different blink patterns"
                ),
                status = "not_started"
            ),
            Project(
                id = 2,
                title = "Button Controlled LED",
                description = "Learn to read digital inputs and respond to user interaction. Implement debouncing for reliable button detection.",
                difficulty = "beginner",
                estimatedHours = 3,
                skills = listOf("GPIO Input", "Debouncing", "State Machines"),
                components = listOf("Arduino/STM32 Board", "Push Button", "LED", "10kΩ Resistor", "330Ω Resistor", "Breadboard"),
                learningOutcomes = listOf("Input reading", "Debouncing techniques", "Simple state management"),
                steps = listOf(
                    "Connect button with pull-down resistor",
                    "Connect LED with current-limiting resistor",
                    "Configure input and output pins",
                    "Read button state in main loop",
                    "Implement software debouncing",
                    "Toggle LED on button press",
                    "Add different modes (toggle vs hold)"
                ),
                status = "not_started"
            ),
            Project(
                id = 3,
                title = "Serial Communication Logger",
                description = "Build a simple serial logger that sends sensor data or debug messages to your computer via UART.",
                difficulty = "beginner",
                estimatedHours = 4,
                skills = listOf("UART", "Serial Communication", "Debugging"),
                components = listOf("Arduino/STM32 Board", "USB Cable", "Optional: Temperature Sensor"),
                learningOutcomes = listOf("UART configuration", "Serial protocol", "Debugging techniques"),
                steps = listOf(
                    "Configure UART peripheral",
                    "Set baud rate (9600 or 115200)",
                    "Implement printf-style output function",
                    "Print formatted messages",
                    "Read and display sensor values",
                    "Open serial terminal on PC",
                    "Experiment with different data formats"
                ),
                status = "not_started"
            )
        )
    }

    /**
     * Create fallback projects based on topics
     */
    private fun createFallbackProjects(completedTopics: List<String>): List<Project> {
        val projects = mutableListOf<Project>()
        
        // Intermediate projects
        projects.add(Project(
            id = 1,
            title = "Digital Thermometer with LCD",
            description = "Build a temperature monitoring system with digital display. Learn to interface sensors and LCD modules.",
            difficulty = "intermediate",
            estimatedHours = 8,
            skills = listOf("ADC", "I2C", "LCD Interfacing", "Sensor Integration"),
            components = listOf("MCU Board", "LM35/DS18B20 Temperature Sensor", "16x2 LCD", "I2C Adapter", "Breadboard"),
            learningOutcomes = listOf("ADC conversion", "I2C communication", "Display interfacing"),
            steps = listOf(
                "Connect temperature sensor to ADC pin",
                "Configure ADC peripheral",
                "Initialize I2C for LCD",
                "Read temperature values",
                "Convert ADC reading to Celsius",
                "Display on LCD with formatting",
                "Add min/max tracking"
            ),
            status = "not_started"
        ))
        
        projects.add(Project(
            id = 2,
            title = "PWM Motor Speed Controller",
            description = "Control DC motor speed using PWM. Learn timer configuration and motor driver interfacing.",
            difficulty = "intermediate",
            estimatedHours = 6,
            skills = listOf("PWM", "Timers", "Motor Drivers"),
            components = listOf("MCU Board", "DC Motor", "L293D/L298N Motor Driver", "Potentiometer", "Power Supply"),
            learningOutcomes = listOf("PWM generation", "Timer configuration", "Motor control"),
            steps = listOf(
                "Configure timer for PWM output",
                "Connect motor driver to MCU",
                "Wire motor to driver outputs",
                "Read potentiometer with ADC",
                "Map ADC value to PWM duty cycle",
                "Implement soft start/stop",
                "Add direction control"
            ),
            status = "not_started"
        ))
        
        projects.add(Project(
            id = 3,
            title = "Real-Time Clock with Alarm",
            description = "Build a digital clock with alarm functionality using an RTC module and interrupts.",
            difficulty = "intermediate",
            estimatedHours = 10,
            skills = listOf("I2C", "Interrupts", "RTC Modules", "Time Management"),
            components = listOf("MCU Board", "DS3231 RTC Module", "Buzzer", "Push Buttons", "7-Segment or LCD Display"),
            learningOutcomes = listOf("RTC usage", "Interrupt handling", "Time keeping"),
            steps = listOf(
                "Connect RTC module via I2C",
                "Initialize and configure RTC",
                "Read time from RTC",
                "Display on screen",
                "Configure alarm interrupt",
                "Implement alarm functionality",
                "Add buttons for time setting"
            ),
            status = "not_started"
        ))
        
        // Advanced project
        projects.add(Project(
            id = 4,
            title = "Multi-Sensor Dashboard (RTOS)",
            description = "Build a real-time sensor monitoring system using FreeRTOS with multiple concurrent tasks.",
            difficulty = "advanced",
            estimatedHours = 20,
            skills = listOf("RTOS", "Task Management", "Queues", "Multiple Sensors"),
            components = listOf("STM32 Board", "Temperature Sensor", "Light Sensor", "Humidity Sensor", "OLED Display"),
            learningOutcomes = listOf("RTOS concepts", "Multi-tasking", "Inter-task communication"),
            steps = listOf(
                "Set up FreeRTOS project",
                "Create sensor reading tasks",
                "Implement message queues",
                "Create display update task",
                "Add task synchronization",
                "Implement data logging",
                "Add UART debug output"
            ),
            status = "not_started"
        ))
        
        return projects
    }

    /**
     * Mark a project as in progress or completed
     */
    suspend fun updateProjectStatus(
        projectId: Int,
        status: String  // "not_started", "in_progress", "completed"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestoreManager.updateProjectStatus(projectId, status)
            Log.d(TAG, "Updated project $projectId status to $status")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating project status", e)
            Result.failure(e)
        }
    }
}
