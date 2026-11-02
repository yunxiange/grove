package dev.grove.reader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun BoxScope.ReaderBackToTop(scroll:ScrollState) {
    val scope=rememberCoroutineScope()
    val interaction=remember{MutableInteractionSource()}
    val pressed by interaction.collectIsPressedAsState()
    val minimum=with(LocalDensity.current){120.dp.roundToPx()}
    val visible by remember(scroll,minimum){derivedStateOf{
        scroll.viewportSize>0&&scroll.value>maxOf(minimum,scroll.viewportSize/2)
    }}
    if(visible)IconButton(onClick={scope.launch{scroll.animateScrollTo(0)}},
        interactionSource=interaction,colors=IconButtonDefaults.iconButtonColors(contentColor=Ink),
        modifier=Modifier.align(Alignment.BottomEnd).padding(12.dp).size(48.dp)) {
        Surface(Modifier.size(32.dp),shape=RoundedCornerShape(4.dp),
            color=if(pressed)Ink.copy(alpha=.08f).compositeOver(Paper)else Paper,border=BorderStroke(1.dp,Rule)) {
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                Icon(Icons.Outlined.VerticalAlignTop,"回到顶部",Modifier.size(16.dp),tint=Ink)
            }
        }
    }
}
