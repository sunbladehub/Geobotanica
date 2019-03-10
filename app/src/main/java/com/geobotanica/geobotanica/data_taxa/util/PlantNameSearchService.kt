package com.geobotanica.geobotanica.data_taxa.util

import com.geobotanica.geobotanica.data_taxa.DEFAULT_RESULT_LIMIT
import com.geobotanica.geobotanica.data_taxa.repo.TaxonRepo
import com.geobotanica.geobotanica.data_taxa.repo.VernacularRepo
import com.geobotanica.geobotanica.data_taxa.util.PlantNameSearchService.PlantNameTag.*
import com.geobotanica.geobotanica.util.Lg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.system.measureTimeMillis


const val defaultFilterFlags = 0b0

class PlantNameSearchService @Inject constructor (
        private val taxonRepo: TaxonRepo,
        private val vernacularRepo: VernacularRepo
) : CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext = Dispatchers.IO + job

    private val defaultSearchSequence = listOf(
        PlantNameSearch(fun0 = vernacularRepo::getAllStarred, tagList = listOf(COMMON, STARRED)),
        PlantNameSearch(fun0 = taxonRepo::getAllStarred, tagList = listOf(SCIENTIFIC, STARRED)),
        PlantNameSearch(fun0 = vernacularRepo::getAllUsed, tagList = listOf(COMMON, USED)),
        PlantNameSearch(fun0 = taxonRepo::getAllUsed, tagList = listOf(SCIENTIFIC, USED))
    )

    private val singleWordSearchSequence = listOf(
        PlantNameSearch(fun1 = vernacularRepo::starredStartsWith, tagList = listOf(COMMON, STARRED)),
        PlantNameSearch(fun1 = taxonRepo::starredStartsWith, tagList = listOf(SCIENTIFIC, STARRED)),
        PlantNameSearch(fun1 = vernacularRepo::usedStartsWith, tagList = listOf(COMMON, USED)),
        PlantNameSearch(fun1 = taxonRepo::usedStartsWith, tagList = listOf(SCIENTIFIC, USED)),
        PlantNameSearch(fun1 = vernacularRepo::nonFirstWordStartsWith, tagList = listOf(COMMON)),
        PlantNameSearch(fun1 = vernacularRepo::firstWordStartsWith, tagList = listOf(COMMON)),
        PlantNameSearch(fun1 = taxonRepo::genericStartsWith, tagList = listOf(SCIENTIFIC)),
        PlantNameSearch(fun1 = taxonRepo::epithetStartsWith, tagList = listOf(SCIENTIFIC))
    )

    private val doubleWordSearchSequence = listOf(
        PlantNameSearch(fun2 = vernacularRepo::starredStartsWith, tagList = listOf(COMMON, STARRED)),
        PlantNameSearch(fun2 = taxonRepo::starredStartsWith, tagList = listOf(SCIENTIFIC, STARRED)),
        PlantNameSearch(fun2 = vernacularRepo::usedStartsWith, tagList = listOf(COMMON, USED)),
        PlantNameSearch(fun2 = taxonRepo::usedStartsWith, tagList = listOf(SCIENTIFIC, USED)),
        PlantNameSearch(fun2 = vernacularRepo::anyWordStartsWith, tagList = listOf(COMMON)),
        PlantNameSearch(fun2 = taxonRepo::genericOrEpithetStartsWith, tagList = listOf(SCIENTIFIC))
    )

    @ExperimentalCoroutinesApi
    fun search(searchText: String, filterOptions: SearchFilterOptions): ReceiveChannel<List<SearchResult>> = produce {
        val words = searchText.split(' ').filter { it.isNotBlank() }
        val wordCount = words.size
        Lg.d("Search words: $words")

        val aggregateResultIds = mutableListOf<Long>()
        val aggregateResults = mutableListOf<SearchResult>()
        val searchSequence = getSearchSequence(wordCount)

        searchSequence.filter { filterOptions.shouldNotFilter(it) }.forEach forEachSearch@ { search ->
            if (aggregateResults.size >= DEFAULT_RESULT_LIMIT)
                return@forEachSearch

            val time = measureTimeMillis {
                val results = getSearchResults(words, search, getLimit(aggregateResults)) ?: return@forEachSearch
                val uniqueIds = results subtract aggregateResultIds
                val mergeTagsOnIds = results intersect aggregateResultIds

                aggregateResultIds.addAll(results)
                aggregateResults.addAll(uniqueIds.map { mapIdToSearchResult(it, search) })

                send(aggregateResults
                    .map {
                        if (mergeTagsOnIds.contains(it.id)) {
                            it.mergeTags(search.tags)
                        } else
                            it
                    }
                    .filter { filterOptions.shouldNotFilter(it) }
                    .distinctBy { it.plantName }
                    .sortedByDescending { it.tagCount() }
                )
            }
            Lg.d("${search.functionName}: ${aggregateResults.size} hits ($time ms)")
        }
        close()
    }

    private fun getSearchSequence(wordCount: Int): List<PlantNameSearch> {
        return when (wordCount) {
            0 -> defaultSearchSequence
            1 -> singleWordSearchSequence
            else -> doubleWordSearchSequence
        }
    }

    private fun getSearchResults(words: List<String>, search: PlantNameSearch, limit: Int): List<Long>? {
        return when (words.size) {
            0 -> search.fun0!!(limit)
            1 -> search.fun1!!(words[0], limit)
            else -> search.fun2!!(words[0], words[1], limit)
        }
    }

    private fun getLimit(filteredResults: List<SearchResult>) =
            DEFAULT_RESULT_LIMIT - filteredResults.size

    private fun mapIdToSearchResult(id: Long, search: PlantNameSearch): SearchResult {
        return SearchResult(id, search.tags, when {
            search.hasTag(COMMON) -> vernacularRepo.get(id)!!.vernacular!!.capitalize()
            search.hasTag(SCIENTIFIC) -> taxonRepo.get(id)!!.scientific.capitalize()
            else -> throw IllegalArgumentException("Must specify either COMMON or SCIENTIFIC tag")
        })
    }

    class PlantNameSearch(
            val fun0: ( (Int) -> List<Long>? )? = null,
            val fun1: ( (String, Int) -> List<Long>? )? = null,
            val fun2: ( (String, String, Int) -> List<Long>? )? = null,
            tagList: List<PlantNameTag> = emptyList()
    ) {
        val tags: Int = tagList.fold(0) { acc, tag -> acc or tag.flag }

        val functionName: String
            get() {
                return (fun1 ?: fun2).toString()
                        .removePrefix("function ")
                        .removeSuffix(" (Kotlin reflection is not available)")
            }

        fun hasTag(tag: PlantNameTag) = tags and tag.flag != 0
    }

    data class SearchResult(
        val id: Long, // Either vernacularId (COMMMON) or taxonId (SCIENTIFIC), depending on tag present
        var tags: Int, // Bitflags
        val plantName: String
    ) {
        fun hasTag(tag: PlantNameTag): Boolean = tags and tag.flag != 0
        fun toggleTag(tag: PlantNameTag) { tags = tags xor tag.flag }
        fun mergeTags(newTags: Int): SearchResult = apply { tags = tags or newTags }
        fun tagCount(): Int {
            var temp = tags
            var count = 0
            while (temp != 0) {
                if (temp and 0b1 != 0)
                    ++count
                temp = temp shr 1
            }
            return count
        }
    }

    data class SearchFilterOptions(val filterFlags: Int) {
        fun hasFilter(filterOption: PlantNameTag) = filterOption.flag and filterFlags != 0
        fun shouldNotFilter(search: PlantNameSearch) = (search.tags and (COMMON.flag or SCIENTIFIC.flag)) and filterFlags == 0
        fun shouldNotFilter(searchResult: SearchResult) = searchResult.tags and filterFlags == 0

        companion object {
            fun fromBooleans(
                    isCommonFiltered: Boolean,
                    isScientificFiltered: Boolean,
                    isStarredFiltered: Boolean,
                    isUsedFiltered: Boolean
            ): SearchFilterOptions {
                var filterFlags = 0b0
                if (isCommonFiltered)
                    filterFlags = filterFlags or COMMON.flag
                if (isScientificFiltered)
                    filterFlags = filterFlags or SCIENTIFIC.flag
                if (isStarredFiltered)
                    filterFlags = filterFlags or STARRED.flag
                if (isUsedFiltered)
                    filterFlags = filterFlags or USED.flag
                return SearchFilterOptions(filterFlags)
            }
        }
    }

    enum class PlantNameTag(val flag: Int) {
        COMMON(     0b0000_0001),
        SCIENTIFIC( 0b0000_0010),
        STARRED(    0b0000_0100),
        USED(       0b0000_1000);
    }

}