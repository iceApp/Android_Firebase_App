package com.example.realtimechat

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class MessageHolder(view: View): RecyclerView.ViewHolder(view) {
    val card_chat: CardView
    val image_user: ImageView
    val text_posted: TextView
    val image_posted: ImageView
    var text_user_name: TextView

    init {
        card_chat = view.findViewById(R.id.card_chat)
        image_user = view.findViewById(R.id.image_user)
        text_posted = view.findViewById(R.id.text_posted)
        image_posted = view.findViewById(R.id.image_posted)
        text_user_name = view.findViewById(R.id.text_user_name)
    }

}