package com.softbankrobotics.dx.followme

import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.aldebaran.qi.sdk.`object`.actuation.LookAtMovementPolicy
import com.aldebaran.qi.sdk.`object`.actuation.PathPlanningPolicy
import com.aldebaran.qi.sdk.`object`.human.Human
import com.aldebaran.qi.sdk.`object`.power.FlapSensor
import com.aldebaran.qi.sdk.builder.GoToBuilder
import com.aldebaran.qi.sdk.builder.LookAtBuilder
import java.util.*
import kotlin.concurrent.schedule
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.math.sqrt

class FollowHuman(
    private val qiContext: QiContext,
    private val humanToFollow: Human,
    private val followHumanListener: FollowHumanListener? = null
) {

    interface FollowHumanListener {
        fun onFollowingHuman()
        fun onCantReachHuman()
        fun onDistanceToHumanChanged(distance: Double)
    }

    companion object {
        private const val TAG = "HumanFollower"
    }

    // Used to get charging flap state in case of movement errors
    private lateinit var chargingFlap: FlapSensor

    private lateinit var robotFrame: Frame
    private lateinit var headFrame: Frame
    private val closeEnoughDistance = 1.0
    private var shouldFollowHuman = false
    private var isFollowingHuman = false
    private var goToFuture: Future<Void>? = null
    private var goToAttemptCounter = 0
    private var timer = Timer()
    private var lookAtFuture: Future<Void>? = null
    private var seemsStuck = false

    init {
        qiContext.power.async().chargingFlap.andThenConsume {
            chargingFlap = it
        }
        qiContext.actuation.async().robotFrame().andThenConsume {
            robotFrame = it
        }
        humanToFollow.async().headFrame.andThenConsume {
            headFrame = it
        }
    }

    private fun maybeFollowHuman(useStraightLines : Boolean) {
        if (shouldFollowHuman) {
            if (computeDistance() < closeEnoughDistance) {
                // Don't try to move
                lookAtFuture?.requestCancellation()
                lookAtFuture = null
                timer.schedule(1000) {
                    // Go back to straight lines
                    seemsStuck = false
                    maybeFollowHuman(true)
                }
            } else {
                startFollowingHuman(useStraightLines)
            }
        }
    }

    private fun startFollowingHuman(useStraightLines : Boolean) {
        val pathPlanningPolicy = when {
            useStraightLines -> PathPlanningPolicy.STRAIGHT_LINES_ONLY
            else -> PathPlanningPolicy.GET_AROUND_OBSTACLES
        }
        Log.i(TAG, "Starting a goTo, using straight lines= $useStraightLines, stuck=$seemsStuck")
        goToFuture = humanToFollow.async().headFrame.andThenCompose { headFrame ->
            if (lookAtFuture == null) {
                lookAtFuture = LookAtBuilder.with(qiContext)
                    .withFrame(headFrame).buildAsync()
                    .andThenCompose {
                        it.policy = LookAtMovementPolicy.HEAD_ONLY
                        it.async().run() }
                    .thenConsume {
                        if (it.hasError()) {
                            Log.e(TAG, "LookAt failed: ${it.errorMessage}")
                            lookAtFuture = null
                        }
                    }
            }
            GoToBuilder.with(qiContext)
                .withFrame(humanToFollow.headFrame)
                .withPathPlanningPolicy(pathPlanningPolicy)
                .buildAsync()
                .andThenCompose {
                    it.addOnStartedListener {
                        if (!isFollowingHuman) {
                            isFollowingHuman = true
                            followHumanListener?.onFollowingHuman()
                        }
                    }
                    it.async().run()
                }.thenConsume {
                    // Future state logging
                    when {
                        it.isSuccess -> {
                            Log.i(TAG, "GoTo action finished with success")
                            seemsStuck = false
                        }
                        it.isCancelled -> {
                            Log.i(TAG, "GoTo action cancelled")
                            seemsStuck = false
                        }
                        it.hasError() -> {
                            Log.e(TAG, "GoTo action error: ${it.errorMessage}")
                            if (chargingFlap.state.open) {
                                Log.e(TAG, "Charging flap is opened")
                            }

                            val thisGoToAttempt = goToAttemptCounter
                            timer.schedule(5000) {
                                // Five seconds later: how many new attempts have been made in the
                                // meantime ?
                                if ((goToAttemptCounter - thisGoToAttempt) >= 5 && computeDistance() > closeEnoughDistance) {
                                    if (seemsStuck && !useStraightLines) {
                                        followHumanListener?.onCantReachHuman()
                                    }
                                    // Now adjust policy
                                    seemsStuck = true
                                }
                            }

                            goToAttemptCounter++
                        }
                    }

                    if (seemsStuck) {
                        // Alternate between using straight lines and not using them
                        maybeFollowHuman(!useStraightLines)
                    } else {
                        maybeFollowHuman(true)
                    }
                }
        }
    }

    private fun computeDistance(): Double {
        if (!::headFrame.isInitialized) {
            return 0.0
        }
        val transformTime = headFrame.computeTransform(robotFrame)
        val transform = transformTime.transform
        val translation = transform.translation
        val x = translation.x
        val y = translation.y
        return sqrt(x * x + y * y)
    }

    fun start() {
        if (!shouldFollowHuman) {
            shouldFollowHuman = true
            // Reset internal tracking variables
            seemsStuck = false
            // Watcher for distance between robot and target
            timer.scheduleAtFixedRate(0, 1000) {
                val distance = computeDistance()
                followHumanListener?.onDistanceToHumanChanged(distance)
                if (distance < closeEnoughDistance) {
                    Log.i(TAG, "Human is close enough, canceling GoTo")
                    goToFuture?.requestCancellation()
                }
            }
            lookAtFuture?.cancel(true)
            lookAtFuture = null
            startFollowingHuman(true)
        } else {
            Log.e(TAG, "Error: the robot is already following a human")
        }
    }

    fun stop() {
        timer.cancel()
        timer = Timer()
        shouldFollowHuman = false
        isFollowingHuman = false
        goToFuture?.cancel(true)
        lookAtFuture?.cancel(true)
        lookAtFuture = null
    }
}