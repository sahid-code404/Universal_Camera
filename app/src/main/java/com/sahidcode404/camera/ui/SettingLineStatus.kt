package com.sahidcode404.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SettingLine(title: String, subtitle: String, value: String, status: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(value, color = Color(0xFFBDC2C8), fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(subtitle, color = Color(0xFFAEB3BA), fontSize = 11.sp)
            Text(
                status,
                color = if (status == "RAW") Color(0xFF17345E) else Color(0xFFAEB3BA),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .background(
                        if (status == "RAW") Color(0xFFD3DDFF) else Color.Transparent,
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
