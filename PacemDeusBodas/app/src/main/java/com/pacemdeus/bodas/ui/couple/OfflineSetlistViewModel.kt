class OfflineSetlistViewModel(
    private val db: AppDatabase
) : ViewModel() {

    var songs by mutableStateOf<List<SetlistEntity>>(emptyList())

    fun downloadSetlist(weddingId: Int) {

        viewModelScope.launch {

            try {

                val response =
                    ApiClient.apiService
                        .getOfflineSetlist(weddingId)

                val entities =
                    response.items.map {

                        SetlistEntity(
                            id = it.id,
                            title = it.title,
                            author = it.author,
                            moment_name = it.moment_name
                        )
                    }

                db.setlistDao().insertAll(entities)

                songs = db.setlistDao().getAll()

            } catch (e: Exception) {

                songs = db.setlistDao().getAll()

            }
        }
    }
}
