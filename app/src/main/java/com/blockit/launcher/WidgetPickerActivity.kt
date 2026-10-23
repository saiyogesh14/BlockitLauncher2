package com.blockit.launcher

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// This activity exists as a registered component for widget pick flows.
// Actual widget picking is handled via system intent in MainActivity.
class WidgetPickerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
