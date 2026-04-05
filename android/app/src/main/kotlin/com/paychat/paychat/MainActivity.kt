package com.paychat.paychat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.paychat.paychat.ui.PayChatApp

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<PayChatViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PayChatApp(viewModel = viewModel)
        }
    }
}
