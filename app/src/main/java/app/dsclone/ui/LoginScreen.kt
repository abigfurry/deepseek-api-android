package com.yuyuyu.deepseek.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuyuyu.deepseek.R
import com.yuyuyu.deepseek.ui.theme.LocalDsColors

private val BrandBlue = Color(0xFF426EFE)

@Composable
fun LoginScreen(onContinue: () -> Unit) {
    val ds = LocalDsColors.current
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        Text(
            text = "联系我们",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 22.dp),
            fontSize = 15.sp,
            color = ds.subText,
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 207.5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.logo_vertical),
                contentDescription = "deepseek",
                modifier = Modifier.width(171.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 30.dp)
                .padding(bottom = 61.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(53.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(BrandBlue)
                    .clickable(onClick = onContinue),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_phone),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "手机号登录",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(53.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .border(1.dp, ds.divider, RoundedCornerShape(27.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(onClick = onContinue),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lock),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "密码登录",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .size(19.dp)
                        .clip(CircleShape)
                        .border(1.dp, ds.divider, CircleShape)
                )
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = ds.subText)) {
                            append("我已阅读并同意 DeepSeek 的")
                        }
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Medium,
                            )
                        ) {
                            append("用户协议")
                        }
                        withStyle(SpanStyle(color = ds.subText)) { append("和") }
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Medium,
                            )
                        ) {
                            append("隐私政策")
                        }
                    },
                    modifier = Modifier
                        .padding(start = 9.dp)
                        .fillMaxWidth(),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}
