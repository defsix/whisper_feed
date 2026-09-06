package com.saulhdev.feeder.utils

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

fun blobFile(itemId: String, filesDir: File): File =
    File(filesDir, "$itemId.txt.gz")

@Throws(IOException::class)
fun blobInputStream(itemId: String, filesDir: File): InputStream =
    GZIPInputStream(blobFile(itemId = itemId, filesDir = filesDir).inputStream())

@Throws(IOException::class)
fun blobOutputStream(itemId: String, filesDir: File): OutputStream =
    GZIPOutputStream(blobFile(itemId = itemId, filesDir = filesDir).outputStream())

fun blobFullFile(itemId: String, filesDir: File): File =
    File(filesDir, "$itemId.full.html.gz")

@Throws(IOException::class)
fun blobFullInputStream(itemId: String, filesDir: File): InputStream =
    GZIPInputStream(blobFullFile(itemId = itemId, filesDir = filesDir).inputStream())

@Throws(IOException::class)
fun blobFullOutputStream(itemId: String, filesDir: File): OutputStream =
    GZIPOutputStream(blobFullFile(itemId = itemId, filesDir = filesDir).outputStream())