package com.citrus.blsc.utils

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import com.citrus.blsc.R

object UIAnimationHelper {
    
    fun animateButtonPress(view: View) {
        val animation = AnimationUtils.loadAnimation(view.context, R.anim.button_press)
        view.startAnimation(animation)
    }
    
    fun animateCardAppear(view: View, delay: Long = 0) {
        view.alpha = 0f
        view.translationY = 50f
        
        val animatorSet = AnimatorSet()
        val alphaAnimator = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
        val translationAnimator = ObjectAnimator.ofFloat(view, "translationY", 50f, 0f)
        
        animatorSet.playTogether(alphaAnimator, translationAnimator)
        animatorSet.duration = 300
        animatorSet.startDelay = delay
        animatorSet.interpolator = AccelerateDecelerateInterpolator()
        animatorSet.start()
    }
    
    fun animatePulse(view: View) {
        val animatorSet = AnimatorSet()
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.1f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.1f, 1f)
        
        animatorSet.playTogether(scaleX, scaleY)
        animatorSet.duration = 200
        animatorSet.interpolator = AccelerateDecelerateInterpolator()
        animatorSet.start()
    }
    
    fun animateShake(view: View) {
        val animatorSet = AnimatorSet()
        val shake1 = ObjectAnimator.ofFloat(view, "translationX", 0f, -10f, 10f, -10f, 10f, 0f)
        val shake2 = ObjectAnimator.ofFloat(view, "translationY", 0f, -5f, 5f, -5f, 5f, 0f)
        
        animatorSet.playTogether(shake1, shake2)
        animatorSet.duration = 500
        animatorSet.interpolator = AccelerateDecelerateInterpolator()
        animatorSet.start()
    }
    
    fun animateRotation(view: View, degrees: Float = 360f) {
        val rotationAnimator = ObjectAnimator.ofFloat(view, "rotation", 0f, degrees)
        rotationAnimator.duration = 300
        rotationAnimator.interpolator = AccelerateDecelerateInterpolator()
        rotationAnimator.start()
    }
}
