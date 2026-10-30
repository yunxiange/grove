package dev.grove.reader.domain

fun sortedRecentVisits(visits:List<RepositoryVisit>,projects:Map<String,Project>,sort:RecentSort):List<RepositoryVisit> {
    val known=projects.mapKeys{it.key.lowercase()}
    return when(sort) {
        RecentSort.VISITED -> visits.sortedByDescending{it.visitedAt}
        RecentSort.NAME -> visits.sortedBy{it.repo.fullName.lowercase()}
        RecentSort.STARS -> visits.sortedWith(compareByDescending<RepositoryVisit>{known.containsKey(it.repo.fullName.lowercase())}
            .thenByDescending{known[it.repo.fullName.lowercase()]?.stars?:0}.thenBy{it.repo.fullName.lowercase()})
    }
}
