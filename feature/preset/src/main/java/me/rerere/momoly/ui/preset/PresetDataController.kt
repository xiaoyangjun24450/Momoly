package me.rerere.momoly.ui.preset

import android.net.Uri
import me.rerere.momoly.core.resource.ImportableResourceController
import me.rerere.momoly.core.resource.NamedResourceCreator
import me.rerere.momoly.core.resource.NamedResourceReader
import me.rerere.momoly.core.resource.ResourceDeleter
import me.rerere.momoly.core.resource.ResourceImporter
import me.rerere.momoly.core.resource.ResourceRenamer
import me.rerere.momoly.data.preset.RegexScript
import me.rerere.momoly.data.preset.StPreset

interface PresetDataSource :
    NamedResourceReader<StPreset>,
    NamedResourceCreator<StPreset>,
    ResourceImporter<StPreset, Uri>,
    ResourceRenamer<String>,
    ResourceDeleter<String> {
    fun defaultName(): String?
    suspend fun save(preset: StPreset)
    suspend fun parseRegex(uri: Uri): RegexScript
}

class PresetDataController(
    private val dataSource: PresetDataSource,
) : ImportableResourceController<StPreset, Uri> {
    fun isDefault(name: String): Boolean = name == dataSource.defaultName()
    override suspend fun names(): List<String> = dataSource.names()
    override suspend fun load(name: String): StPreset = dataSource.load(name)
    suspend fun create(name: String): StPreset = dataSource.create(name)
    override suspend fun import(source: Uri): String = dataSource.import(source).name
    override suspend fun rename(old: String, new: String): Boolean = dataSource.rename(old, new)
    override suspend fun delete(name: String) = dataSource.delete(name)
    suspend fun save(preset: StPreset) = dataSource.save(preset)
    suspend fun parseRegex(uri: Uri): RegexScript = dataSource.parseRegex(uri)
}
