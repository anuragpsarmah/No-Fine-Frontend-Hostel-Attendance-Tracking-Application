package com.app.attendance

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var saveButton: Button
    private var username: String = ""
    private var password: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        usernameEditText = findViewById(R.id.username)
        passwordEditText = findViewById(R.id.password)
        saveButton = findViewById(R.id.button)

        // Set background tint color programmatically
        saveButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.white)

        saveButton.setOnClickListener {
            // Get the username and password entered by the user
            username = usernameEditText.text.toString()
            password = passwordEditText.text.toString()

            // Clear focus from EditText fields
            usernameEditText.clearFocus()
            passwordEditText.clearFocus()

            // Check if username or password is empty
            if (username.isEmpty() || password.isEmpty()) {
                // Show a toast message indicating that username or password is empty
                Toast.makeText(this@MainActivity, "Username or password cannot be empty", Toast.LENGTH_SHORT).show()
            } else {
                // For testing purposes, log the username and password
                Log.d("MainActivity", "Username: $username, Password: $password")

                // Show a toast message indicating that username and password are saved
                Toast.makeText(this@MainActivity, "Username and password saved successfully", Toast.LENGTH_SHORT).show()

                // Start the background service
                val backgroundServiceIntent = Intent(this@MainActivity, BackgroundService::class.java).apply {
                    putExtra("username", username)
                    putExtra("password", password)
                }
                startForegroundService(backgroundServiceIntent)
            }
        }
    }


    // Foreground service to handle background tasks
    class BackgroundService : Service() {
        private var time = -1
        private var nextMillis: Long = 1
        private lateinit var notificationManager: NotificationManagerCompat

        private lateinit var handler: Handler

        override fun onCreate() {
            super.onCreate()
            notificationManager = NotificationManagerCompat.from(this)
        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            // Retrieve the username and password from the intent extras
            val username = intent?.getStringExtra("username") ?: ""
            val password = intent?.getStringExtra("password") ?: ""

            // Initialize the handler
            handler = Handler(Looper.getMainLooper())

            // Start the service in the foreground
            startForeground(NOTIFICATION_ID, createNotification())

            // Schedule background tasks
            scheduleBackgroundTasks(username, password)

            return START_STICKY
        }

        override fun onBind(intent: Intent?): IBinder? {
            return null
        }

        private fun createNotification(): Notification {
            // Create a notification channel for Android Oreo and above
            val channelId = createNotificationChannel("background_service", "Background Service")
            return NotificationCompat.Builder(this, channelId)
                .setContentTitle("No Fine")
                .setContentText("Notification Scheduling")
                .setSmallIcon(R.drawable.icon_no_fine)
                .build()
        }

        private fun createNotificationChannel(channelId: String, channelName: String): String {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            return channelId
        }

        private fun scheduleBackgroundTasks(username: String, password: String) {
            // Schedule the post request immediately and then periodically
            handler.post{
                schedulePostRequests(username, password)
            }
        }

        private fun schedulePostRequests(username: String, password: String) {
            // Get the current calendar instance
            val calendar = Calendar.getInstance()
            // Get the current hour and minute
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(Calendar.MINUTE)

            // Calculate milliseconds until the next 8:39 AM
            val next839Millis = calculateNext839Millis(currentHour, currentMinute)

            // Calculate milliseconds until the next 9:24 PM
            val next924Millis = calculateNext924Millis(currentHour, currentMinute)

            // Determine the time parameter based on the next scheduled time
            time = if (next839Millis < next924Millis) 0 else 1

            // Schedule the post request based on the next scheduled time
            nextMillis = if (time == 0) next839Millis else next924Millis

            Log.d("MainActivity", "$nextMillis")

            // Post request at the appropriate time
            handler.postDelayed({
                sendPostRequest(username, password, time)
            }, nextMillis)
        }

        private fun calculateNext839Millis(currentHour: Int, currentMinute: Int): Long {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 40)
                set(Calendar.SECOND, 0)
                if(get(Calendar.HOUR_OF_DAY) < currentHour) add(Calendar.DAY_OF_MONTH, 1)
                else if(get(Calendar.HOUR_OF_DAY) == currentHour){
                    if(get(Calendar.MINUTE) < currentMinute) add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            return calendar.timeInMillis - Calendar.getInstance().timeInMillis
        }

        private fun calculateNext924Millis(currentHour: Int, currentMinute: Int): Long {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 21)
                set(Calendar.MINUTE, 25)
                set(Calendar.SECOND, 0)
                if(get(Calendar.HOUR_OF_DAY) < currentHour) add(Calendar.DAY_OF_MONTH, 1)
                else if(get(Calendar.HOUR_OF_DAY) == currentHour){
                    if(get(Calendar.MINUTE) < currentMinute) add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            return calendar.timeInMillis - Calendar.getInstance().timeInMillis
        }

        private fun sendPostRequest(username: String, password: String, time: Int) {
            Log.d("MainActivity", "$username $password $time")

            val url = "https://4e1b-13-200-255-111.ngrok-free.app/login"
            val requestBody = "{\"username\": \"$username\", \"password\": \"$password\", \"time\": \"$time\"}"

            // Increase the timeout value to allow longer wait times
            val client = OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS) // Set the connect timeout to 60 seconds
                .readTimeout(120, TimeUnit.SECONDS) // Set the read timeout to 60 seconds
                .writeTimeout(120, TimeUnit.SECONDS) // Set the write timeout to 60 seconds
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()
                    if (responseBody == "A") {
                        // If the response indicates success, show a notification
                        showNotification()
                    }
                    handler.postDelayed({
                        Log.d("BackgroundService", "Username: $username, Password: $password")
                        schedulePostRequests(username, password)
                    }, 2 * 60 * 1000)
                }

                override fun onFailure(call: Call, e: IOException) {
                    Log.e("MainActivity", "Failed to send POST request: ${e.message}")
                }
            })
        }

        private fun showNotification() {
            val message = "Give Attendance!"
            // Create a notification channel for Android Oreo and above
            val channelId = "attendance_notification_channel"
            val channelName = "Attendance Notification Channel"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Notification channel for attendance reminders"
            }
            notificationManager.createNotificationChannel(channel)

            // Create a notification
            val notification = NotificationCompat.Builder(this, "attendance_notification_channel")
                .setContentTitle("Attendance Notification")
                .setContentText(message)
                .setSmallIcon(R.drawable.icon_no_fine)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            // Show the notification
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            notificationManager.notify(1, notification)
        }

        companion object {
            private const val NOTIFICATION_ID = 12345
        }
    }

}
