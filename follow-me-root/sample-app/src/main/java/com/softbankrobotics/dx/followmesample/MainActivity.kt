package com.softbankrobotics.dx.followmesample

import android.os.Bundle
import android.util.Log
import android.view.View
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.aldebaran.qi.sdk.design.activity.conversationstatus.SpeechBarDisplayStrategy
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : RobotActivity(), RobotLifecycleCallbacks, MainView {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var mainPresenter: MainPresenter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        QiSDK.register(this, this)
        setContentView(R.layout.activity_main)
        setSpeechBarDisplayStrategy(SpeechBarDisplayStrategy.IMMERSIVE)
    }

    override fun onResume() {
        super.onResume()
        // Enables sticky immersive mode.
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                // Set the content to appear under the system bars so that the
                // content doesn't resize when the system bars hide and show.
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                // Hide the nav bar and status bar
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    override fun showLookingForHuman() {
        runOnUiThread {
            imageView.setImageResource(R.drawable.looking_for_human)
            buttonFollowMe.setOnClickListener { }
            buttonStop.setOnClickListener { }
        }
    }

    override fun showReadyToFollow() {
        runOnUiThread {
            imageView.setImageResource(R.drawable.follow_me)
            buttonFollowMe.setOnClickListener { mainPresenter?.startFollowing() }
            buttonStop.setOnClickListener { }
        }
    }

    override fun showStopFollowing() {
        runOnUiThread {
            imageView.setImageResource(R.drawable.following_human)
            buttonStop.setOnClickListener { mainPresenter?.stopFollowing() }
            buttonFollowMe.setOnClickListener { }
        }
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        Log.i(TAG, "onRobotFocusGained")
        showLookingForHuman()
        mainPresenter = MainPresenter(this, this, qiContext)
    }

    override fun onRobotFocusRefused(reason: String?) {
        Log.e(TAG, "onRobotFocusRefused: $reason")
    }

    override fun onRobotFocusLost() {
        Log.i(TAG, "onRobotFocusLost")
        mainPresenter?.onDestroy()
    }

    override fun onDestroy() {
        super.onDestroy()
        QiSDK.unregister(this, this)
        mainPresenter?.onDestroy()
    }
}