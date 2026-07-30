package luzzr.muse.ui.screens.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun LibraryScreen(viewModel: LibraryViewModel = hiltViewModel(), innerPadding: PaddingValues = PaddingValues()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LibraryRoute(
            viewModel = viewModel,
            showSearch = true,
            scaffoldPadding = padding,
            innerPadding = innerPadding
        )
    }
}
