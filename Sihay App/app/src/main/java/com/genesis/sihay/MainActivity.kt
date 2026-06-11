package com.genesis.sihay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.genesis.sihay.ui.theme.SIhayTheme
import com.genesis.sihay.ui.SihayApp

class MainActivity : ComponentActivity() {

    // 1. Declare the classifier variable
    private lateinit var classifier: EggClassifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 2. Initialize the classifier with the Activity Context
        // Ensure you have created the 'EggClassifier.kt' file from the previous step!
        classifier = EggClassifier(this)

        enableEdgeToEdge()
        setContent {
            SIhayTheme {
                // 3. Pass the classifier to your App Composable
                // Note: This will show an error until you update SihayApp (see below)
                SihayApp(classifier = classifier)
            }
        }
    }

    override fun onDestroy() {
        // 4. Clean up the AI model when the app closes to free memory
        if (::classifier.isInitialized) {
            classifier.close()
        }
        super.onDestroy()
    }
}