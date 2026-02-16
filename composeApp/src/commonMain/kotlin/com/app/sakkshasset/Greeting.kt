package com.app.sakkshasset

class Greeting {
    private val platform = getPlatform()

    fun greet(): String {
        return "Hello, ${platform.name}!"
    }
}