package dev.shs.shsplayer.core.data.mappers

import dev.shs.shsplayer.core.common.Utils
import dev.shs.shsplayer.core.database.relations.DirectoryWithMedia
import dev.shs.shsplayer.core.database.relations.MediumWithInfo
import dev.shs.shsplayer.core.model.Folder

fun DirectoryWithMedia.toFolder() = Folder(
    name = directory.name,
    path = directory.path,
    dateModified = directory.modified,
    parentPath = directory.parentPath,
    formattedMediaSize = Utils.formatFileSize(media.sumOf { it.mediumEntity.size }),
    mediaList = media.map(MediumWithInfo::toVideo),
)
