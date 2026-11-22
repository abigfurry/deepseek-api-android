package com.yuyuyu.deepseek.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuyuyu.deepseek.R
import com.yuyuyu.deepseek.ui.theme.LocalDsColors

private val BrandBlue = Color(0xFF426EFE)

@Composable
fun PhoneLoginScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
) {
    val ds = LocalDsColors.current
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(ds.chipBg)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "联系我们",
                fontSize = 15.sp,
                color = ds.subText,
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 96.dp)
        ) {
            Text(
                text = "手机号登录",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(26.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ds.chipBg)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "+86",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = phone,
                    onValueChange = { if (it.length <= 11) phone = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    ),
                    cursorBrush = SolidColor(BrandBlue),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    decorationBox = { inner ->
                        if (phone.isEmpty()) {
                            Text("", fontSize = 16.sp)
                        }
                        inner()
                    },
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ds.chipBg)
                    .padding(start = 18.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = code,
                    onValueChange = { if (it.length <= 6) code = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    ),
                    cursorBrush = SolidColor(BrandBlue),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    decorationBox = { inner ->
                        if (code.isEmpty()) {
                            Text("验证码", fontSize = 16.sp, color = ds.subText)
                        }
                        inner()
                    },
                )
                Box(
                    Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(ds.divider)
                )
                Text(
                    text = "发送验证码",
                    modifier = Modifier.padding(start = 12.dp),
                    fontSize = 15.sp,
                    color = if (phone.length >= 11) BrandBlue else ds.subText,
                )
            }

            Spacer(Modifier.height(24.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(if (phone.length >= 11) BrandBlue else BrandBlue.copy(alpha = 0.45f))
                    .clickable(enabled = phone.length >= 11) { onLogin() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "登录",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
    }
}
