@Composable
fun NotificationCard(item: NotificationItem) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = item.title,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(text = item.message)

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.createdAt,
                fontSize = 12.sp
            )
        }
    }
}
