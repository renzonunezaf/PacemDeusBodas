data class OfflineSetlistResponse(
    val offline: Boolean,
    val weddingId: Int,
    val items: List<SetlistSong>
)
