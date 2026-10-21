package com.amix.tutor.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amix.tutor.auth.AmixUser
import com.amix.tutor.auth.AuthRepository
import com.amix.tutor.auth.PromoOutcome
import com.amix.tutor.auth.PromoRepository
import com.amix.tutor.auth.SignInOutcome
import com.amix.tutor.data.ChatDao
import com.amix.tutor.data.ChatEntity
import com.amix.tutor.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val auth: AuthRepository,
    private val promo: PromoRepository,
    dao: ChatDao
) : ViewModel() {

    init {
        auth.start()
    }

    val language: StateFlow<String> = settings.language
    val user: StateFlow<AmixUser?> = auth.user
    val proUntil: StateFlow<Long> = settings.proUntil

    val chats: StateFlow<List<ChatEntity>> = dao.observeChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setLanguage(lang: String) = settings.setLanguage(lang)

    fun toggleLanguage() = settings.setLanguage(if (language.value == "ru") "en" else "ru")

    suspend fun signInGoogle(activity: Activity): SignInOutcome = auth.signInWithGoogle(activity)

    suspend fun signInEmail(email: String, password: String): SignInOutcome = auth.signInWithEmail(email, password)

    suspend fun createEmail(email: String, password: String): SignInOutcome = auth.createWithEmail(email, password)

    fun signOut() {
        auth.signOut()
        settings.setProUntil(0L)
    }

    suspend fun redeem(code: String): PromoOutcome = promo.redeem(code)
}
