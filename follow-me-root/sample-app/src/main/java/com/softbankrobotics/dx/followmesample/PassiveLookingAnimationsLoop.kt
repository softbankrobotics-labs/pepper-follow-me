package com.softbankrobotics.dx.followmesample

import android.os.Handler
import android.os.Looper
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.builder.AnimateBuilder
import com.aldebaran.qi.sdk.builder.AnimationBuilder
import com.aldebaran.qi.sdk.util.FutureUtils
import java.util.concurrent.TimeUnit

class PassiveLookingAnimationsLoop(private val qiContext: QiContext, private val delay: Int = 10) {

    private val animationNames = arrayOf(
        "goto_left_35_1.qianim",
        "goto_left_35_2.qianim",
        "goto_left_35_3.qianim",
        "goto_left_35_4.qianim",
        "goto_left_35_5.qianim",
        "goto_left_35.qianim",
        "goto_left_50_2.qianim",
        "goto_left_50_3.qianim",
        "goto_left_50_4.qianim",
        "goto_left_50.qianim",
        "goto_left_70_2.qianim",
        "goto_left_70_3.qianim",
        "goto_left_70_4.qianim",
        "goto_left_70_5.qianim",
        "goto_left_70_6.qianim",
        "goto_left_70.qianim",
        "goto_right_35_1.qianim",
        "goto_right_35_2.qianim",
        "goto_right_35_3.qianim",
        "goto_right_35_4.qianim",
        "goto_right_35_5.qianim",
        "goto_right_35.qianim",
        "goto_right_50_2.qianim",
        "goto_right_50_3.qianim",
        "goto_right_50_4.qianim",
        "goto_right_50.qianim",
        "goto_right_70_2.qianim",
        "goto_right_70_3.qianim",
        "goto_right_70_4.qianim",
        "goto_right_70_5.qianim",
        "goto_right_70_6.qianim",
        "goto_right_70.qianim"
    )
    private var animationNamesQueue = mutableListOf<String>()
    private var animationName = ""

    private val handler = Handler(Looper.getMainLooper())

    private var animationLauncher = object : Runnable {
        override fun run() {
            buildAndRunAnimate()
            animationFuture.thenConsume {
                if (!it.isCancelled) {
                    handler.postDelayed(this, delay * 1000L)
                }
            }
        }
    }

    private var animationFuture = FutureUtils.wait(1, TimeUnit.MILLISECONDS)

    private fun buildAndRunAnimate() {
        animationName = chooseAnimationName()
        animationFuture = AnimationBuilder.with(qiContext)
            .withAssets(animationName)
            .buildAsync()
            .andThenCompose { animation ->
                AnimateBuilder.with(qiContext)
                    .withAnimation(animation)
                    .buildAsync()
                    .andThenCompose { animate ->
                        animate.async().run()
                    }
            }
    }

    private fun chooseAnimationName(): String {
        if (animationNamesQueue.isEmpty()) {
            animationNamesQueue = animationNames.toMutableList()
            animationNamesQueue.shuffle()
        }

        val chosenAnimationName = animationNamesQueue[0]
        animationNamesQueue.removeAt(0)

        return if (chosenAnimationName != animationName) {
            chosenAnimationName
        } else {
            chooseAnimationName()
        }
    }

    fun start() {
        handler.postDelayed(animationLauncher, delay * 1000L)
    }

    fun stop(): Future<Void> {
        handler.removeCallbacksAndMessages(null)
        if (!animationFuture.isDone) {
            animationFuture.requestCancellation()
        }
        return animationFuture
    }
}