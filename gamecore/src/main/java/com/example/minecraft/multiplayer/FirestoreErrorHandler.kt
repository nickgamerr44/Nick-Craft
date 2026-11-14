package com.example.minecraft.multiplayer

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException

fun handleFirestoreError(
  error: Throwable,
  operation: String,
  path: String,
  uid: String?
): String {
  val code = (error as? FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
  val structured = """{"operation":"$operation","path":"$path","uid":"${uid ?: "null"}","code":"$code","message":"${error.message ?: ""}"}"""
  Log.e("FirestoreError", structured, error)

  return when (error) {
    is FirebaseFirestoreException -> when (error.code) {
      FirebaseFirestoreException.Code.PERMISSION_DENIED ->
        "Permissão negada ou servidor cheio (máximo 5 jogadores)."
      FirebaseFirestoreException.Code.NOT_FOUND ->
        "Servidor não encontrado. Verifique o código."
      FirebaseFirestoreException.Code.UNAVAILABLE ->
        "Sem conexão com o servidor online. Tente novamente."
      else -> "Erro no servidor ($code): ${error.localizedMessage ?: "tente novamente"}"
    }
    else -> error.localizedMessage ?: "Erro inesperado ao sincronizar multiplayer."
  }
}
