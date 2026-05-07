class OfflineSetlistViewModel : ViewModel() {

    var songs by mutableStateOf<List<SetlistSong>>(emptyList())

    fun downloadSetlist(weddingId: Int) {

        viewModelScope.launch {

            try {

                val response =
                    ApiClient.apiService.getOfflineSetlist(weddingId)

                songs = response.items

            } catch (e: Exception) {

            }
        }
    }
}
