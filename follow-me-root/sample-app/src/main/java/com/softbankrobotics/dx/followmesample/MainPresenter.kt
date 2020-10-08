package com.softbankrobotics.dx.followmesample

import android.content.Context
import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.conversation.BodyLanguageOption
import com.aldebaran.qi.sdk.`object`.conversation.Listen
import com.aldebaran.qi.sdk.`object`.conversation.ListenResult
import com.aldebaran.qi.sdk.`object`.human.Human
import com.aldebaran.qi.sdk.builder.ListenBuilder
import com.aldebaran.qi.sdk.builder.PhraseSetBuilder
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.softbankrobotics.dx.followme.FollowHuman
import java.util.*
import kotlin.concurrent.schedule

class MainPresenter(
    private var mainView: MainView?,
    private val context: Context,
    private val qiContext: QiContext
) :
    FollowHuman.FollowHumanListener {

    companion object {
        private const val TAG = "MainPresenter"
    }

    private var passiveLookingAnimationsLoop = PassiveLookingAnimationsLoop(qiContext)
    private var passiveLookingAnimationsLoopIsRunning = false
    private lateinit var humanToFollow: Human
    private var followHuman: FollowHuman? = null
    private var humanDetected = false
    private var followingHuman = false
    private var sayFuture: Future<Void>? = null
    private var listenFuture: Future<ListenResult>? = null
    private var sayTooFar = true

    init {
        // Check if a human is already detected
        val humansAround = qiContext.humanAwareness.humansAround
        if (humansAround.size != 0) {
            Log.i(TAG, "Human detected at initialization")
            humanToFollow = humansAround[0]
            humanDetected()
        } else if (!passiveLookingAnimationsLoopIsRunning) {
            passiveLookingAnimationsLoop.start()
            passiveLookingAnimationsLoopIsRunning = true
        }

        // Look for humans around
        qiContext.humanAwareness.addOnHumansAroundChangedListener {
            Log.i(TAG, "Number of humans detected: ${it.size}")
            if (it.size != 0) {
                humanToFollow = it[0]
                humanDetected()
            } else {
                humanLost()
            }
        }
    }

    private fun lookingForHuman() {
        Log.d(TAG, "lookingForHuman")
        cancelListen()
        mainView?.showLookingForHuman()
    }

    private fun readyToFollow() {
        Log.d(TAG, "readyToFollow")
        mainView?.showReadyToFollow()

        cancelListen()
        val phrases = context.resources.getStringArray(R.array.say_follow)
        PhraseSetBuilder.with(qiContext)
            .withTexts(*phrases)
            .buildAsync()
            .andThenConsume { phraseSet ->
                ListenBuilder.with(qiContext)
                    .withPhraseSet(phraseSet)
                    .buildAsync()
                    .andThenConsume { listen ->
                        if (sayFuture != null && !sayFuture!!.isDone) {
                            sayFuture!!.thenConsume {
                                if (!followingHuman) {
                                    runStartListen(listen)
                                }
                            }
                        } else {
                            runStartListen(listen)
                        }
                    }
            }
    }

    fun startFollowing() {
        Log.d(TAG, "startFollowing")
        followHuman = FollowHuman(qiContext, humanToFollow, this)
        followHuman!!.start()
    }

    private fun followingHuman() {
        Log.d(TAG, "followingHuman")
        mainView?.showStopFollowing()

        cancelListen()
        buildAndRunStopListen()
    }

    private fun buildAndRunStopListen() {
        val phrases = context.resources.getStringArray(R.array.listen_stop)
        PhraseSetBuilder.with(qiContext)
            .withTexts(*phrases)
            .buildAsync()
            .andThenConsume { phraseSet ->
                ListenBuilder.with(qiContext)
                    .withPhraseSet(phraseSet)
                    .withBodyLanguageOption(BodyLanguageOption.DISABLED)
                    .buildAsync()
                    .andThenConsume { listen ->
                        if (sayFuture != null && !sayFuture!!.isDone) {
                            sayFuture!!.thenConsume {
                                if (followingHuman) {
                                    runStopListen(listen)
                                }
                            }
                        } else {
                            runStopListen(listen)
                        }
                    }
            }
    }

    fun stopFollowing() {
        Log.d(TAG, "stopFollowing")
        cancelListen()
        followHuman?.stop()
        followingHuman = false
        if (humanDetected) {
            readyToFollow()
        } else {
            lookingForHuman()
        }
    }

    private fun humanDetected() {
        Log.d(TAG, "humanDetected")
        if (!humanDetected) {
            humanDetected = true
            readyToFollow()
        }
        if (passiveLookingAnimationsLoopIsRunning) {
            passiveLookingAnimationsLoop.stop()
            passiveLookingAnimationsLoopIsRunning = false
        }
    }

    override fun onFollowingHuman() {
        Log.d(TAG, "onFollowingHuman")
        followingHuman = true
        followingHuman()
    }

    override fun onDistanceToHumanChanged(distance: Double) {
        Log.d(TAG, "onDistanceToHumanChanged: $distance")
        if (distance > 3 && sayTooFar && followingHuman) {
            val phrases = context.resources.getStringArray(R.array.say_too_far)
            runSay(phrases.random())
            if (followingHuman) {
                buildAndRunStopListen()
                sayTooFar = false
                Timer().schedule(5000) {
                    sayTooFar = true
                }
            }
        }
    }

    private fun humanLost() {
        Log.d(TAG, "humanLost")
        humanDetected = false
        if (followingHuman) {
            val phrases = context.resources.getStringArray(R.array.say_lost)
            runSay(phrases.random())
        }
        stopFollowing()
        if (!passiveLookingAnimationsLoopIsRunning) {
            passiveLookingAnimationsLoop.start()
            passiveLookingAnimationsLoopIsRunning = true
        }
    }

    override fun onCantReachHuman() {
        Log.d(TAG, "onCantReachHuman")
        runSay(context.getString(R.string.unreachable))
        stopFollowing()
    }

    private fun runStartListen(listen: Listen) {
        listenFuture = listen.async().run()
        listenFuture!!.andThenConsume {
            startFollowing()
        }
    }

    private fun runStopListen(listen: Listen) {
        listenFuture = listen.async().run()
        listenFuture!!.andThenConsume {
            stopFollowing()
        }
    }

    private fun cancelListen() {
        if (listenFuture != null && !listenFuture!!.isDone) {
            listenFuture!!.cancel(true)
        }
    }

    private fun runSay(messageToSay: String) {
        cancelListen()
        SayBuilder.with(qiContext)
            .withText(messageToSay)
            .withBodyLanguageOption(BodyLanguageOption.DISABLED)
            .buildAsync()
            .andThenConsume {
                sayFuture = it.async().run()
            }
    }

    fun onDestroy() {
        Log.d(TAG, "onDestroy")
        qiContext.humanAwareness.removeAllOnHumansAroundChangedListeners()
        if (passiveLookingAnimationsLoopIsRunning) {
            passiveLookingAnimationsLoop.stop()
        }
        mainView?.showLookingForHuman()
        mainView = null
    }
}