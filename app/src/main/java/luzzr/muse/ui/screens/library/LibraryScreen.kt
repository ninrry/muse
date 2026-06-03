package luzzr.muse.ui.screens.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    innerPadding: PaddingValues = PaddingValues()
) {
    var showSearch by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("曲库") },
                actions = {
                    IconButton(onClick = { viewModel.cycleSortType() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "排序"
                        )
                    }
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LibraryRoute(
            viewModel = viewModel,
            showSearch = showSearch,
            scaffoldPadding = padding,
            innerPadding = innerPadding
        )
    }
}
