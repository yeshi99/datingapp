package com.dating.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dating.app.data.api.RetrofitClient
import com.dating.app.data.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

/**
 * 功能 1：照片管理页 —— 上传照片 / 删除照片（纯前端实现，接口集中见 ApiService）
 *
 * 后端接口：
 *   GET    /users/me/photos          拉取照片列表
 *   POST   /users/me/photos          multipart 上传（字段 file）
 *   DELETE /users/me/photos/{id}     删除
 *   POST   /users/me/photos/{id}/avatar  设为头像（可选）
 */
@Composable
fun PhotosScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var uploading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            try {
                val resp = RetrofitClient.api.getMyPhotos()
                val body = resp.body()
                if (resp.isSuccessful && body?.data != null) {
                    photos = body.data!!
                    errorMsg = null
                } else {
                    errorMsg = "加载失败(${resp.code()})"
                }
            } catch (e: Exception) {
                errorMsg = "网络异常：${e.message}"
            }
            loading = false
        }
    }

    // 系统相册选图（Photo Picker，无需运行时权限）
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            uploading = true
            scope.launch {
                try {
                    val part = uriToMultipart(context, uri)
                    if (part == null) {
                        errorMsg = "读取图片失败"
                    } else {
                        val resp = RetrofitClient.api.uploadPhoto(part)
                        if (resp.isSuccessful) errorMsg = null else errorMsg = "上传失败(${resp.code()})"
                    }
                } catch (e: Exception) {
                    errorMsg = "上传异常：${e.message}"
                }
                uploading = false
                refresh()
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("我的照片", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(8.dp))
            if (loading || uploading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("上传照片")
            }
        }

        errorMsg?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(12.dp))

        if (photos.isEmpty() && !loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有照片，点右上角上传第一张吧", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(photos, key = { it.id }) { photo ->
                    Box(Modifier.aspectRatio(0.8f)) {
                        AsyncImage(
                            model = photo.url,
                            contentDescription = "照片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                        )
                        FilledTonalIconButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        RetrofitClient.api.deletePhoto(photo.id)
                                        refresh()
                                    } catch (e: Exception) {
                                        errorMsg = "删除失败：${e.message}"
                                    }
                                }
                            },
                            modifier = Modifier.size(28.dp).align(Alignment.TopEnd).padding(4.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "删除", Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

/** 把相册里的 Uri 拷贝为临时文件，并包装成 multipart 上传体 */
private suspend fun uriToMultipart(context: Context, uri: Uri): MultipartBody.Part? =
    withContext(Dispatchers.IO) {
        try {
            val tmp = File.createTempFile("upload_", ".jpg", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            } ?: return@withContext null
            val body = tmp.asRequestBody("image/jpeg".toMediaTypeOrNull())
            MultipartBody.Part.createFormData("file", tmp.name, body)
        } catch (e: Exception) {
            null
        }
    }
