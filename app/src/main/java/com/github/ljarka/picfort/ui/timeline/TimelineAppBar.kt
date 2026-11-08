package com.github.ljarka.picfort.ui.timeline

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.github.ljarka.picfort.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(text = "Timeline")
        },
        navigationIcon = {
            Image(
                painter = painterResource(R.drawable.icon),
                contentDescription = null,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(44.dp),
            )
        },
        scrollBehavior = scrollBehavior,
    )
}