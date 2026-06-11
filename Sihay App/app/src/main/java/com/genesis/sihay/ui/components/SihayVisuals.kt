package com.genesis.sihay.ui.components

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.genesis.sihay.R
import com.genesis.sihay.ui.theme.SihayAccent
import com.genesis.sihay.ui.theme.SihayBackground
import com.genesis.sihay.ui.theme.SihayOnBackground
import com.genesis.sihay.ui.theme.SihayPrimary

@Composable
fun SihayGradientBackground(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFFFFF7EA),
                        Color(0xFFFDDDB6),
                        Color(0xFFF8C988)
                    )
                )
            )
            .padding(contentPadding)
    ) {
        content()
    }
}

@Composable
fun SihayLogo(
    modifier: Modifier = Modifier,
    titleColor: Color = SihayPrimary,
    accentColor: Color = Color(0xFFFDE8CA)
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "SIHAY",
            style = MaterialTheme.typography.headlineMedium.copy(
                color = titleColor,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
        )
        Surface(
            color = accentColor,
            shape = CircleShape,
            tonalElevation = 2.dp
        ) {
            Text(
                text = "Egg Fertility AI",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium.copy(
                    color = SihayOnBackground,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

@Composable
fun SihayFloatingEgg(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    shape: Shape = CircleShape
) {
    val transition = rememberInfiniteTransition(label = "sihay_egg")
    val wobble by transition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "egg_wobble"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = wobble },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.sihay_small_icon),
            contentDescription = "Sihay mascot egg icon",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun EggWaveAnimation(
    modifier: Modifier = Modifier,
    message: String = "Salamat! See you soon."
) {
    val context = LocalContext.current
    val imageLoader = ImageLoader.Builder(context)
        .components {
            if (Build.VERSION.SDK_INT >= 28) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Image(
            painter = rememberAsyncImagePainter(R.drawable.sihay_good_bye, imageLoader),
            contentDescription = "Goodbye animation",
            modifier = Modifier.size(250.dp),
            contentScale = ContentScale.Fit
        )
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium.copy(color = SihayOnBackground),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
