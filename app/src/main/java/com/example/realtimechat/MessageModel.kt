package com.example.realtimechat

import java.util.*

data class MessageItem(
    var userName: String = "",  // ユーザー名
    var userPhotoUrl: String = "",  // ユーザー写真URL
    var postedMessage: String = "",  //　投稿メッセージ
    var postedImageUrl: String = "",  // 投稿画像URL
    val registerTime: Date = Date()  // 投稿時間
)