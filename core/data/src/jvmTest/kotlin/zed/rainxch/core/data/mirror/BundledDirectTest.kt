package zed.rainxch.core.data.mirror

import zed.rainxch.core.domain.model.mirror.MirrorConfig
import zed.rainxch.core.domain.model.mirror.MirrorStatus
import zed.rainxch.core.domain.model.mirror.MirrorType
import kotlin.test.Test
import kotlin.test.assertEquals

class BundledDirectTest {
    private val bundledDirect = BundledMirrors.ALL.single { it.id == "direct" }

    @Test
    fun a_catalog_without_direct_gets_the_bundled_entry_first() {
        val ghfast = mirror("ghfast_top", MirrorType.COMMUNITY)

        assertEquals(listOf(bundledDirect, ghfast), listOf(ghfast).withBundledDirect())
    }

    @Test
    fun an_empty_catalog_still_offers_direct() {
        assertEquals(listOf(bundledDirect), emptyList<MirrorConfig>().withBundledDirect())
    }

    @Test
    fun a_catalog_that_lists_direct_keeps_its_own_entry() {
        val catalog = listOf(mirror("ghfast_top", MirrorType.COMMUNITY), mirror("direct", MirrorType.OFFICIAL))

        assertEquals(catalog, catalog.withBundledDirect())
    }

    private fun mirror(
        id: String,
        type: MirrorType,
    ) = MirrorConfig(
        id = id,
        name = id,
        urlTemplate = null,
        type = type,
        status = MirrorStatus.OK,
        latencyMs = 120,
        lastCheckedAt = null,
    )
}
