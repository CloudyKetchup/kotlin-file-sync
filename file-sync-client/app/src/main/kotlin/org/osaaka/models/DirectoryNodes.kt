package org.osaaka.models

import kotlinx.serialization.Serializable

@Serializable
data class DirectoryTreeNode(
        val name: String,
        val path: String,
        val dateCreated: String,
        val dateModified: String
)

@Serializable data class DirectoryNodesResponse(val files: List<DirectoryTreeNode>)