package com.oneimage.android.ui.dashboard

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.zIndex
import com.oneimage.android.R
import com.oneimage.android.ui.Screen
import com.oneimage.android.ui.theme.PrimaryGradient

data class DashboardItem(
    val title: String,
    val icon: ImageVector,
    val route: Any,
    val description: String
)

private val GadgetBlack = Color(0xFF050505)
private val GadgetPrimaryText = Color.White
private val GadgetSecondaryText = Color.White.copy(alpha = 0.68f)

data class DashboardDemo(
    val id: String,
    val title: String,
    val eyebrow: String,
    val description: String,
    val stat: String,
    val beforeLabel: String,
    val afterLabel: String,
    @param:DrawableRes val beforeImageRes: Int,
    @param:DrawableRes val afterImageRes: Int,
    val icon: ImageVector,
    val route: Any,
    val accent: Color
)

object DashboardDemoCatalog {
    val demos = listOf(
        DashboardDemo(
            id = "image_generation",
            title = "Image Generation",
            eyebrow = "Reference to turntable",
            description = "A single character reference becomes a controlled set of production-ready views.",
            stat = "1 image to 8 views",
            beforeLabel = "Input image",
            afterLabel = "Generated view",
            beforeImageRes = R.drawable.demo_image_generation_before,
            afterImageRes = R.drawable.demo_image_generation_after,
            icon = Icons.Default.AutoAwesome,
            route = Screen.ImageGen,
            accent = Color(0xFF38BDF8)
        ),
        DashboardDemo(
            id = "story_images",
            title = "Story Images",
            eyebrow = "Paragraph to panel",
            description = "Narrative guidance turns into a matching illustration panel for review or publishing.",
            stat = "Story brief to panel",
            beforeLabel = "Input reference",
            afterLabel = "Story image",
            beforeImageRes = R.drawable.demo_story_images_before,
            afterImageRes = R.drawable.demo_story_images_after,
            icon = Icons.Default.AutoStories,
            route = Screen.StoryImages,
            accent = Color(0xFFF43F5E)
        ),
        DashboardDemo(
            id = "ref_restyle",
            title = "Ref Restyle",
            eyebrow = "Source and style reference",
            description = "A source image is transformed with visual direction from a second reference.",
            stat = "Source to styled output",
            beforeLabel = "Source image",
            afterLabel = "Restyled output",
            beforeImageRes = R.drawable.demo_ref_restyle_before,
            afterImageRes = R.drawable.demo_ref_restyle_after,
            icon = Icons.Default.Palette,
            route = Screen.RefRestyle,
            accent = Color(0xFF14B8A6)
        ),
        DashboardDemo(
            id = "game_asset_upscaler",
            title = "Game Asset Upscaler",
            eyebrow = "Small asset to HD",
            description = "Low-resolution game art is cleaned and enlarged while preserving its silhouette.",
            stat = "Small source to HD result",
            beforeLabel = "Input asset",
            afterLabel = "Output asset",
            beforeImageRes = R.drawable.demo_game_asset_before,
            afterImageRes = R.drawable.demo_game_asset_after,
            icon = Icons.Default.Hd,
            route = Screen.GameAssetUpscaler,
            accent = Color(0xFFF59E0B)
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: androidx.navigation.NavHostController
) {
    val surfaceGradient = Brush.verticalGradient(
        colors = listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.background)
    )

    val items = listOf(
        DashboardItem("Credits", Icons.Default.Paid, Screen.EarnCredits, "Buy or earn credits"),
        DashboardItem("Image Generation", Icons.Default.AutoAwesome, Screen.ImageGen, "Consistent character views"),
        DashboardItem("Video Generation", Icons.Default.Movie, Screen.VideoGen, "Short video from two images"),
        DashboardItem("Single I2V", Icons.Default.SmartDisplay, Screen.SingleI2V, "One image into a clip up to 10 seconds"),
        DashboardItem("Keyframes", Icons.Default.Animation, Screen.Keyframes, "Longer video from key images"),
        DashboardItem("Sound Effects", Icons.Default.Mic, Screen.SoundEffects, "Foley, ambience, and impacts"),
        DashboardItem("LipSync", Icons.Default.Mic, Screen.LipSync, "Speaking or singing image clips"),
        DashboardItem("Character Replacement", Icons.Default.FaceRetouchingNatural, Screen.CharacterReplacement, "Change a character in a clip"),
        DashboardItem("Story Images", Icons.Default.AutoStories, Screen.StoryImages, "Images for story paragraphs"),
        DashboardItem("Ref Restyle", Icons.Default.Palette, Screen.RefRestyle, "Restyle from a reference image"),
        DashboardItem("Game Mesh", Icons.Default.ViewInAr, Screen.MeshModel, "Draft 3D model from an image"),
        DashboardItem("Game Asset Upscaler", Icons.Default.Hd, Screen.GameAssetUpscaler, "Clean, larger game art"),
        DashboardItem("Support", Icons.Default.SupportAgent, Screen.Support, "Search the QA database")
    )

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Creator Tools", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Unleash your creativity", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 16.dp, end = 8.dp)) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.clickable { navController.navigate(Screen.Settings) }
                            ) {
                                Icon(Icons.Default.Person, contentDescription = "Account settings", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Settings) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.8f)
                ),
                windowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                tonalElevation = 0.dp,
                windowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            ) {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.Dashboard, null) },
                    label = { Text("Home", fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(Screen.Support) },
                    icon = { Icon(Icons.Default.SupportAgent, null) },
                    label = { Text("Support", fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(Screen.Settings) },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Settings", fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(surfaceGradient)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 148.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 16.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DashboardDemoHeader()
                }
                gridItems(
                    items = DashboardDemoCatalog.demos,
                    key = { demo -> demo.id },
                    span = { GridItemSpan(maxLineSpan) }
                ) { demo ->
                    DashboardDemoCard(demo = demo) {
                        navController.navigate(demo.route)
                    }
                }
                gridItems(items) { item ->
                    DashboardCard(item) {
                        navController.navigate(item.route)
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardDemoHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Workflow Demos",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Real inputs. Real results.",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            lineHeight = 26.sp
        )
    }
}

