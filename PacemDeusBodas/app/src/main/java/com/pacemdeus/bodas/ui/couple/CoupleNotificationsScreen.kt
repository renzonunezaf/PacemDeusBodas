@Composable
fun CoupleNotificationsScreen() {

    var notifications by remember {
        mutableStateOf<List<NotificationItem>>(emptyList())
    }

    LaunchedEffect(Unit) {

        try {

            notifications =
                ApiClient.apiService.getNotifications()

        } catch (e: Exception) {

            e.printStackTrace()

        }
    }

    LazyColumn {

        items(notifications) { item ->

            NotificationCard(item)

        }
    }
}
