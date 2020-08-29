package com.example.realtimechat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide.with
import com.bumptech.glide.request.RequestOptions
import com.firebase.ui.firestore.FirestoreRecyclerAdapter
import com.firebase.ui.firestore.FirestoreRecyclerOptions
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.dynamiclinks.DynamicLink
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks
import com.google.firebase.dynamiclinks.ktx.androidParameters
import com.google.firebase.dynamiclinks.ktx.dynamicLink
import com.google.firebase.dynamiclinks.ktx.dynamicLinks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_singn.*
import kotlinx.android.synthetic.main.content_main.*

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    // ログインユーザー
    private var firebaseUser: FirebaseUser? = null

    var layoutManager: LinearLayoutManager? = null
    lateinit var firebaseAdapter: FirestoreRecyclerAdapter<MessageItem, MessageHolder>

    var userName: String = ""
    var userPhotoUrl: String = ""

    // GoogleSignInClient の生成
    private val googleSignInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(this, gso)
    }

    // GoogleSignInClient のオプション、今回は特に設定が必要ないのでデフォルト値的なものを利用する
    private val gso: GoogleSignInOptions by lazy {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
    }

    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        val toggle = ActionBarDrawerToggle(
            this,
            drawer_layout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()

        nav_view.setNavigationItemSelectedListener(this)

        logInCheck()

        btnSendMessage.setOnClickListener {
            postMessage()
        }


        // 画面タッチでキーボードを閉じる
        drawer_layout?.setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                /* Fragmentのレイアウトがタッチされた時に、Fragment全体ににフォーカスを移す */
                drawer_layout?.requestFocus()
            }
            v?.onTouchEvent(event) ?: true
        }

        // 入力欄からフォーカスが外れたタイミングでキーボードを閉じる
        inputMessage.setOnFocusChangeListener { view, hasFocus ->
            Log.d(TAG, "hasFocus : $hasFocus")
            if (!hasFocus) {
                val inputManager = this.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputManager.hideSoftInputFromWindow(
                    view.windowToken,
                    InputMethodManager.HIDE_NOT_ALWAYS
                )
            }
        }

        // 画像送付ボタン
        btnAddPhoto.setOnClickListener {
            postImage()
        }
    }

    //　チャット履歴を表示
    private fun displayChatData() {
        layoutManager = LinearLayoutManager(this)
        layoutManager!!.stackFromEnd = true
        chatList.layoutManager = layoutManager

        val query = db.collection("rooms")
            .document(userName)
            .collection("my_chat_rooms").orderBy(
                MessageItem::registerTime.name,
                Query.Direction.ASCENDING
            )
            .limit(20)

        // Firebase UIに入れる
        val options = FirestoreRecyclerOptions.Builder<MessageItem>()
            .setQuery(query, MessageItem::class.java)
            .build()

        firebaseAdapter = object : FirestoreRecyclerAdapter<MessageItem, MessageHolder>(options) {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageHolder {

                val view =
                    LayoutInflater.from(parent.context).inflate(
                        R.layout.chat_content,
                        parent,
                        false
                    )
                return MessageHolder(view)
            }

            override fun onBindViewHolder(holder: MessageHolder, position: Int, model: MessageItem) {
                setUserContents(holder, model)
                setChatContents(holder, model)
            }
        }
        chatList.adapter = firebaseAdapter

        // コメント後の自動ページング
        firebaseAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver(){
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                val chatMessageCount = firebaseAdapter.itemCount
                val lastVisiblePosition = layoutManager!!.findLastCompletelyVisibleItemPosition()
                if (lastVisiblePosition == -1 || positionStart >= chatMessageCount -1 && lastVisiblePosition == positionStart -1) {
                    chatList.scrollToPosition(positionStart)
                }
            }
        })
    }

    //　チャットのユーザー情報表示
    private fun setUserContents(holder: MessageHolder, model: MessageItem) {
        holder.text_user_name.text = model.userName
        if (model.userPhotoUrl != ""){
            with(this).load(model.userPhotoUrl)
                .into(holder.image_user)
            return
        }
        holder.image_user.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                R.drawable.ic_baseline_account_circle_24
            )
        )
    }

    // チャットの投稿した文章と画像を表示
    private fun setChatContents(holder: MessageHolder, model: MessageItem) {
        // 投稿文章
        if (model.postedMessage != "") {
            holder.apply {
                text_posted.text = model.postedMessage
                text_posted.visibility = View.VISIBLE
                image_posted.visibility = View.GONE
            }
            return
        }

        // 投稿画像
        setPostImageContent(holder, model)
    }

    private fun setPostImageContent(holder: MessageHolder, model: MessageItem) {

        val imageUrl = model.postedImageUrl
        holder.apply {
            image_posted.visibility = View.VISIBLE
            text_posted.visibility = View.VISIBLE
        }

        // 画像のURLがStorage以外の場合
        if (!imageUrl.startsWith("gs://")) {
            GlideApp.with(holder.image_posted.context)
                .load(imageUrl)
                .into(holder.image_posted)
            return
        }

        val storageRef = Firebase.storage.getReferenceFromUrl(imageUrl)

        GlideApp.with(holder.image_posted.context)
            .applyDefaultRequestOptions(
                RequestOptions()
                    .placeholder(R.drawable.ic_sharp_image_loading_error_24)
                    .error(R.drawable.ic_sharp_image_loading_error_24)
            )
            .load(storageRef)
            .into(holder.image_posted)
    }

    override fun onResume() {
        super.onResume()
        firebaseAdapter.startListening()
    }

    override fun onStop() {
        super.onStop()
        firebaseAdapter.stopListening()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when(requestCode){
            //画像フォルダ参照後
            REQUEST_GET_IMAGE -> {
                getImageResult(resultCode, data)
            }
        }
    }

    // 画像パスをfirestoreとstorageで結合
    private fun getImageResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK){
            makeToast(this, getString(R.string.get_image_failed))
            return
        }

        if (data == null) return
        val uriFromDevice = data.data

        // firestoreの画像保存パス作成
        val tempMessage = MessageItem(userName, userPhotoUrl, "", "")
        val refId = db.collection("rooms")
            .document(userName)
            .collection("my_chat_rooms")
            .document()

        val imageFileName = System.currentTimeMillis().toString()

        //　storageのディレクトリパス作成
        val storageRef = FirebaseStorage.getInstance().getReference(firebaseUser!!.uid)
                .child(refId.id).child(imageFileName)

        // 画像をstorageにアップ
        putImageStorage(storageRef, uriFromDevice, refId.id)
    }

    // 画像をstorageに、画像パスをfirestoreに送信
    private fun putImageStorage(storageRef: StorageReference?, uriFromDevice: Uri?, refId: String) {

        // 画像をstorageに送信
        storageRef!!.putFile(uriFromDevice!!).addOnCompleteListener { task ->

            //　storeに送信する画像パス
            val gsReference = task.result?.storage.toString()

            //　storeに画像パスを送信
            val chatMessage =
                MessageItem(
                    userName,
                    userPhotoUrl,
                    "",
                    gsReference
                )

            db.collection("rooms")
                .document(userName)
                .collection("my_chat_rooms")
                .document(refId)
                .set(chatMessage!!)
                .addOnCompleteListener {
                }
                .addOnSuccessListener {
                    inputMessage.text.clear()
                    makeToast(applicationContext, "送信完了")
                }
                .addOnFailureListener {
                    makeToast(applicationContext, "送信失敗")
                }
        }
        .addOnFailureListener {
            makeToast(this, getString(R.string.image_upload_error))
        }
    }

    // デバイスの画像フォルダ参照
    private fun postImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, REQUEST_GET_IMAGE)
    }

    // メッセージをstoreに送信
    private fun postMessage() {
        val model = MessageItem(userName, userPhotoUrl, inputMessage.text.toString())
        if(model.postedMessage == "" ) return

        /* ボタンクリックのタイミングでFragmentにフォーカスを移すことによって、キーボードを閉じる */
        drawer_layout.requestFocus()
        db.collection("rooms")
            .document(userName)
            .collection("my_chat_rooms")
            .document()
            .set(model)
            .addOnCompleteListener {
            }
            .addOnSuccessListener {
                inputMessage.text.clear()
                makeToast(applicationContext, "送信完了")
            }
            .addOnFailureListener {
                makeToast(applicationContext, "送信失敗")
            }
    }

    // ログイン済みかチェック
    private fun logInCheck() {

        firebaseUser = FirebaseAuth.getInstance().currentUser

        // ログインしていない場合
        if (firebaseUser == null){
            startActivity(Intent(this, SignActivity::class.java))
            finish()
            return
        }

        // アカウント情報参照
        setUserProfiles(firebaseUser!!)
        displayChatData()

        // 招待者とのチャットへ
        receiveInvitation()
    }

    // ログインアカウントをナビヘッダーに情報表示
    private fun setUserProfiles(firebaseUser: FirebaseUser) {
        val navHeader = nav_view.getHeaderView(0)
        val textUserName = navHeader.findViewById<TextView>(R.id.text_user_name)
        val textUserId = navHeader.findViewById<TextView>(R.id.text_user_id)
        textUserName.text = firebaseUser.displayName
        textUserId.text = firebaseUser.email

        userName = firebaseUser.displayName!!
        userPhotoUrl = firebaseUser.photoUrl.toString()
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_menu_invite -> {
                sendInvitation()
            }
            R.id.nav_menu_sign_out -> {
                signOut()
            }
        }

        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }

    // ログアウト
    private fun signOut() {
        FirebaseAuth.getInstance().signOut()
        googleSignInClient.signOut().addOnCompleteListener { task ->
            if (task.isSuccessful){
                startActivity(Intent(this, SignActivity::class.java))
                finish()
                return@addOnCompleteListener
            }
        }
    }

    //　招待ボタン
    private fun sendInvitation(){
        val dynamicLink = generateContentLink()

        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(
                Intent.EXTRA_TEXT,
                getString(R.string.app_invite_message) + " " + dynamicLink.uri.toString()
            )
            type = "text/plain"
        }
        startActivity(Intent.createChooser(intent, getString(R.string.app_invite_title)))
    }

    //　招待用Dynamic Link作成
    private fun generateContentLink(): DynamicLink {
        return Firebase.dynamicLinks.dynamicLink {
            link = Uri.parse("https://play.google.com/store/apps/details?id=com.example.realtimechat/") // アプリ公式サイトなど
            domainUriPrefix = "https://realtimechat001.page.link" //コンソールで設定したDynamicLink用独自ドメイン
            androidParameters("com.example.realtimechat") {
                fallbackUrl =
                    Uri.parse("https://play.google.com/store/apps/details?id=com.example.realtimechat") // Play storeのアプリURL
            }
//            iosParameters("com.example.bundleid") {
//            }
        }
    }

    // 招待リンク受け取り
    private fun receiveInvitation(){
        // TODO: 招待URLから開いた場合、招待者とのチャットを開く
        FirebaseDynamicLinks.getInstance()
            .getDynamicLink(intent)
            .addOnSuccessListener(this) { pendingDynamicLinkData ->
                // Get deep link from result (may be null if no link is found)
                var deepLink: Uri? = null
                if (pendingDynamicLinkData != null) {
                    //
                    deepLink = pendingDynamicLinkData.link
                }

                // Handle the deep link. For example, open the linked
                // content, or apply promotional credit to the user's
                // account.
                // ...

                // ...
            }
            .addOnFailureListener(this) { e -> Log.w(
                "receiveInvitation",
                "getDynamicLink:onFailure",
                e
            ) }
    }

}