@Composable
fun DashboardDemoCard(
    demo: DashboardDemo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = GadgetBlack
        ),
        border = BorderStroke(1.dp, demo.accent.copy(alpha = 0.45f)),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(demo.accent.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = demo.icon,
                        contentDescription = null,
                        tint = demo.accent,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = demo.eyebrow,
                        fontSize = 11.sp,
                        color = demo.accent,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = demo.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = GadgetPrimaryText,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = demo.description,
                fontSize = 12.sp,
                color = GadgetSecondaryText,
                lineHeight = 16.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            DashboardBeforeAfterSlider(
                demo = demo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(276.dp)
            )

            DashboardDemoPreviewStrip(demo = demo)

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = demo.accent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = demo.stat,
                    fontSize = 12.sp,
                    color = GadgetPrimaryText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun DashboardBeforeAfterSlider(
    demo: DashboardDemo,
    modifier: Modifier = Modifier
) {
    var position by remember(demo.id) { mutableFloatStateOf(0.58f) }
    val shape = RoundedCornerShape(14.dp)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(shape)
                .background(GadgetBlack)
        ) {
            val stageWidth = maxWidth
            val beforeWidth = stageWidth * position

            DashboardDemoImageLayer(
                imageRes = demo.afterImageRes,
                contentDescription = "${demo.title} after",
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.52f))
                        )
                    )
            )
            DashboardDemoImageLayer(
                imageRes = demo.beforeImageRes,
                contentDescription = "${demo.title} before",
                modifier = Modifier
                    .matchParentSize()
                    .drawWithContent {
                        drawContext.canvas.save()
                        drawContext.canvas.clipRect(
                            left = 0f,
                            top = 0f,
                            right = size.width * position,
                            bottom = size.height,
                            clipOp = ClipOp.Intersect
                        )
                        drawContent()
                        drawContext.canvas.restore()
                    }
            )
            Box(
                modifier = Modifier
                    .offset(x = beforeWidth - 1.dp)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.86f))
                    .zIndex(1f)
            )
            Surface(
                modifier = Modifier
                    .offset(x = beforeWidth - 18.dp)
                    .align(Alignment.CenterStart)
                    .size(36.dp)
                    .zIndex(2f),
                color = demo.accent,
                shape = CircleShape,
                shadowElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DemoLabel(text = demo.beforeLabel)
                DemoLabel(text = demo.afterLabel)
            }
        }

        Slider(
            value = position,
            onValueChange = { position = it.coerceIn(0.12f, 0.88f) },
            valueRange = 0.12f..0.88f,
            colors = SliderDefaults.colors(
                thumbColor = demo.accent,
                activeTrackColor = demo.accent,
                inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )
        )
    }
}

@Composable
fun DashboardDemoPreviewStrip(demo: DashboardDemo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DemoPreviewTile(
            imageRes = demo.beforeImageRes,
            label = demo.beforeLabel,
            modifier = Modifier.weight(1f)
        )
        Surface(
            color = demo.accent.copy(alpha = 0.18f),
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = demo.accent,
                modifier = Modifier
                    .size(28.dp)
                    .padding(6.dp)
            )
        }
        DemoPreviewTile(
            imageRes = demo.afterImageRes,
            label = demo.afterLabel,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DemoPreviewTile(
    @DrawableRes imageRes: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(GadgetBlack)
    ) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = label,
            contentScale = ContentScale.Fit,
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DashboardDemoImageLayer(
    @DrawableRes imageRes: Int,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(GadgetBlack)
    ) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .alpha(0.34f)
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.18f))
        )
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center,
            modifier = Modifier
                .matchParentSize()
                .padding(10.dp)
        )
    }
}

@Composable
private fun DemoLabel(text: String) {
    Surface(
        color = Color.Black.copy(alpha = 0.58f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DashboardCard(item: DashboardItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(124.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = GadgetBlack
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(PrimaryGradient, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (item.route == Screen.SoundEffects) {
                    Image(painter = painterResource(R.drawable.sound_effects), contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                } else Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 14.sp,
                    color = GadgetPrimaryText,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.description,
                    fontSize = 11.sp,
                    color = GadgetSecondaryText,
                    lineHeight = 14.sp,
                    maxLines = 2
                )
            }
        }
    }
}
