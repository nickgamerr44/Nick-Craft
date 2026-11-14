package com.example.base

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
abstract class FirestoreEmulatorTestBase {

  protected lateinit var context: Context
  protected lateinit var auth: FirebaseAuth
  protected lateinit var db: FirebaseFirestore

  companion object {
    private var emulatorsBound = false
    private const val AUTH_TIMEOUT_MS = 8000L
  }

  @Before
  open fun setUpFirestoreEmulator() {
    context = ApplicationProvider.getApplicationContext()
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(context)
    }

    auth = FirebaseAuth.getInstance()
    val dbId = context.getString(R.string.firestore_database_id)
    db = FirebaseFirestore.getInstance(dbId)

    if (!emulatorsBound) {
      auth.useEmulator("127.0.0.1", 9099)
      db.useEmulator("127.0.0.1", 8085)
      db.firestoreSettings = FirebaseFirestoreSettings.Builder()
        .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
        .build()
      emulatorsBound = true
    }
    auth.signOut()
  }

  @After
  open fun tearDownFirestoreEmulator() {
    auth.signOut()
  }

  protected suspend fun signInTestUser(email: String, password: String = "Pass123456!"): String =
    withContext(Dispatchers.IO) {
      withTimeout(AUTH_TIMEOUT_MS) {
        auth.signOut()
        val result = try {
          auth.signInWithEmailAndPassword(email, password).await()
        } catch (_: FirebaseAuthInvalidUserException) {
          auth.createUserWithEmailAndPassword(email, password).await()
        } catch (_: Exception) {
          auth.createUserWithEmailAndPassword(email, password).await()
        }
        checkNotNull(result.user?.uid) { "User auth failed for $email" }
      }
    }

  protected fun Throwable.unwrapFirestoreException(): FirebaseFirestoreException? {
    var current: Throwable? = this
    while (current != null) {
      if (current is FirebaseFirestoreException) return current
      current = current.cause
    }
    return null
  }
}
