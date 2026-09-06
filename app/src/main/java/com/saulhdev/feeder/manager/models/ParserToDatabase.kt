package com.saulhdev.feeder.manager.models

interface ParserToDatabase <T> {
    suspend fun getItem(id: String): T?

    suspend fun saveItem(item: T)
}
