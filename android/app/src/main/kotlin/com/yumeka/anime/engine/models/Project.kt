package com.yumeka.anime.engine.models

import java.io.Serializable

data class Project(
    val id: String,
    val name: String,
    val path: String,
    val version: String,
    val lastEdited: String,
    val sizeLabel: String = "",
    val iconRes: Int,
    val isFavorite: Boolean = false,
    val hasError: Boolean = false,
    val errorMessage: String? = null
) : Serializable
