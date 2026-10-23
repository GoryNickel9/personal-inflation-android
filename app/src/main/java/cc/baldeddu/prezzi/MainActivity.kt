package cc.baldeddu.prezzi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cc.baldeddu.prezzi.data.TrackerRepository
import cc.baldeddu.prezzi.ui.PrezziApp
import java.io.File
import cc.baldeddu.prezzi.update.UpdateRepository
import cc.baldeddu.prezzi.update.UpdateViewModel

class MainActivity : ComponentActivity() {
    private val updates: UpdateViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = UpdateViewModel(UpdateRepository(applicationContext)) as T
        }
    }
    private val model: TrackerViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TrackerViewModel(
                TrackerRepository(File(filesDir, "tracker-v1.json"))) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { PrezziApp(model, updates) }
    }
}
