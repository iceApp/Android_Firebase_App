package com.example.realtimechat

class MessageModel() {
    var userName: String = ""  // ユーザー名
    var userPhotoUrl: String = ""  // ユーザー写真URL
    var postedMessage: String = ""  //　投稿メッセージ
    var postedImageUrl: String = ""  // 　投稿画像URL

    constructor(userName: String,
                userPhotoUrl: String,
                postedMessage: String,
                postedImageUrl:String) : this(){
        this.userName = userName
        this.userPhotoUrl = userPhotoUrl
        this.postedMessage = postedMessage
        this.postedImageUrl = postedImageUrl
    }
}