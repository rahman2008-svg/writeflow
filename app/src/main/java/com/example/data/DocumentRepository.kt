package com.example.data

import kotlinx.coroutines.flow.Flow

class DocumentRepository(private val documentDao: DocumentDao) {
    val allDocuments: Flow<List<Document>> = documentDao.getAllDocuments()

    fun getDocumentById(id: Long): Flow<Document?> {
        return documentDao.getDocumentById(id)
    }

    suspend fun insertDocument(document: Document): Long {
        return documentDao.insertDocument(document)
    }

    suspend fun updateDocument(document: Document) {
        documentDao.updateDocument(document)
    }

    suspend fun deleteDocument(document: Document) {
        documentDao.deleteDocument(document)
    }

    suspend fun deleteDocumentById(id: Long) {
        documentDao.deleteDocumentById(id)
    }
}
