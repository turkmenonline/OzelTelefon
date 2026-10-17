package com.ozel.telefon.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozel.telefon.callVoicemail
import com.ozel.telefon.ui.theme.IOS

@Composable
fun VoicemailScreen() {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(top = 46.dp)) {
        LargeTitle("Sesli Mesaj")
        Text(
            "Sesli mesaj kutunu aramak için dokun.",
            color = IOS.Gray,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Text(
            "Sesli Mesajı Ara",
            color = IOS.Blue,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clickable { callVoicemail(context) }
                .padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}
