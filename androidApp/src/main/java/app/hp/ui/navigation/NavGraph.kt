package app.hp.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object EntryDetail : Screen("entry/{entryId}") {
        fun createRoute(entryId: String) = "entry/$entryId"
    }
    object CreateEntry : Screen("create_entry")
    object StorageSettings : Screen("storage/{storageId}") {
        fun createRoute(storageId: String) = "storage/$storageId"
    }
    object Search : Screen("search")
}
