package com.shs.videoplayer.core.data.mappers

import com.shs.videoplayer.core.common.Utils
import com.shs.videoplayer.core.database.relations.DirectoryWithMedia
import com.shs.videoplayer.core.database.relations.MediumWithInfo
import com.shs.videoplayer.core.model.Folder

fun DirectoryWithMedia.toFolder() = Folder(
    name = directory.name,
    path = directory.path,
    dateModified = directory.modified,
    parentPath = directory.parentPath,
    formattedMediaSize = Utils.formatFileSize(media.sumOf { it.mediumEntity.size }),
    mediaList = media.map(MediumWithInfo::toVideo),
)
