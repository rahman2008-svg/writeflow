package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val wordCount: Int = 0,
    val charCount: Int = 0,
    val lastModified: Long = System.currentTimeMillis(),
    val isStarred: Boolean = false,
    val category: String = "Draft" // Draft, Work, Personal, Creative, Template
)
