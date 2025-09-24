package com.example.loginui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.loginui.net.MsExchangeRequest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals

@RunWith(AndroidJUnit4::class)
class MsExchangeRequestTest {

    @Test
    fun constructor_and_property_are_covered() {
        val token = "idTok_123"
        val req = MsExchangeRequest(token)
        assertEquals(token, req.idToken)
    }
}