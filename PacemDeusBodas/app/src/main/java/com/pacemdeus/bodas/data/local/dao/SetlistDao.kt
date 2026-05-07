@Dao
interface SetlistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(
        items: List<SetlistEntity>
    )

    @Query("SELECT * FROM offline_setlist")
    suspend fun getAll(): List<SetlistEntity>
}
