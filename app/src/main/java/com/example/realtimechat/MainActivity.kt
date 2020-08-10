package com.example.realtimechat

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast.LENGTH_SHORT
import android.widget.Toast.makeText
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_singn.*
import kotlinx.android.synthetic.main.content_main.*

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private var firebaseUser: FirebaseUser? = null

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

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        val toggle = ActionBarDrawerToggle(
                this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
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
                val inputManager = this?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputManager.hideSoftInputFromWindow(view.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
            }
        }


    private fun postMessage() {
        val model = MessageItem(userName, userPhotoUrl, inputMessage.text.toString())
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
                makeText(applicationContext, "送信完了", LENGTH_SHORT)
            }
            .addOnFailureListener {
                makeText(applicationContext, "送信失敗", LENGTH_SHORT)
            }
    }

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
        val link = generateContentLink()

        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, link.toString())

        startActivity(Intent.createChooser(intent, "Share Link"))
    }

    //　招待用Dynamic Link作成
    private fun generateContentLink(): Uri {
        // このアプリ自体の公式サイトURLなどあれば指定　Firebaseコンソールで指定していれば不要
        val baseUrl = Uri.parse("")
        //　シェアするURL(Firebaseコンソールで作ったショートURLを使う)
        val domain = "https://realtimechat001.page.link/PZXe"

        val link = FirebaseDynamicLinks.getInstance()
            .createDynamicLink()
            .setLink(baseUrl)
            .setDomainUriPrefix(domain)
                //招待する当アプリ名の指定。Firebaseコンソールで指定していれば不要
//            .setIosParameters(DynamicLink.IosParameters.Builder("com.example.bundleid").build())
//            .setAndroidParameters(DynamicLink.AndroidParameters.Builder("com.example.realtimechat").build())
            .buildDynamicLink()

        return link.uri
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
            .addOnFailureListener(this) { e -> Log.w("receiveInvitation", "getDynamicLink:onFailure", e) }
    }

}